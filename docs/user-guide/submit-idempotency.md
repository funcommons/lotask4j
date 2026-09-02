# 任务提交与幂等

## 提交请求

`POST /api/v1/client/tasks/submit`

```json
{
  "type": "data_export",
  "payload": {"query": "SELECT * FROM orders"},
  "priority": 10,
  "idempotencyKey": "export-20260902-001",
  "callbackUrl": "https://biz.example.com/asts/callback"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | 是 | 任务类型标识；未配置/被禁用分别报 `20101` / `20102` |
| `payload` | object | 是 | 业务入参，JSONB 存储，Worker 自行解释 |
| `priority` | int | 否 | 0-100，缺省 0；数值越大越先被 `PRIORITY` 策略轮询到 |
| `idempotencyKey` | string | 否 | 幂等键，租户内 + 任务类型内唯一命名空间 |
| `callbackUrl` | string | 否 | 终态 Webhook 地址；建议 HTTPS |

> **说明**
> - `tenant_id` 只取自 Token claim，请求体中即使携带同名参数也会被忽略——杜绝跨租户写入。
> - 提交受[背压准入](../best-practice/backpressure.md)控制：队列满返回 `20006 QUEUE_FULL`。

## 幂等机制

带 `idempotencyKey` 提交时，服务端先按 **（租户， 任务类型， 幂等键）** 查找已有任务：

- **命中**：直接返回已有任务 ID，不创建新任务、不消耗队列配额。响应与首次提交完全一致。
- **未命中**：正常创建。

幂等键的推荐构造方式：

| 场景 | 推荐键 |
|------|--------|
| 用户触发的导出 | `export-{userId}-{yyyyMMdd}-{参数摘要}` |
| 消息驱动的补偿 | 业务消息 ID（天然唯一） |
| 定时批处理 | `daily-report-{yyyyMMdd}` |

> **注意**
> 幂等键的匹配发生在**提交时**而非执行时：它保证"不重复排队"，不保证业务侧副作用幂等。Worker 执行逻辑仍应遵循幂等消费原则，见[Worker 开发规范](../best-practice/worker.md)。

## 提交后的确认方式

| 方式 | 适用 | 说明 |
|------|------|------|
| 轮询 `GET /tasks/{id}` | 简单场景 | 建议 2-5 秒间隔，指数退避 |
| Webhook 回调 | 生产推荐 | 终态推送，配[验签](../best-practice/webhook-verify.md) |
| 管理台查看 | 人工排障 | 含步骤进度、事件时间线 |

## 相关文档

- [Client API](../dev-guide/client-api.md)
- [背压与限流](../best-practice/backpressure.md)
