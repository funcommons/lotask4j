# 发布记录

遵循语义化版本；详细变更见仓库 [Releases](https://github.com/funcommons/lotask4j/releases) 与 git log。

## 2026-09

### 多租户隔离（framework4j-tenant）

- `POST /api/v1/auth/token` 切换为框架内置 client_credentials 端点，统一签发 TENANT 型 JWT（claim `tenant_id`，平台身份=0）
- 三域守卫：admin 域 `@PlatformDomain` / client+worker 域 `@TenantDomain`
- 数据面收口：五张业务表加 `tenant_id` 全链路过滤 + PG RLS POLICY 兜底；索引重建租户打头
- `asts_application` 演进为 `asts_tenant`（框架契约 18 列，密钥 AES-256-GCM）
- 租户管理接口 `/api/v1/admin/tenants`：创建（一次性明文）/reset-secret（24h 宽限 + 撤会话）/启停
- 嵌入组件短期 token：accessKey → TENANT JWT → `ASTS_EMBED_TOKEN` Cookie
- **Webhook 防伪造（R4）**：投递携带签名三头 `X-ASTS-Event-Id / X-ASTS-Timestamp / X-ASTS-Signature`
- client GET 端点不再匿名开放（嵌入走短期 token）

### 工程硬化

- framework4j v1.5.1（web/id/datasource/redis/accesstoken/signature/rate-limit）+ tenant/-tenant-tck 显式引入
- Flyway 版本化迁移（V1 基线 → V4 租户化）；任务表按月 RANGE 分区滚动预建
- Outbox 模式 Webhook 可靠投递（指数退避 8 次）
- 凭据签名（HMAC）+ 租户维度滑动窗口限流 + 认证防爆破
- 任务执行事件审计（append-only）+ Micrometer 指标（`lotask4j.*`）

### 测试基线

- 后端 431 用例全绿，**JaCoCo 100% 行覆盖门禁**（`mvn verify` 强制）
- 前端 vitest 222 用例 + playwright e2e 65 用例全绿

## 更早

见 git 历史（六项硬化、前端统一重写等）。
