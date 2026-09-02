# 认证与凭据

ASTS 采用 OAuth2 **client_credentials** 模式签发 JWT。三类身份、三个接口域，凭据与能力严格对应。

## 身份模型

| 身份 | client_id | 凭据来源 | 可达域 |
|------|-----------|----------|--------|
| 租户 | 租户名 / 租户 ID | 管理台创建租户时**一次性明文** `tenantSecret` | client + worker |
| 平台运营 | `PLATFORM` | 环境变量 `PLATFORM_CLIENT_SECRET` | admin 管理域 |
| 嵌入组件 | — | `/web-embed/{type}?accessKey=` 自动签发短期 token | client 只读 |

> **说明**
> Token claim 携带 `tenant_id`（平台身份=0，真实租户>0）。业务数据的租户归属**只取自 claim**，请求体同名字段一律忽略。

## 换取 Token

```bash
curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=<租户名|PLATFORM>&client_secret=<secret>'
```

成功响应：

```json
{"code": 0, "data": {"access_token": "<JWT>", "token_type": "Bearer", "expires_in": 28800}}
```

失败响应（注意：HTTP 仍为 200，以 `code` 区分）：

```json
{"code": 401, "message": "invalid credentials"}
```

| 错误码 | 场景 |
|--------|------|
| `401` | 凭据错误 / 租户被停用（`SUSPEND`）/ 宽限期已过 |
| `20104` | grant_type 不是 `client_credentials` |

## Token 策略

| 项 | 值 | 说明 |
|----|-----|------|
| 类型 | TENANT 型 JWT | claim: `tenant_id` |
| 有效期 | 8 小时（28800s） | 支持自动续期 |
| 互斥 | 同租户单会话 | 新 Token 签发即踢旧 Token |
| 撤销 | reset-secret / 停用 | 立即撤销该租户全部存量会话 |

**客户端最佳实践**：

1. 缓存 Token 复用（内存 + 过期前刷新），不要每次请求都换；
2. 收到 HTTP 401 或 `code=10200/10201/10205/10208` 时重新换 Token 并重试一次；
3. 多实例部署各自持有 Token 属正常行为，后者换发会使前者失效——建议共用一个刷新入口或容忍偶尔重登。

## 防爆破

认证端点按 client_id 维度防爆破：**5 次失败锁定 15 分钟**。请勿在循环里暴力重试；CI/脚本环境建议把凭据放环境变量而非命令行明文。

## 可选：HMAC 请求签名

除 Bearer 外，写接口可叠加 HMAC 请求签名（`X-Access-Key / X-Timestamp / X-Nonce / X-Signature`），对 BODY 防篡改。密钥解析规则：`X-Access-Key` = 租户名（或 `PLATFORM`）。契约：

```text
toSign      = [METHOD, path, timestamp, nonce, MD5(body)].join("\n")
X-Signature = Base64(HmacSHA256(toSign, secret))
```

前端同款实现见 `frontend/src/utils/signature.ts`。

## 相关文档

- [密钥管理与轮换](../security/credential-rotation.md)
- [多租户隔离](../security/tenant-isolation.md)
