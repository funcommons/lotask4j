# 产品架构

## 组件拓扑

```text
┌─────────────────┐
│   客户端应用    │  (业务系统, 租户凭据)
└────────┬────────┘
         │ HTTP/REST API (Bearer + 可选 HMAC 签名)
         ▼
┌─────────────────────────────────────┐
│   API 服务层 (lotask4j-backend)     │
│  - 认证 (framework4j-tenant)        │
│  - 三域守卫 (platform/tenant/embed) │
│  - Client / Worker / Admin / Embed  │
└─────────────┬───────────────────────┘
              │ SQL (tenant_id 隔离 + PG RLS 兜底)
              ▼
┌─────────────────────────────────────┐
│   PostgreSQL (核心存储, 任务表按月   │
│   RANGE 分区, JSONB 存步骤/入参/结果)│
│   asts_task / asts_tenant /         │
│   asts_outbox / ...                 │
└──────────────┬──────────────────────┘
               │
         ┌─────┴─────┐
         ▼           ▼
    ┌────────┐   ┌─────────┐
    │ Redis  │   │ Worker  │  (租户级, 各消其队)
    │ 限流/  │   │ Cluster │
    │ 会话   │   └─────────┘
    └────────┘
```

## 关键组件

| 组件 | 职责 |
|------|------|
| `ClientTaskController` | 业务方入口：提交 / 查询 / 取消 / 列表 / 统计 |
| `WorkerTaskController` | Worker 入口：轮询抢占 / 上报进度 / 上报结果 |
| `AdminTaskController` | 平台管理：任务、任务类型、Worker、统计、系统配置 |
| `AdminTenantController` | 租户生命周期：创建、启停、密钥轮换 |
| `AdminWebEmbedController` / `WebEmbedController` | 嵌入组件配置管理与组件入口 |
| `TaskStateMachine` | 中心状态机：派发、续约、上报、取消、终态，全部走 SQL CAS |
| `OutboxPublisher` | Webhook 可靠投递（扫描 outbox，指数退避） |
| `TaskReaper` | 回收租约过期的僵死任务（重试或判死） |
| `TaskArchiver` | 每日归档 7 天前的终态任务 + 预建下月分区 |
| `WorkerCleaner` | 每分钟清理离线 Worker |

## 一次任务的生命流转

```text
业务方                     API 服务                    Worker
  │  POST /tasks/submit      │                           │
  ├─────────────────────────►│ 生成任务 PENDING           │
  │◄──── task_id ────────────┤                           │
  │                          │◄── POST /tasks/poll ──────┤
  │                          │ CAS 抢占 → RUNNING         │
  │                          │──── executionToken ──────►│ (Fencing 令牌)
  │                          │◄── POST /tasks/progress ──┤ (多次, 分步进度)
  │  GET /tasks/{id}         │                           │
  ├─────────────────────────►│──────────────────────────►│
  │◄── progress / steps ─────┤                           │
  │                          │◄── POST /tasks/result ────┤ (终态 + result)
  │                          │ 任务终态 → outbox 入队      │
  │◄──── Webhook (签名三头) ──┤──── OutboxPublisher ──────┤
  ▼                          ▼                           ▼
```

## 可靠性设计

| 机制 | 说明 |
|------|------|
| Fencing Token | 每次派发签发单调递增令牌，Worker 上报必须携带，旧令牌上报直接拒绝 |
| 版本号 CAS | 所有状态变更走 `UPDATE ... WHERE version=?`，杜绝并发覆盖 |
| 租约 (Lease) | 派发时带租约时长，Worker 心跳续约；过期由 TaskReaper 回收重派 |
| Outbox 模式 | 终态与回调事件同事务落库，投递失败指数退避，最终必达或显式 FAILED |
| 执行事件审计 | append-only 事件流（派发/上报/取消/终态），可追溯每次状态迁移 |

## 相关文档

- [任务生命周期](../user-guide/lifecycle.md)
- [回调与 Webhook](../user-guide/webhook.md)
