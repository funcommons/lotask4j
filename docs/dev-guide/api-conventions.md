# 公共约定

## 基地址与版本

- 基地址：`http://<host>:9080`
- 业务接口统一前缀 `/api/v1/`：`client`（业务方）/ `worker`（执行器）/ `admin`（平台管理）
- 交互式文档：`/swagger-ui.html`，OpenAPI JSON：`/v3/api-docs`

## 统一响应信封

所有业务接口返回 HTTP 200 + 信封；HTTP 状态码只反映**传输层**结果（405/415/404/500 等），业务成败看 `code`。

```json
{"code": 0, "message": "OK", "data": { }, "success": true, "trace_id": "…"}
```

| 字段 | 说明 |
|------|------|
| `code` | `0` 成功；非 0 见[错误码](error-codes.md) |
| `data` | 业务数据，失败时通常为 null |
| `trace_id` | 链路追踪 ID，排障时请提供给平台方 |

**HTTP 状态与 code 的映射**：

| HTTP | code | 场景 |
|------|------|------|
| 200 | 业务码 | 一切业务异常（含认证失败 401、规则校验 10106） |
| 401 | — | 未携带/携带无效 Token 访问受保护接口 |
| 403 | — | 域守卫拒绝（如租户 token 打管理域） |
| 405 / 415 / 404 | 10104 / 10105 / 10400 | 方法不支持 / 媒体类型不支持 / 路径不存在 |
| 500 | 10001 | 服务端缺陷（`SYSTEM_BUSY`），请携 trace_id 反馈 |

## 认证头

```text
Authorization: Bearer <access_token>
```

## ID 格式（OpenID）

对外暴露的实体 ID（任务 ID、租户 ID 等）经 OpenID 混淆编码，形如 `Yk3xR9pQmZ2w`。请在响应中**原样回传**，不要自行解析/拼接数字 ID。

## 分页

列表接口统一参数与结构：

| 参数 | 默认 | 说明 |
|------|------|------|
| `page` | 1 | 从 1 开始 |
| `pageSize` | 20 | 建议不超过 100 |

```json
{"list": [ ], "total": 128, "page": 1, "pageSize": 20, "totalPages": 7}
```

## 限流

- 维度：带 Token 按租户（claim `tenant_id`）；无 Token 按来源 IP（`X-Forwarded-For` 优先）。
- 算法：Redis Lua 滑动窗口；规则由接口上的 `@RateLimit` 注解决定。
- 环回地址（127.0.0.1/::1）默认在白名单，本地调试不受限。
- 触发限流返回 `code=10500`（message 含"请求过于频繁"），请指数退避重试。

## 时间与编码

- 请求/响应均为 UTF-8 JSON；
- 时间字段 ISO-8601 带时区（如 `2026-09-02T10:00:00+08:00`）；
- 时间戳（Webhook）为毫秒 epoch。

## 幂等与重试

- 提交类接口建议携带幂等键（见[任务提交与幂等](../user-guide/submit-idempotency.md)）；
- 网络超时后重试前先查状态，避免重复动作；
- 写接口可叠加 HMAC 签名防篡改（见[认证与凭据](auth.md)）。

## 相关文档

- [错误码](error-codes.md)
- [Client API](client-api.md)
