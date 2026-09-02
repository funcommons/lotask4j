# 基本概念与术语表

| 术语 | 英文 | 说明 |
|------|------|------|
| 任务 | Task | 一次异步业务逻辑执行的载体，含入参（`payload`）、状态、进度、结果（`result`） |
| 任务类型 | Task Type | 任务的业务分类（如 `data_export`），决定并发上限、超时、重试与步骤定义 |
| 步骤 | Step | 任务内部的执行阶段；任务类型可定义步骤权重，Worker 上报分步进度后自动折算全局进度 |
| Worker | Worker | 实际执行任务的进程；按任务类型向服务端注册并轮询抢占任务 |
| 租户 | Tenant | 接入方身份，持有唯一凭据（`tenant_id` + 密钥）；数据按租户隔离 |
| 平台身份 | Platform | 运营管理身份（`client_id=PLATFORM`），`tenant_id=0`，仅可达管理域 |
| 域守卫 | Domain Guard | 接口隔离机制：admin 域（平台）与 client/worker 域（租户）互斥 |
| 执行令牌 | Execution Token / Fencing Token | 每次派发签发的单调递增令牌，Worker 上报必须携带，防旧执行体"复活"写状态 |
| 版本号 | Version | 任务行的乐观锁版本，所有状态变更 `UPDATE ... WHERE version=?` |
| 租约 | Lease | 派发时授予 Worker 的执行时限；心跳续约，过期任务被系统回收 |
| 状态机 | State Machine | 任务状态的唯一变更入口：`PENDING → RUNNING → SUCCESS/FAILED/CANCELLED` |
| 幂等键 | Idempotency Key | 提交任务时可选传入；同（租户、类型、幂等键）重复提交返回已有任务 |
| 背压 | Backpressure | 提交准入控制：按任务类型的并发上限与队列上限拒绝超量提交 |
| Outbox | Outbox | 可靠投递模式：事件与业务数据同事务落库，再异步投递 |
| Webhook | Webhook | 任务终态的 HTTP 回调通知，携带 HMAC 签名三头 |
| 回调验签 | Verify-then-act | 接收 Webhook 后先验签（必要时回查任务终态）再执行业务动作的安全模式 |
| 归档 | Archive | 终态满 7 天的任务被逻辑删除（`is_deleted=1`），进入只读归档视图 |
| 短期 Token | Embed Token | 嵌入组件入口按 accessKey 签发的 TENANT 型 JWT，种入 Cookie 供组件调用只读接口 |

## 任务状态一览

| 状态 | 含义 | 可迁移至 |
|------|------|----------|
| `PENDING` | 已提交待执行 | `RUNNING` / `CANCELLING` / `FAILED`（超时判死） |
| `RUNNING` | Worker 执行中 | `SUCCESS` / `FAILED` / `CANCELLING` / `PENDING`（重试回收） |
| `CANCELLING` | 取消已请求，等待 Worker 确认 | `CANCELLED` / `FAILED` |
| `SUCCESS` | 执行成功（终态） | — |
| `FAILED` | 执行失败（终态） | — |
| `CANCELLED` | 已取消（终态） | — |

## 相关文档

- [任务生命周期](../user-guide/lifecycle.md)
- [Worker 开发规范](../best-practice/worker.md)
