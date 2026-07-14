## 项目名称

**lotask4j 异步慢任务服务 (ASTS)**
Asynchronous Slow Task Service

> 分布式异步任务处理平台，专门处理耗时较长（>10秒）的业务逻辑，如数据导出、视频转码等。提供实时细粒度进度反馈、任务取消机制、优先级调度、可视化管理后台等企业级能力。

## 运行条件

### 必需环境

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| **JDK** | 17+ | 推荐 OpenJDK 17.0.8 或 Amazon Corretto 17 |
| **Spring Boot** | 3.2.x | LTS 长期支持版本 |
| **Maven** | 3.9+ | 项目构建工具 |
| **PostgreSQL** | 12+ | 主数据库，支持 JSONB |
| **Redis** | 6+ | 缓存和分布式锁，建议准备 3 个实例 (cache/business/lock) |

### 核心依赖

- framework4j-all v1.1.3 (from `com.github.funcommons.framework4j` via JitPack) (API、ID、DataSource、Redis、AccessToken 等)
- MyBatis Plus 3.5.5 (ORM)
- Druid 1.2.20 (连接池和监控)
- FastJSON2 2.0.41 (JSON 序列化)
- Redisson 3.25.0 (Redis 客户端)
- RocketMQ 5.1.4 (消息队列)

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

参考 `MAVEN_INIT_GUIDE.md` 的"快速开始 → 环境准备 → 配置数据库"部分

### 3. 构建项目

```bash
cd D:\ldx2\tms\lotask4j

# 清理并下载依赖
mvn clean install -DskipTests

# 或仅清理
mvn clean
```

### 4. 运行应用

**方式 A: 使用 Maven 直接运行**
```bash
mvn spring-boot:run
```

**方式 B: 构建 JAR 后运行**
```bash
mvn clean package -DskipTests
java -jar target/lotask4j-1.0.0-SNAPSHOT.jar
```

### 5. 访问应用

| 页面 | 地址 | 说明 |
|------|------|------|
| **Swagger API 文档** | http://localhost:8080/swagger-ui.html | 交互式 API 测试 |
| **OpenAPI JSON** | http://localhost:8080/v3/api-docs | API 定义文件 |
| **Druid 监控** | http://localhost:8080/druid/index.html | SQL 统计、慢查询 |
| **健康检查** | http://localhost:8080/actuator/health | 应用健康状态 |
| **性能指标** | http://localhost:8080/actuator/metrics | Prometheus 格式 |

## 测试说明

### 单元测试

```bash
# 运行所有单元测试
mvn test

# 运行特定测试类
mvn test -Dtest=TaskServiceTest

# 生成覆盖率报告
mvn test jacoco:report
```

### API 测试

使用 Swagger UI 或 curl 进行 API 测试：

```bash
# 提交任务
curl -X POST http://localhost:8080/api/v1/client/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "type": "data_export",
    "payload": {"query": "SELECT * FROM users"},
    "priority": 10
  }'

# 查询任务详情
curl -X GET http://localhost:8080/api/v1/client/tasks/550e8400-e29b-41d4-a716-446655440000

# 取消任务
curl -X POST http://localhost:8080/api/v1/client/tasks/550e8400-e29b-41d4-a716-446655440000/cancel
```

## 技术架构

### 整体架构

```
┌─────────────────┐
│   客户端应用    │ (业务系统)
└────────┬────────┘
         │ HTTP/REST API
         ▼
┌─────────────────────────────────────┐
│   API 服务层 (Cluster)              │
│  - 提交任务                         │
│  - 查询任务详情                     │
│  - 取消任务                         │
│  - 统计报表                         │
└─────────────┬───────────────────────┘
              │ SQL
              ▼
┌─────────────────────────────────────┐
│   PostgreSQL 数据库 (核心)           │
│  - asts_task (任务主表)             │
│  - asts_task_type_config (配置)    │
│  - asts_worker_node (节点表)       │
└──────────────┬──────────────────────┘
               │
         ┌─────┴─────┐
         ▼           ▼
    ┌────────┐   ┌────────┐
    │ Redis  │   │ Worker │ (分布式执行)
    │ 缓存   │   │ Cluster│
    └────────┘   └────────┘
```

### 核心组件

| 组件 | 说明 | 技术 |
|------|------|------|
| **API 服务** | 请求处理和响应 | Spring Boot MVC |
| **数据持久化** | 任务数据存储 | MyBatis Plus + PostgreSQL |
| **分布式 ID** | 任务和实体 ID 生成 | SnowflakeDistributor |
| **缓存层** | 热数据缓存 | Redis (多实例) |
| **监控** | 数据库监控和性能分析 | Druid |
| **日志** | 应用日志和追踪 | SLF4J + Logback |
| **API 文档** | 接口文档自动生成 | OpenAPI 3.x (SpringDoc) |

### 数据库模型

**核心表**:
- `asts_task` - 任务主表 (JSONB 字段存储步骤详情、入参、结果)
- `asts_task_type_config` - 任务类型配置表
- `asts_worker_node` - Worker 节点注册表
- `asts_task_history` - 历史任务归档表 (冷数据)

**关键字段**:
- `status` - 任务状态 (PENDING/RUNNING/SUCCESS/FAILED/CANCELLING/CANCELLED)
- `progress` - 全局进度 (0-100%)
- `steps_detail` - 步骤详情 (JSONB)
- `payload` - 任务入参 (JSONB)
- `result` - 执行结果 (JSONB)
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
- `TaskArchiver` - 每天凌晨 2:00 执行归档任务
- `WorkerCleaner` - 每分钟清理离线 Worker 节点

## 协作者

- **lotask4j-team** - 项目维护团队

## 快速导航

- **详细指南**: 参考 [`MAVEN_INIT_GUIDE.md`](./MAVEN_INIT_GUIDE.md)
- **产品设计**: [`documents/异步慢任务服务(ASTS)产品设计.md`](./documents/异步慢任务服务(ASTS)产品设计.md)
- **接口设计**: [`documents/异步慢任务服务 (ASTS) 接口设计文档1.8.md`](./documents/异步慢任务服务%20(ASTS)%20接口设计文档1.8.md)
- **数据库设计**: [`documents/异步慢任务服务 (ASTS) 数据库设计文档 v1.9.md`](./documents/异步慢任务服务%20(ASTS)%20数据库设计文档%20v1.9.md)
- **开发规范**: [`documents/规范/Java SpringBoot 后端开发规范v1.0.md`](./documents/规范/Java%20SpringBoot%20后端开发规范v1.0.md)
- **SDK 指南**: [`documents/规范/framework4j-sdk 用户指南.md`](./documents/规范/framework4j-sdk%20用户指南.md)
