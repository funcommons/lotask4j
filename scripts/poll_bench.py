#!/usr/bin/env python3
"""poll 并发压测 — SKIP LOCKED 抢占语义的正确性 + 吞吐基线。

流程 (对运行中的 ASTS 真实栈):
  1. 平台身份创建压测租户 + 任务类型 (幂等: 复用已存在的)
  2. 提交 N 个任务 (HMAC 签名)
  3. M 个并发 worker 线程疯狂 poll, 拿到任务立即上报 SUCCESS (契约: version+1)
  4. 断言: 每个任务恰好被消费一次 (dup=0 为正确性硬指标), 输出吞吐/p50/p95

用法:
  python3 scripts/poll_bench.py --tasks 50 --workers 8 \
      [--base http://localhost:19080] [--platform-secret smoke-platform-secret]
退出码: 0 全部消费且无重复; 2 有重复/有任务未被消费 (抢占语义被破坏)
"""
import argparse
import concurrent.futures
import hashlib
import hmac
import base64
import json
import sys
import threading
import time
import urllib.request
import uuid

LOCK = threading.Lock()
DELIVERED = []          # [(taskId, t_deliver)]
POLL_LATENCIES = []     # [ms]


def http(method, url, body=None, headers=None):
    req = urllib.request.Request(url, data=body.encode() if body else None, method=method)
    req.add_header("Content-Type", "application/json")
    for h in (headers or []):
        req.add_header(*h.split(":", 1))
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


def signed_headers(method, path, body, access_key, secret):
    ts = str(int(time.time() * 1000))
    nonce = str(uuid.uuid4())
    md5 = hashlib.md5((body or "").encode()).hexdigest()
    to_sign = "\n".join([method, path, ts, nonce, md5])
    sig = base64.b64encode(hmac.new(secret.encode(), to_sign.encode(), hashlib.sha256).digest()).decode()
    return [f"X-Access-Key:{access_key}", f"X-Timestamp:{ts}", f"X-Nonce:{nonce}", f"X-Signature:{sig}"]


def post_with_retry(method, url, body, headers, retries=3):
    """限流 (10500, submit 30/min·租户) 退避重试 — 服务端提示 60s 窗口"""
    for attempt in range(retries + 1):
        status, r = http(method, url, body, headers)
        if status == 200 and r.get("code") == 10500:
            time.sleep(20 + attempt * 20)
            continue
        return status, r
    return status, r


def jget(d, *keys):
    for k in keys:
        d = d.get(k)
        if d is None:
            return None
    return d


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:19080")
    ap.add_argument("--platform-secret", default="smoke-platform-secret")
    ap.add_argument("--tasks", type=int, default=20,
                    help="任务数 (submit 限流 30/min/租户, 默认 20 留余量)")
    ap.add_argument("--workers", type=int, default=8)
    args = ap.parse_args()
    base, ts = args.base, int(time.time())

    # 1. 平台 token → 建租户/类型
    _, r = http("POST", f"{base}/api/v1/auth/token",
                "grant_type=client_credentials&client_id=PLATFORM"
                f"&client_secret={args.platform_secret}".encode().decode(),
                ["Content-Type:application/x-www-form-urlencoded"])
    pt = jget(r, "data", "access_token")
    assert pt, f"平台登录失败: {r}"

    name = f"bench-{ts}"
    type_key = f"bench-{ts}"  # 每轮唯一: 残留任务不占新一轮背压水位
    _, r = http("POST", f"{base}/api/v1/admin/tenants",
                json.dumps({"name": name, "description": "bench"}),
                [f"Authorization:Bearer {pt}"])
    secret = jget(r, "data", "tenantSecret")
    tenant_id = jget(r, "data", "id")
    assert secret, f"建租户失败: {r}"

    _, r = http("POST", f"{base}/api/v1/admin/types",
                json.dumps({"typeKey": type_key, "tenantId": tenant_id, "name": "bench",
                            "concurrencyLimit": 64, "timeoutSeconds": 600,
                            "maxRetries": 0, "isEnabled": True}),
                [f"Authorization:Bearer {pt}"])
    assert r.get("code") == 0, f"建类型失败 (V5 起需租户归属): {r}"

    _, r = http("POST", f"{base}/api/v1/auth/token",
                f"grant_type=client_credentials&client_id={name}&client_secret={secret}",
                ["Content-Type:application/x-www-form-urlencoded"])
    token = jget(r, "data", "access_token")
    assert token, "租户登录失败"

    # 2. 提交 N 个任务
    submit_path = "/api/v1/client/tasks/submit"
    task_ids = []
    t0 = time.time()
    for i in range(args.tasks):
        body = json.dumps({"type": type_key, "payload": {"i": i}, "idempotencyKey": f"bench-{ts}-{i}"})
        _, r = post_with_retry("POST", f"{base}{submit_path}", body,
                               [f"Authorization:Bearer {token}"] + signed_headers(
                                   "POST", submit_path, body, name, secret))
        tid = jget(r, "data", "id")
        assert tid, f"提交失败: {r}"
        task_ids.append(str(tid))
        time.sleep(2.2)  # 配速 ~27/min, 贴近但不触碰 submit 限流 (30/min/租户)
    submit_secs = time.time() - t0
    print(f"[bench] 提交 {args.tasks} 任务耗时 {submit_secs:.1f}s ({args.tasks/submit_secs:.0f}/s)")

    # 3. M 个并发 worker 消费
    stop = threading.Event()

    def worker(wid):
        poll_path = "/api/v1/worker/tasks/poll"
        while not stop.is_set():
            body = json.dumps({"taskType": type_key, "strategy": "PRIORITY",
                               "workerId": f"bench-w{wid}"})
            t_poll = time.time()
            status, r = post_with_retry("POST", f"{base}{poll_path}", body,
                                        [f"Authorization:Bearer {token}"])
            lat = (time.time() - t_poll) * 1000
            if status != 200:
                continue
            with LOCK:
                POLL_LATENCIES.append(lat)
            data = jget(r, "data")
            if not data:
                time.sleep(0.05)
                continue
            tid, et, ver = str(data["id"]), data["executionToken"], data["version"]
            with LOCK:
                DELIVERED.append((tid, time.time()))
            # 拿到即成功上报 (契约: version+1)
            http("POST", f"{base}/api/v1/worker/tasks/{tid}/result",
                 json.dumps({"status": "SUCCESS", "result": {"w": wid},
                             "executionToken": et, "version": ver + 1}),
                 [f"Authorization:Bearer {token}"])

    def any_pending():
        # 全部任务被领取即认为收尾 (由最终断言兜底)
        return False

    t0 = time.time()
    threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(args.workers)]
    for t in threads:
        t.start()
    # 等待: 所有任务被消费或 120s 超时
    deadline = time.time() + 120
    while time.time() < deadline:
        with LOCK:
            if len(DELIVERED) >= args.tasks:
                break
        time.sleep(0.2)
    stop.set()
    time.sleep(1)
    drain_secs = time.time() - t0

    # 4. 断言与报告
    ids = [t for t, _ in DELIVERED]
    dup = len(ids) - len(set(ids))
    missing = set(task_ids) - set(ids)
    lat_sorted = sorted(POLL_LATENCIES)
    p50 = lat_sorted[len(lat_sorted) // 2] if lat_sorted else 0
    p95 = lat_sorted[int(len(lat_sorted) * 0.95)] if lat_sorted else 0

    print(f"[bench] 消费 {len(set(ids))}/{args.tasks}  重复投递 {dup}  未消费 {len(missing)}")
    print(f"[bench] 消费吞吐 {len(set(ids))/drain_secs:.1f} 任务/s  poll p50={p50:.0f}ms p95={p95:.0f}ms")

    if dup or missing:
        print(f"[bench] ✗ 抢占语义破坏: dup={dup} missing={len(missing)}")
        return 2
    print("[bench] ✓ 每个任务恰好消费一次 (SKIP LOCKED 语义保持)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
