# lotask4j 异步慢任务服务 (ASTS) 文档中心

> Asynchronous Slow Task Service — 分布式异步任务处理平台：执行耗时较长（>10 秒）的业务逻辑（数据导出、视频转码、批处理等），提供实时进度反馈、任务取消、失败重试、多租户隔离与可视化管理后台。

**文档目录**

| 章节 | 内容 | 适合读者 |
|------|------|----------|
| **产品简介** | | |
| ├ [什么是异步慢任务服务](product/introduction.md) | 功能、优势、使用限制 | 全部 |
| ├ [应用场景](product/scenarios.md) | 数据导出、转码、批处理等典型场景 | 全部 |
| ├ [产品架构](product/architecture.md) | 组件拓扑与任务流转 | 全部 |
| └ [基本概念与术语表](product/glossary.md) | 任务、Worker、租约、Fencing Token 等 | 全部 |
| **快速入门** | | |
| ├ [准备工作](quick-start/prepare.md) | 环境要求、部署、建租户拿凭据 | 接入方 |
| ├ [提交第一个任务](quick-start/first-task.md) | 换 Token → 提交 → 查结果 | 接入方 |
| └ [实现第一个 Worker](quick-start/first-worker.md) | 轮询 → 执行 → 上报 | Worker 开发者 |
| **用户指南** | | |
| ├ [任务生命周期](user-guide/lifecycle.md) | 状态机与各状态语义 | 接入方 |
| ├ [任务提交与幂等](user-guide/submit-idempotency.md) | 优先级、幂等键、回调地址 | 接入方 |
| ├ [进度上报与取消](user-guide/progress-cancel.md) | 分步进度、全局进度、取消流程 | Worker 开发者 |
| ├ [回调与 Webhook](user-guide/webhook.md) | 可靠投递、签名三头、防伪造 | 接入方 |
| ├ [任务归档](user-guide/archive.md) | 归档策略与数据保留 | 运营 |
| └ [嵌入组件](user-guide/embed.md) | 三种嵌入组件与短期 token | 前端/接入方 |
| **开发指南** | | |
| ├ [认证与凭据](dev-guide/auth.md) | client_credentials、Token 互斥、防爆破 | 接入方 |
| ├ [公共约定](dev-guide/api-conventions.md) | 响应信封、HTTP 状态、分页、限流 | 开发者 |
| ├ [Client API](dev-guide/client-api.md) | 提交/查询/取消/列表/统计 | 接入方 |
| ├ [Worker API](dev-guide/worker-api.md) | 轮询/上报进度/上报结果 | Worker 开发者 |
| ├ [Admin API](dev-guide/admin-api.md) | 租户/类型/任务/统计管理 | 平台运营 |
| └ [错误码](dev-guide/error-codes.md) | 全量错误码与排查建议 | 开发者 |
| **最佳实践** | | |
| ├ [Worker 开发规范](best-practice/worker.md) | 心跳、幂等消费、优雅退出 | Worker 开发者 |
| ├ [Webhook 验签与 verify-then-act](best-practice/webhook-verify.md) | 回调防伪造、敏感动作回查 | 接入方 |
| └ [背压与限流](best-practice/backpressure.md) | 队列上限、并发限制、滑动窗口 | 运营/架构 |
| **管理指南** | | |
| ├ [租户管理](admin-guide/tenant.md) | 创建、启停、密钥轮换 | 平台运营 |
| ├ [任务类型配置](admin-guide/task-type.md) | 并发/超时/重试/步骤定义 | 平台运营 |
| └ [监控与运维](admin-guide/monitoring.md) | 统计概览、Worker 监控、指标 | 运营 |
| **安全** | | |
| ├ [多租户隔离](security/tenant-isolation.md) | 三域守卫、数据隔离、RLS 兜底 | 架构/安全 |
| └ [密钥管理与轮换](security/credential-rotation.md) | AES-GCM 存储、双版本宽限、会话撤销 | 安全/运营 |
| **附录** | | |
| ├ [FAQ](faq.md) | 常见问题 | 全部 |
| └ [发布记录](release-notes.md) | 版本演进 | 全部 |

---

**接入路径推荐**

```text
业务系统接入   → 准备工作 → 提交第一个任务 → 任务提交与幂等 → Webhook 验签
Worker 开发   → 准备工作 → 实现第一个 Worker → Worker 开发规范 → Worker API
平台运营      → 租户管理 → 任务类型配置 → 监控与运维
嵌入式集成    → 嵌入组件 → 嵌入配置管理（Admin API）
```

> **说明**
> - API 基地址：`http://<host>:9080`，交互式文档见 Swagger（`http://<host>:9080/swagger-ui.html`）。
> - 全部接口返回统一响应信封（`code/message/data`），HTTP 状态与业务码的映射见[公共约定](dev-guide/api-conventions.md)。
> - 可运行接入示例（client / worker / webhook 验签）见仓库 `lotask4j-demo/` 模块。
