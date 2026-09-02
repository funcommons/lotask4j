#!/usr/bin/env python3
"""Smoke 用 Webhook 接收端: 校验 X-ASTS-* 签名三头后落盘。

契约 (与 docs/user-guide/webhook.md 一致):
  X-ASTS-Event-Id  事件 id (幂等键, 本脚本不校验重复)
  X-ASTS-Timestamp epoch millis (±5min)
  X-ASTS-Signature Base64(HmacSHA256(secret, ts + "\\n" + rawBody))
验证通过后写 {"verify":"ok","body":{...}} 到 --out 文件, 供冒烟脚本断言。
"""
import argparse
import base64
import hashlib
import hmac
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

ARGS = None  # populated in main


def verify(secret: str, ts: str, body: bytes, signature: str) -> bool:
    if abs(time.time() * 1000 - int(ts)) > 5 * 60 * 1000:
        return False
    to_sign = (ts + "\n").encode() + body
    expected = base64.b64encode(
        hmac.new(secret.encode(), to_sign, hashlib.sha256).digest()
    ).decode()
    return hmac.compare_digest(expected, signature)


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        ts = self.headers.get("X-ASTS-Timestamp", "")
        sig = self.headers.get("X-ASTS-Signature", "")
        event_id = self.headers.get("X-ASTS-Event-Id", "")

        ok = bool(sig) and verify(ARGS.secret, ts, body, sig)
        print(f"[receiver] event={event_id} verified={ok}", flush=True)

        result = {
            "verify": "ok" if ok else "fail",
            "event_id": event_id,
            "body": json.loads(body) if body else {},
        }
        with open(ARGS.out, "w") as f:
            json.dump(result, f)

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"code":0}')

    def log_message(self, *args):  # 静默默认访问日志
        pass


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=19999)
    p.add_argument("--secret", required=True, help="租户 A 明文密钥 (验签密钥)")
    p.add_argument("--out", default="/tmp/smoke-webhook.json")
    ARGS = p.parse_args()
    HTTPServer(("0.0.0.0", ARGS.port), Handler).serve_forever()
