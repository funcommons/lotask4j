# 提交第一个任务

本文以"数据导出"为例，三步完成异步任务接入：**换 Token → 提交任务 → 查询结果**。

## 前提条件

- 已完成[准备工作](prepare.md)，持有租户凭据（`client_id` + `tenantSecret`）。

## 第一步：换取访问 Token

```bash
BASE=http://localhost:9080

TOKEN=$(curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=order-service&client_secret=<tenantSecret>' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["access_token"])')
```

Token 有效期 8 小时；同一租户重复换取会互斥（新 Token 生效、旧 Token 失效）。请缓存复用，失效（HTTP 401）后再换。

## 第二步：提交任务

```bash
curl -X POST $BASE/api/v1/client/tasks/submit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
        "type": "data_export",
        "payload": {"query": "SELECT * FROM orders", "target": "oss://bucket/orders.csv"},
        "priority": 10,
        "idempotencyKey": "export-20260902-001",
        "callbackUrl": "https://order.example.com/asts/callback"
      }'
```

响应：

```json
{"code": 0, "data": {"id": "Yk3xR9pQmZ2w"}}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `type` | 是 | 任务类型标识，需已配置且启用 |
| `payload` | 是 | 任务入参，任意 JSON，Worker 自行解释 |
| `priority` | 否 | 0-100，越大越先被轮询抢占；缺省 0 |
| `idempotencyKey` | 否 | 幂等键：同（租户+类型+幂等键）重复提交返回已有任务 |
| `callbackUrl` | 否 | 任务终态 Webhook 回调地址（签名三头防伪造） |

## 第三步：查询任务结果

```bash
# 详情（id 为提交响应返回的 OpenID 格式）
curl -H "Authorization: Bearer $TOKEN" $BASE/api/v1/client/tasks/<id>
```

轮询建议间隔 2-5 秒；也可以直接等 Webhook 通知（推荐）：

```json
{
  "code": 0,
  "data": {
    "status": "RUNNING",
    "progress": 60,
    "stepsDetail": [
      {"key": "fetch", "status": "done", "progress": 100},
      {"key": "write", "status": "processing", "progress": 20}
    ]
  }
}
```

任务进入 `SUCCESS` 后，`result` 字段即为 Worker 上报的执行结果（如文件地址）。

## 使用幂等键防重复提交

网络重试、消息重复消费是常态。带同一个 `idempotencyKey` 重复提交：

```bash
curl -X POST $BASE/api/v1/client/tasks/submit -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"data_export","payload":{},"idempotencyKey":"export-20260902-001"}'
```

服务端发现已有任务时**直接返回原任务 ID，不创建新任务**——请求方无感知。

## 相关文档

- [任务提交与幂等](../user-guide/submit-idempotency.md)
- [回调与 Webhook](../user-guide/webhook.md)
- [Client API](../dev-guide/client-api.md)
