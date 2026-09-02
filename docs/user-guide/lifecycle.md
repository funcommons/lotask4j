# 任务生命周期

## 状态机

```text
                 ┌──────────────┐
    submit ─────►│   PENDING    │
                 └──┬───────┬───┘
              poll  │       │ 超时/判死 (maxRetries 用尽)
              抢占  ▼       ▼
                 ┌──────────┐  ┌──────────┐
        cancel ─►│ RUNNING  │─►│  FAILED  │
                 └──┬───────┘  └──────────┘
                    │
                    ▼
              ┌────────────┐   worker 确认   ┌───────────┐
              │ CANCELLING │───────────────►│ CANCELLED │
              └────────────┘                └───────────┘
```

| 阶段 | 触发方 | 说明 |
|------|--------|------|
| `PENDING` | 业务方提交 | 进入类型队列等待抢占，受背压准入控制 |
| `RUNNING` | Worker 轮询抢占 | 派发时签发 `executionToken` 与租约 |
| `CANCELLING` | 业务方/平台发起取消 | 请求已记录，等待 Worker 上报 `CANCELLED` 终态确认 |
| `SUCCESS` | Worker 上报 | 终态，触发 Webhook |
| `FAILED` | Worker 上报 / 系统判死 | 终态；`lastErrorCode` 可用于告警归因 |
| `CANCELLED` | Worker 上报确认 | 终态，触发 Webhook |

> **说明**
> - `CANCELLING` 是两段式取消的中间态：API 发起取消只做"标记"，真正的终态由 Worker 在协作点检查后上报 `CANCELLED`。若 Worker 消失，租约过期后由 TaskReaper 按重试语义处理。
> - 终态（`SUCCESS`/`FAILED`/`CANCELLED`）与 `CANCELLING` 不可再次发起取消。

## 贯穿生命周期的四条保障线

| 保障 | 机制 | 兜底动作 |
|------|------|----------|
| 不重复执行 | 派发即签发 Fencing Token + 版本号，上报双校验 | 旧执行体上报被拒（`TASK_STATE_INVALID`） |
| 不悬死 | 每次派发带租约，Worker 上报自动续约 | TaskReaper 回收：可重试则回 `PENDING`（attempt+1），否则判 `FAILED`（`PO_TIMEOUT`） |
| 不丢结果 | 终态写库 + Outbox 同事务入队 | Webhook 指数退避 8 次，仍失败标记投递 `FAILED` |
| 可追溯 | 每次状态迁移写执行事件（append-only） | 管理台任务详情页可查看事件时间线 |

## 有效期与超时

- 任务默认有效期 7 天（`expiredAt`）；任务类型配置了 `timeoutSeconds` 时按配置计算。
- `PENDING` 超过有效期或 `RUNNING` 租约超时，均由 TaskReaper 统一处理。
- 重试语义：`attempt < maxAttempts`（任务类型 `maxRetries` + 1）时回 `PENDING` 重派，否则终判 `FAILED`。

## 归档

终态满 7 天的任务每日 02:00 被逻辑删除（`is_deleted=1`），管理台"归档任务"页只读保留，不占用当前任务列表。任务表按月 RANGE 分区，归档不删除物理数据。

## 相关文档

- [任务提交与幂等](submit-idempotency.md)
- [任务归档](archive.md)
- [错误码](../dev-guide/error-codes.md)
