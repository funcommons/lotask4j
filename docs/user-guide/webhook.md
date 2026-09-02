# 回调与 Webhook

任务进入终态（`SUCCESS` / `FAILED` / `CANCELLED`）时，ASTS 向提交时指定的 `callbackUrl` 投递 HTTP POST 通知。投递基于 **Outbox 可靠投递模式**，并提供 **HMAC 签名三头**防伪造。

## 投递机制

```text
任务终态写库 ──同事务──► asts_outbox 入队 (PENDING)
                              │
                   OutboxPublisher 每 5 秒扫描
                              │
                    HTTP POST callbackUrl ── 2xx → SENT
                              │
                        非 2xx / 异常
                              ▼
                    attempt+1, 指数退避重试 (上限 1 小时)
                              │
                        8 次仍失败 → 投递 FAILED
```

> **说明**
> - 未配置 `callbackUrl` 的任务不投递。
> - 投递失败的终态不影响任务本身；管理台可查看投递状态（`callbackStatus`）。
> - 接收端应保证幂等：同一事件可能因重试被投递多次，以 `X-ASTS-Event-Id` 去重。

## 请求格式

```http
POST /asts/callback HTTP/1.1
Content-Type: application/json
X-ASTS-Event-Id: 7348291029384756
X-ASTS-Timestamp: 1788278925584
X-ASTS-Signature: 6tL8pQ0m3vXw2nK9bCy1dA==
```

```json
{
  "event": "TASK_FINISHED",
  "task_id": "100001",
  "type": "data_export",
  "status": "FAILED",
  "result": null,
  "timestamp": 1788278925584
}
```

| 字段 | 说明 |
|------|------|
| `event` | 固定 `TASK_FINISHED` |
| `task_id` | 任务 ID（雪花 Long 的字符串形式） |
| `type` | 任务类型 |
| `status` | 终态：`SUCCESS` / `FAILED` / `CANCELLED` |
| `result` | Worker 上报的执行结果（原样透传，可能为 null） |

## 签名三头（R4 防伪造）

| Header | 说明 |
|--------|------|
| `X-ASTS-Event-Id` | 事件 ID（outbox 行 ID），接收方幂等去重键 |
| `X-ASTS-Timestamp` | 毫秒时间戳，接收方校验 **±5 分钟**防重放 |
| `X-ASTS-Signature` | `Base64(HmacSHA256(租户密钥, timestamp + "\n" + rawBody))` |

验签示例（Java，完整类见 `lotask4j-demo/.../WebhookReceiverExample`）：

```java
String toSign = timestamp + "\n" + rawBody;
String expected = SignatureUtil.sign(tenantSecret, toSign);
if (!expected.equals(signature)) {
    // 403 拒绝
}
// 再校验 |now - timestamp| <= 5min
```

> **注意**
> - 签名密钥 = 该任务归属租户的**明文租户密钥**。租户轮换密钥后，宽限期内新投递使用新钥。
> - 无租户归属的任务（平台任务/存量数据）投递时**不带签名头**——接收方务必走 verify-then-act 兜底，见[Webhook 验签与 verify-then-act](../best-practice/webhook-verify.md)。

## 接收端返回值约定

| 接收端返回 | 服务端行为 |
|-----------|-----------|
| 2xx | 投递成功（`SENT`），不再重试 |
| 3xx / 4xx / 5xx / 超时 / 异常 | 视为失败，指数退避重试 |

> **说明**
> 建议接收端**先落库再处理**，收到即返回 200——业务处理异步化，避免处理超时触发无谓重试。

## 相关文档

- [Webhook 验签与 verify-then-act](../best-practice/webhook-verify.md)
- [密钥管理与轮换](../security/credential-rotation.md)
