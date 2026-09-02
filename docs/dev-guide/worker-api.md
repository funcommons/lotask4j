# Worker API

面向任务执行器的接口。前缀 `/api/v1/worker`，需租户身份 Bearer Token（与 client 同一凭据体系）。所有上报带 **Fencing Token + 版本号**双校验，防止跨租户篡改与旧执行体复活写入。

## 轮询抢占任务

```http
POST /api/v1/worker/tasks/poll
```

```json
{"taskType": "data_export", "strategy": "PRIORITY", "workerId": "wkr-node-001"}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `taskType` | 是 | 要消费的任务类型；租户级隔离，只可能抢到自己租户的任务 |
| `strategy` | 否 | `PRIORITY`（默认，优先级高先出）/ `FIFO` |
| `workerId` | 否 | Worker 自定义标识，用于管理台展示与心跳；缺省按 IP+类型生成 |

**响应**（队列为空时 `data` 为 null）：

```json
{
  "code": 0,
  "data": {
    "id": "Yk3xR9pQmZ2w",
    "type": "data_export",
    "payload": {"query": "SELECT * FROM orders"},
    "priority": 80,
    "attempt": 1,
    "executionToken": 123456789,
    "version": 1
  }
}
```

> **注意**
> - 抢占即加租约（lease），超时未上报将被 TaskReaper 回收重派。
> - `executionToken` / `version` 是后续所有上报的凭据，**必须持久化**。
> - SQL 侧 `SKIP LOCKED`，多 Worker 并发轮询不会重复取到同一任务。

## 查询执行中任务状态

```http
GET /api/v1/worker/tasks/{id}/status
```

供 Worker 恢复场景对账（如进程重启后确认任务是否仍归属自己）。

## 上报进度

```http
POST /api/v1/worker/tasks/{id}/progress
```

```json
{"currentStepKey": "write", "stepProgress": 60, "executionToken": 123456789, "version": 3}
```

- 每次上报自动续约租约；
- 全局进度按任务类型步骤权重折算，见[进度上报与取消](../user-guide/progress-cancel.md)；
- 上报成功后任务 `version` +1，**下次上报携带新 version**。

## 上报终态

```http
POST /api/v1/worker/tasks/{id}/result
```

```json
{
  "status": "SUCCESS",
  "result": {"fileUrl": "oss://bucket/orders.csv"},
  "errorMsg": null,
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "executionToken": 123456789,
  "version": 4
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `status` | 是 | 仅终态：`SUCCESS` / `FAILED` / `CANCELLED` |
| `result` | 否 | 执行结果，原样透传给 Webhook 与查询接口 |
| `errorMsg` / `lastErrorCode` / `lastErrorMessage` | 失败建议携带 | 用于管理台展示与告警归因 |

**常见错误**：`20409` 状态已变更（Fencing/版本冲突、被取消、租约被回收）。

> **说明**
> 收到 `20409` 说明任务已不归属当前执行（常见：租约过期被重派、已发起取消）。Worker 应停止业务动作并重新轮询。

## 状态码速查

| 场景 | 返回 |
|------|------|
| 正常轮询（含空队列） | `code=0` |
| 上报成功 | `code=0` |
| 任务不存在 / 跨租户 | `20100` |
| 状态机冲突 | `20409` |
| 非终态 status | `20409`（`reportResult 仅接受终态`） |

## 相关文档

- [Worker 开发规范](../best-practice/worker.md)
- [实现第一个 Worker](../quick-start/first-worker.md)
- [任务生命周期](../user-guide/lifecycle.md)
