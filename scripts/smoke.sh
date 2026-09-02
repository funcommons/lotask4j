#!/usr/bin/env bash
# lotask4j 全链路真实联调冒烟 (对 docker compose 起的真实栈)
#
# 前置: mvn -pl lotask4j-backend -am package -DskipTests && docker compose up -d --build
# 覆盖: Flyway 迁移 → 平台登录 → 建租户(A/B) → 类型配置 → 提交(幂等) → 隔离断言
#       → worker poll/progress/result → Webhook HMAC 验签 → reset-secret 宽限
#       → embed 短期 token → 防爆破锁定
set -euo pipefail

BASE="${SMOKE_BASE:-http://localhost:19080}"
PLATFORM_SECRET="${PLATFORM_CLIENT_SECRET:-smoke-platform-secret}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TS=$(date +%s)
PASS=0; FAIL=0

say()  { printf '\033[36m[SMOKE]\033[0m %s\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31m✗ %s\033[0m\n' "$1"; FAIL=$((FAIL+1)); }
assert_eq() { # assert_eq <desc> <expected> <actual>
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (expected=$2 actual=$3)"; fi
}

# 容错 JSON 取值: 解析失败返回空串 (set -e 下不能让子命令失败中断脚本)
jsonget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1" 2>/dev/null || true; }

# HMAC 请求签名 (契约与 application.yml signature 段 / frontend signature.ts 对齐)
# toSign = [METHOD, path, timestamp, nonce, MD5(body)].join("\n")
# 输出单行: X-Access-Key:..;X-Timestamp:..;X-Nonce:..;X-Signature:..
signed_headers() { # <method> <path> <body> <accessKey> <secret>
  python3 - "$1" "$2" "$3" "$4" "$5" <<'PYEOF'
import sys, hashlib, hmac, base64, time, uuid
method, path, body, access_key, secret = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
ts = str(int(time.time() * 1000))
nonce = str(uuid.uuid4())
body_str = body if body else ""
md5 = hashlib.md5(body_str.encode()).hexdigest()
to_sign = "\n".join([method, path, ts, nonce, md5])
sig = base64.b64encode(hmac.new(secret.encode(), to_sign.encode(), hashlib.sha256).digest()).decode()
print(";".join([f"X-Access-Key:{access_key}", f"X-Timestamp:{ts}", f"X-Nonce:{nonce}", f"X-Signature:{sig}"]))
PYEOF
}

# 切分签名 header 到 _sh1.._sh4 (bash 3.2 无 mapfile, 用参数展开)
split_headers() {
  _rest="$1"
  _sh1=${_rest%%;*}; _rest=${_rest#*;}
  _sh2=${_rest%%;*}; _rest=${_rest#*;}
  _sh3=${_rest%%;*}; _sh4=${_rest#*;}
}

# ---------- 0. 健康等待 (Flyway 迁移在启动期完成) ----------
say "等待 backend 就绪 (含 Flyway V1..V5 迁移)..."
for i in $(seq 1 60); do
  if curl -sf "$BASE/actuator/health" 2>/dev/null | grep -q '"UP"'; then break; fi
  [ "$i" = 60 ] && { bad "backend 未就绪"; exit 1; }
  sleep 2
done
ok "backend UP"

# Flyway 迁移产物断言: V5 收紧后 tenant_id 必为 NOT NULL (compose 内 psql 不便, 走后端日志语义)
say "断言 V5 迁移已应用 (flyway_schema_history 含 V5)"
V5=$(docker compose exec -T postgres psql -U admin -d lotask4j -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE version='5' AND success=true")
assert_eq "Flyway V5 已应用" "1" "$V5"

# ---------- 1. 平台身份登录 + 建租户 ----------
say "平台身份登录"
PT=$(curl -sf -X POST "$BASE/api/v1/auth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=PLATFORM&client_secret=$PLATFORM_SECRET" | jsonget "d['data']['access_token']")
[ -n "$PT" ] && ok "平台 Token 获取" || bad "平台 Token 获取"

say "创建租户 A / B"
RA=$(curl -sf -X POST "$BASE/api/v1/admin/tenants" -H "Authorization: Bearer $PT" \
  -H "Content-Type: application/json" -d "{\"name\":\"smoke-a-$TS\",\"description\":\"smoke\"}")
RB=$(curl -sf -X POST "$BASE/api/v1/admin/tenants" -H "Authorization: Bearer $PT" \
  -H "Content-Type: application/json" -d "{\"name\":\"smoke-b-$TS\",\"description\":\"smoke\"}")
SECRET_A=$(echo "$RA" | jsonget "d['data']['tenantSecret']")
SECRET_B=$(echo "$RB" | jsonget "d['data']['tenantSecret']")
TENANT_A_ID=$(echo "$RA" | jsonget "d['data']['id']")
[ -n "$SECRET_A" ] && ok "租户 A 创建 (一次性明文 ${#SECRET_A} 位)" || bad "租户 A 创建"
[ -n "$SECRET_B" ] && ok "租户 B 创建" || bad "租户 B 创建"

# ---------- 2. 租户登录 ----------
TA=$(curl -sf -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=smoke-a-$TS&client_secret=$SECRET_A" | jsonget "d['data']['access_token']")
TB=$(curl -sf -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=smoke-b-$TS&client_secret=$SECRET_B" | jsonget "d['data']['access_token']")
[ -n "$TA" ] && ok "租户 A Token" || bad "租户 A Token"
[ -n "$TB" ] && ok "租户 B Token" || bad "租户 B Token"

# ---------- 3. 类型配置 (平台替租户建, 带租户归属 — V5 起必填) ----------
TYPE_KEY="data-export-$TS"
say "平台创建任务类型 $TYPE_KEY (归属租户 A)"
RC=$(curl -sf -X POST "$BASE/api/v1/admin/types" -H "Authorization: Bearer $PT" \
  -H "Content-Type: application/json" \
  -d '{"typeKey":"'"$TYPE_KEY"'","tenantId":'"$TENANT_A_ID"',"name":"数据导出","concurrencyLimit":5,"timeoutSeconds":600,"maxRetries":1,"isEnabled":true}')
assert_eq "类型配置创建 (带租户归属)" "0" "$(echo "$RC" | jsonget "d['code']")"

# ---------- 4. Webhook 接收端 (宿主机, 后端容器经 host.docker.internal 回调) ----------
say "启动 Webhook 接收端 (验签 HMAC)..."
python3 "$SCRIPT_DIR/webhook_receiver.py" --port 19999 --secret "$SECRET_A" --out /tmp/smoke-webhook.json &
RECEIVER_PID=$!
trap 'kill $RECEIVER_PID 2>/dev/null || true' EXIT
sleep 1

# ---------- 5. 提交任务 (A, 带幂等键 + HMAC 签名) ----------
say "租户 A 提交任务 (submit 端点强制 HMAC 签名)"
SUBMIT_BODY="{
    \"type\":\"$TYPE_KEY\",
    \"payload\":{\"query\":\"SELECT 1\"},
    \"priority\":10,
    \"idempotencyKey\":\"smoke-$TS\",
    \"callbackUrl\":\"http://host.docker.internal:19999/hook\"}"
split_headers "$(signed_headers POST "/api/v1/client/tasks/submit" "$SUBMIT_BODY" "smoke-a-$TS" "$SECRET_A")"
RS=$(curl -sf -X POST "$BASE/api/v1/client/tasks/submit" -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" -d "$SUBMIT_BODY" -H "$_sh1" -H "$_sh2" -H "$_sh3" -H "$_sh4")
TASK_ID=$(echo "$RS" | jsonget "d['data']['id']")
[ -n "$TASK_ID" ] && ok "提交成功 (签名通过) task_id=$TASK_ID" || { bad "提交失败: $RS"; exit 1; }

# 篡改 body 后签名失效 → 拒绝
split_headers "$(signed_headers POST "/api/v1/client/tasks/submit" "$SUBMIT_BODY" "smoke-a-$TS" "$SECRET_A")"
TAMPER=$(curl -s -X POST "$BASE/api/v1/client/tasks/submit" -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" -d "{\"type\":\"$TYPE_KEY\",\"payload\":{},\"idempotencyKey\":\"tamper-$TS\"}" -H "$_sh1" -H "$_sh2" -H "$_sh3" -H "$_sh4")
assert_eq "篡改 body 被签名拒绝" "10302" "$(echo "$TAMPER" | jsonget "d['code']")"

# 幂等: 同键再提交返回同一 id
split_headers "$(signed_headers POST "/api/v1/client/tasks/submit" '{"type":"'"$TYPE_KEY"'","payload":{},"idempotencyKey":"smoke-'"$TS"'"}' "smoke-a-$TS" "$SECRET_A")"
RS2=$(curl -sf -X POST "$BASE/api/v1/client/tasks/submit" -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" \
  -d "{\"type\":\"$TYPE_KEY\",\"payload\":{},\"idempotencyKey\":\"smoke-$TS\"}" -H "$_sh1" -H "$_sh2" -H "$_sh3" -H "$_sh4")
assert_eq "幂等键命中返回同任务" "$TASK_ID" "$(echo "$RS2" | jsonget "d['data']['id']")"

# ---------- 6. 隔离断言 ----------
say "租户隔离断言"
RB_GET=$(curl -sf -H "Authorization: Bearer $TB" "$BASE/api/v1/client/tasks/$TASK_ID")
assert_eq "B 查 A 任务 → 20100" "20100" "$(echo "$RB_GET" | jsonget "d['code']")"

RB_HTTP=$(curl -s -o /tmp/smoke-b-poll.json -w '%{http_code}' -X POST "$BASE/api/v1/worker/tasks/poll" \
  -H "Authorization: Bearer $TB" \
  -H "Content-Type: application/json" -d '{"taskType":"'"$TYPE_KEY"'","strategy":"PRIORITY","workerId":"wkr-b"}')
RB_DATA=$(jsonget "d['data']" < /tmp/smoke-b-poll.json)
if [ "$RB_HTTP" = "200" ] && { [ -z "$RB_DATA" ] || [ "$RB_DATA" = "None" ]; }; then
  ok "B worker poll → 空 (隔离)"
else
  bad "B worker poll 应为空 (http=$RB_HTTP body=$(head -c 200 /tmp/smoke-b-poll.json))"
fi

# ---------- 7. A worker 消费全流程 ----------
say "租户 A worker poll → progress → result"
RP=$(curl -sf -X POST "$BASE/api/v1/worker/tasks/poll" -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" -d '{"taskType":"'"$TYPE_KEY"'","strategy":"PRIORITY","workerId":"wkr-a"}')
ET=$(echo "$RP" | jsonget "d['data']['executionToken']")
VER=$(echo "$RP" | jsonget "d['data']['version']")
[ -n "$ET" ] && ok "抢占成功 executionToken=$ET" || { bad "poll 失败: $RP"; exit 1; }

curl -sf -X POST "$BASE/api/v1/worker/tasks/$TASK_ID/progress" -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" \
  -d "{\"currentStepKey\":\"fetch\",\"stepProgress\":100,\"executionToken\":$ET,\"version\":$VER}" > /dev/null
ok "进度上报"

curl -sf -X POST "$BASE/api/v1/worker/tasks/$TASK_ID/result" -H "Authorization: Bearer $TA" \
  -H "Content-Type: application/json" \
  -d "{\"status\":\"SUCCESS\",\"result\":{\"fileUrl\":\"oss://smoke/out.csv\"},\"executionToken\":$ET,\"version\":$((VER+1))}" > /dev/null
ok "终态上报 SUCCESS"

say "等待 Webhook 投递 (outbox 扫描 5s 周期)..."
for i in $(seq 1 20); do [ -f /tmp/smoke-webhook.json ] && break; sleep 1; done
if [ -f /tmp/smoke-webhook.json ]; then
  assert_eq "Webhook 收到且 HMAC 验签通过" "ok" "$(jsonget "d['verify']" < /tmp/smoke-webhook.json)"
  assert_eq "Webhook 事件状态 SUCCESS" "SUCCESS" "$(jsonget "d['body']['status']" < /tmp/smoke-webhook.json)"
else
  bad "Webhook 未收到"
fi

# ---------- 8. reset-secret (宽限 + 撤会话) ----------
say "reset-secret"
TID=$(docker compose exec -T postgres psql -U admin -d lotask4j -tAc \
  "SELECT id FROM asts_tenant WHERE name='smoke-a-$TS'")
RR=$(curl -sf -X POST "$BASE/api/v1/admin/tenants/$TID/reset-secret" -H "Authorization: Bearer $PT")
NEW_SECRET=$(echo "$RR" | jsonget "d['data']['tenantSecret']")
[ -n "$NEW_SECRET" ] && ok "新密钥签发" || bad "新密钥签发"

OLD_LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=smoke-a-$TS&client_secret=$SECRET_A")
assert_eq "旧密钥宽限期内仍可换 Token" "0" "$(echo "$OLD_LOGIN" | jsonget "d['code']")"

NEW_LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=smoke-a-$TS&client_secret=$NEW_SECRET")
assert_eq "新密钥可换 Token" "0" "$(echo "$NEW_LOGIN" | jsonget "d['code']")"

# ---------- 9. embed 短期 token ----------
say "嵌入配置 + 短期 token"
RE=$(curl -sf -X POST "$BASE/api/v1/admin/embed-config/configs" -H "Authorization: Bearer $PT" \
  -H "Content-Type: application/json" -d "{
    \"tenantId\":$TENANT_A_ID,\"configKey\":\"ek-smoke-$TS\",\"configName\":\"smoke\",
    \"userId\":\"u-smoke\",\"componentType\":\"task-list\",\"isOpen\":1}")
assert_eq "嵌入配置创建 (带租户归属)" "0" "$(echo "$RE" | jsonget "d['code']")"

COOKIE=$(curl -s -D - -o /dev/null "$BASE/web-embed/task-list?accessKey=ek-smoke-$TS" | grep -i "set-cookie" | grep "ASTS_EMBED_TOKEN=" || true)
[ -n "$COOKIE" ] && ok "embed 短期 token 已种 Cookie" || bad "embed token 未签发"

# ---------- 10. 防爆破 ----------
say "防爆破 (5 次失败锁定)"
for i in 1 2 3 4 5; do
  curl -s -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=smoke-b-$TS&client_secret=wrong-$i" > /dev/null
done
LOCKED=$(curl -s -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=smoke-b-$TS&client_secret=$SECRET_B")
LOCK_CODE=$(echo "$LOCKED" | jsonget "d['code']")
if [ "$LOCK_CODE" != "0" ]; then ok "正确凭据亦被锁 (code=$LOCK_CODE)"; else bad "未触发锁定"; fi

# ---------- 结果 ----------
printf '\n\033[36m[SMOKE]\033[0m ===== 结果: %d 通过 / %d 失败 =====\n' "$PASS" "$FAIL"
[ "$FAIL" = "0" ]
