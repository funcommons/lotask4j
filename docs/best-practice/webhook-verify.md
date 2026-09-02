# Webhook 验签与 verify-then-act

Webhook 直接打进业务内网接口，一旦被伪造（例如伪造"任务 FAILED"触发退款），就是资金级事故。接收端必须实现两层防御。

## 第一层：验签

按[签名三头契约](../user-guide/webhook.md)校验：

```java
// 1. 防重放: |now - timestamp| <= 5min
long ts = Long.parseLong(request.getHeader("X-ASTS-Timestamp"));
if (Math.abs(System.currentTimeMillis() - ts) > 5 * 60 * 1000) reject();

// 2. 防篡改: HMAC-SHA256(secret, ts + "\n" + rawBody)
String expected = SignatureUtil.sign(tenantSecret,
        ts + "\n" + rawBody);
if (!expected.equals(request.getHeader("X-ASTS-Signature"))) reject403();

// 3. 幂等: X-ASTS-Event-Id 去重表
if (seen(eventId)) return 200;
```

**注意事项**：

- 参与签名的 body 是**原始字节**，网关/框架改写 body 会验签失败——取 rawBody 后再反序列化；
- 密钥用该任务归属租户的明文密钥；租户轮换密钥后的 24h 宽限期内可能新旧双钥并存，验签建议**双钥择一通过**；
- 无签名头的投递=无租户归属任务（平台任务），直接走第二层。

## 第二层：verify-then-act（高敏动作必做）

对退款、发货、资金类动作，不要只信回调内容——**回查任务终态后再执行**：

```text
收到 Webhook (status=FAILED, taskId=T)
   │
   ├─ 验签通过? ──否──► 403 拒绝
   │
   ├─ GET /api/v1/client/tasks/{T}  (回查终态)
   │
   ├─ 回查结果与回调一致且确为 FAILED? ──否──► 丢弃 (可疑伪造)
   │
   └─► 执行业务动作 (退款), 记录 eventId 幂等
```

| 动作级别 | 建议防御 |
|----------|----------|
| 低敏（刷新缓存、发通知） | 验签即可 |
| 高敏（退款/发货/权益变更） | 验签 + verify-then-act + 幂等 |

## 接收端实现清单

- [ ] 取 rawBody 验签（不改写 body）
- [ ] timestamp ±5 分钟校验
- [ ] `X-ASTS-Event-Id` 幂等去重（推荐 Redis SETNX + TTL 24h）
- [ ] 高敏动作 verify-then-act 回查
- [ ] 先落库后处理，立即返回 200（避免处理慢触发服务端重试风暴）
- [ ] 处理失败要有补偿（服务端最多重试 8 次，之后投递终态 FAILED）

## 相关文档

- [回调与 Webhook](../user-guide/webhook.md)
- [密钥管理与轮换](../security/credential-rotation.md)
