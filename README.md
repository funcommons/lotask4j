## 项目名称

**lotask4j 异步慢任务服务 (ASTS)**
Asynchronous Slow Task Service

> 分布式异步任务处理平台，专门处理耗时较长（>10秒）的业务逻辑，如数据导出、视频转码等。提供实时细粒度进度反馈、任务取消机制、优先级调度、多租户隔离、可视化管理后台等企业级能力。

## 运行条件

### 必需环境

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| **JDK** | 17+ | 推荐 OpenJDK 17.0.8 或 Amazon Corretto 17 |
| **Spring Boot** | 3.5.x | 由 framework4j 依赖链对齐 |
| **Maven** | 3.9+ | 项目构建工具；父 POM `lotask4j-parent` 来自内部 Maven 仓库 (`~/.m2/settings.xml`) |
| **PostgreSQL** | 12+ | 主数据库，支持 JSONB；JDBC 连接串需 `?stringtype=unspecified` |
| **Redis** | 6+ | 缓存/令牌/限流，建议准备 3 个实例 (cache/business/lock) |

### 核心依赖

- framework4j-all v1.5.1 (from `com.github.funcommons.framework4j` via JitPack — web/id/datasource/redis/accesstoken/signature/rate-limit 等)
- framework4j-tenant v1.5.1 (多租户横切面；**不在 framework4j-all 聚合中，显式引入**)
- framework4j-tenant-tck v1.5.1 (租户合规结构断言，test scope)
- MyBatis Plus 3.5.15 (ORM)
- Druid 1.2.28 (连接池和监控, spring-boot-3-starter)
- FastJSON2 2.0.41 (JSON 序列化)
- Redisson 4.6.1 (Redis 客户端)

## 运行说明

### 1. 环境准备

```bash
# 检查 JDK 版本
java -version

# 检查 Maven 版本
mvn -v

# 启动 PostgreSQL 服务
# Linux/Mac: systemctl start postgresql
# Windows: services.msc 启动 PostgreSQL

# 启动 Redis 服务
redis-server
# 或使用 Docker
docker run -d -p 6379:6379 redis:latest
```

### 2. 数据库初始化

数据库结构由 **Flyway** 管理（`lotask4j-backend/src/main/resources/db/migration/`）：

- 存量库：`baseline-on-migrate` 自动打基线标记（baseline-version=1），仅执行 V2+ 增量迁移
- 全新空库：从 `V1__baseline.sql` 全量建表，依次应用 V2（任务表按月分区）/ V3（outbox）/ V4（多租户隔离）

启动应用即自动完成迁移（Flyway 使用独立直连 datasource，不走 Druid）。

### 3. 构建项目

```bash
mvn clean install -DskipTests

# 或仅清理
mvn clean
```

### 4. 运行应用

**方式 A: 使用 Maven 直接运行**
```bash
cd lotask4j-backend
mvn spring-boot:run
```

**方式 B: 构建 JAR 后运行**
```bash
cd lotask4j-backend
mvn clean package -DskipTests
java -jar target/lotask4j-1.0.0-SNAPSHOT.jar
```

### 5. 访问应用（端口 9080）

| 页面 | 地址 | 说明 |
|------|------|------|
| **Swagger API 文档** | http://localhost:9080/swagger-ui.html | 交互式 API 测试 |
| **OpenAPI JSON** | http://localhost:9080/v3/api-docs | API 定义文件 |
| **Druid 监控** | http://localhost:9080/druid/index.html | SQL 统计、慢查询 |
| **健康检查** | http://localhost:9080/actuator/health | 应用健康状态 |
| **管理前端 (dev)** | http://localhost:9083 | `cd frontend && pnpm dev`，dev-mock 可零后端运行 |

## 认证与多租户

系统为**多租户架构**（framework4j-tenant）：租户即接入方，凭据从管理端创建租户时一次性下发（明文仅显示一次，库内 AES-256-GCM 加密）。

**三类身份**：

| 身份 | client_id | 凭据来源 | 可达域 |
|------|-----------|---------|--------|
| **租户** | 租户 id/name | 管理端创建租户时的一次性明文 `tenantSecret` | client（提交/查询/取消）+ worker（消费） |
| **平台运营** | `PLATFORM` | 环境变量 `PLATFORM_CLIENT_SECRET` | admin 管理域（跨租户） |
| **嵌入组件** | — | `/web-embed/{type}?accessKey=...` 入口自动签发短期 token（cookie） | client 只读 |

### API 测试（curl 全链路）

```bash
BASE=http://localhost:9080

# 1. 平台身份登录 (管理域)
PLATFORM_TOKEN=$(curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=PLATFORM&client_secret=lotask4j-platform-dev-secret' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["access_token"])')

# 2. 创建租户 (tenantSecret 明文仅此一次)
curl -X POST $BASE/api/v1/admin/tenants \
  -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"order-service","description":"订单中心接入"}'

# 3. 租户身份登录 (client_id=租户名, client_secret=上一步返回的 tenantSecret)
TENANT_TOKEN=$(curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=order-service&client_secret=<tenantSecret>' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["access_token"])')

# 4. 提交任务 (POST /submit 子路径)
curl -X POST $BASE/api/v1/client/tasks/submit \
  -H "Authorization: Bearer $TENANT_TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"data_export","payload":{"query":"SELECT * FROM users"},"priority":10}'
# → {"code":0,"data":{"id":"<OpenID 格式任务 id>"}}

# 5. 查询任务详情 / 取消 (OpenID 格式 id)
curl -H "Authorization: Bearer $TENANT_TOKEN" $BASE/api/v1/client/tasks/<id>
curl -X POST -H "Authorization: Bearer $TENANT_TOKEN" $BASE/api/v1/client/tasks/<id>/cancel
```

**安全机制**（均可配置）：租户数据隔离（业务表 `tenant_id` 只取自 token claim + PG RLS 兜底）、写端点 HMAC 请求签名（`X-Access-Key/X-Timestamp/X-Nonce/X-Signature`）、限流（租户维度滑动窗口）、登录防爆破（5 次失败锁 15 分钟）、密钥重置宽限期（旧密钥 24h 双版本并行 + 撤销全部会话）。

**Webhook 回调**（R4 防伪造）：任务终态经 outbox 可靠投递到 `callback_url`，携带签名三头 `X-ASTS-Event-Id`（幂等去重）/ `X-ASTS-Timestamp`（±5min 防重放）/ `X-ASTS-Signature`（HMAC-SHA256，密钥=租户密钥）。接收方推荐 verify-then-act（高敏动作回查任务终态后再执行），验签示例见 `lotask4j-demo/.../WebhookReceiverExample.java`。

## 测试说明

### 单元测试

```bash
# 运行所有单元测试 (含 JaCoCo 覆盖率门禁 ≥75%/包)
mvn test

# 运行特定测试类
mvn test -Dtest=TaskServiceTest

# 生成覆盖率报告
mvn test jacoco:report
```

## 技术架构

### 整体架构

```
┌─────────────────┐
│   客户端应用    │ (业务系统, 租户凭据)
└────────┬────────┘
         │ HTTP/REST API (Bearer + 可选 HMAC 签名)
         ▼
┌─────────────────────────────────────┐
│   API 服务层 (Cluster)              │
│  - 三域守卫 (platform/tenant/embed) │
│  - 提交 / 查询 / 取消 / 统计        │
│  - 认证 (framework4j-tenant)        │
└─────────────┬───────────────────────┘
              │ SQL (tenant_id 隔离 + RLS)
              ▼
┌─────────────────────────────────────┐
│   PostgreSQL 数据库 (核心, 按月分区) │
│  - asts_task (任务主表)             │
│  - asts_tenant (租户/凭据)          │
│  - asts_outbox (webhook 可靠投递)   │
└──────────────┬───────────────────────┘
               │
         ┌─────┴─────┐
         ▼           ▼
    ┌────────┐   ┌────────┐
    │ Redis  │   │ Worker │ (租户级 worker, 各消其队)
    │ 缓存   │   │ Cluster│
    └────────┘   └────────┘
```

### 核心组件

| 组件 | 说明 | 技术 |
|------|------|------|
| **API 服务** | 请求处理和响应 | Spring Boot MVC |
| **多租户** | 三域守卫/认证/密钥生命周期 | framework4j-tenant |
| **数据持久化** | 任务数据存储 | MyBatis Plus + PostgreSQL |
| **分布式 ID** | 任务和实体 ID 生成 | SnowflakeDistributor |
| **缓存/会话** | 令牌/限流/nonce | Redis (多实例) |
| **监控** | 数据库监控和性能分析 | Druid |
| **日志** | 应用日志和追踪 | SLF4J + Logback |
| **API 文档** | 接口文档自动生成 | OpenAPI 3.x (SpringDoc) |

### 数据库模型

**核心表**（结构由 Flyway 版本化迁移管理）:
- `asts_task` - 任务主表（按月 RANGE 分区；JSONB 字段存储步骤详情、入参、结果）
- `asts_task_type_config` - 任务类型配置表（租户内唯一 type_key）
- `asts_task_execution_event` - 任务执行事件（append-only audit）
- `asts_worker_node` - Worker 节点注册表（租户级）
- `asts_web_embed_config` - 嵌入组件配置（accessKey → 租户归属）
- `asts_tenant` - 租户表（framework4j-tenant 契约，密钥 AES-256-GCM）
- `asts_outbox` - Webhook 可靠投递 outbox（跨租户事件总线）

**关键字段**:
- `tenant_id` - 租户归属（隔离生命线，只从 token claim 取）
- `status` - 任务状态 (PENDING/RUNNING/SUCCESS/FAILED/CANCELLING/CANCELLED)
- `progress` - 全局进度 (0-100%)
- `steps_detail` / `payload` / `result` - 步骤详情/入参/结果 (JSONB)
- `is_deleted` - 逻辑删除标志 (0: 未删除, 1: 已归档)

### 任务归档机制

**归档策略**:
- 自动归档：每天凌晨 2:00 自动归档 7 天前已完成的任务 (SUCCESS/FAILED/CANCELLED)
- PENDING/RUNNING 状态的任务不会被归档
- 归档通过逻辑删除实现 (is_deleted = 1)，数据仍保留在数据库中

**前端查看**:
- 任务管理页面提供"当前任务"和"归档任务"两个标签页
- 当前任务：显示 is_deleted = 0 的任务
- 归档任务：显示 is_deleted = 1 的历史任务（只读模式）

**定时任务**:
- `TaskArchiver` - 每天凌晨 2:00 执行归档（并滚动预建下月分区）
- `WorkerCleaner` - 每分钟清理离线 Worker 节点
- `TaskReaper` - 回收租约过期的僵死任务
- `OutboxPublisher` - 扫描 outbox 投递 webhook（指数退避）

## 协作者

- **lotask4j-team** - 项目维护团队

## 快速导航

- **前端说明**: [`frontend/CLAUDE.md`](./CLAUDE.md)（含架构约定与开发规范）
- **接入示例**: [`lotask4j-demo/README.md`](./lotask4j-demo/README.md)（client 提交 + worker 消费 + webhook 验签）
- **产品/接口/数据库设计文档**: 位于内部 wiki（本仓库不含 `documents/` 目录）
