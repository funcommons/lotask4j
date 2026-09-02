# 密钥管理与轮换

## 密钥的存储与使用

| 环节 | 机制 |
|------|------|
| 生成 | 创建租户时框架随机生成 40 位明文，**仅创建响应展示一次** |
| 落库 | AES-256-GCM 加密存储；查询时由 typeHandler 透明解密 |
| 校验 | 认证端点比对待验密钥（含宽限期双版本） |
| 签名复用 | HMAC 请求签名、Webhook 回调签名均以租户明文密钥为密钥 |

## 轮换：reset-secret

```bash
curl -X POST $BASE/api/v1/admin/tenants/{id}/reset-secret \
  -H "Authorization: Bearer $PLATFORM_TOKEN"
```

```json
{"code": 0, "data": {"id": "x1Y2...", "name": "order-service", "tenantSecret": "<新明文>"}}
```

一次 reset 同时发生的三件事：

1. **新明文签发**（响应一次性返回）；
2. **旧密钥进入 24 小时宽限期**——双版本并行，期间新旧密钥都能换 Token（给业务方灰度更新配置的窗口）；
3. **撤销该租户全部存量会话**——所有在途 Bearer Token 立即失效。

宽限期过后，旧密钥换 Token 返回 `401`。

## 标准轮换流程

```text
1. 与业务方约定轮换窗口
2. reset-secret（保存新明文，安全渠道交付）
3. 业务方更新配置并用新钥换 Token 验证
4. 宽限期内监控旧钥换 Token 调用 → 归零后自然失效
5. 紧急情况（疑似泄露）：直接 reset，接受业务方短暂重登
```

> **注意**
> - reset 会立即踢掉该租户全部在途 Token，业务方需具备 401 自动重登能力（参考[认证与凭据](../dev-guide/auth.md)）；
> - 平台凭据（`PLATFORM_CLIENT_SECRET`）为环境变量配置，轮换走发布流程，不走本接口。

## 防爆破与凭据安全

- 认证端点按 client_id 防爆破：**5 次失败锁 15 分钟**；
- 凭据传递禁令：不出现在 URL/日志/前端代码/聊天明文；建议密管系统（Vault/KMS）分发；
- HMAC 请求签名中的 `X-Timestamp` 亦参与防重放，客户端时钟漂移过大时会验签失败——保持 NTP 同步。

## 相关文档

- [租户管理](../admin-guide/tenant.md)
- [Webhook 验签与 verify-then-act](../best-practice/webhook-verify.md)
