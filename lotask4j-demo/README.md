# lotask4j 异步慢任务服务 (ASTS) - 演示模块

本模块展示如何集成和使用 lotask4j 异步慢任务服务 (ASTS)，包含客户端示例和 RESTful API 演示。

## 项目结构

```
lotask4j-demo/
├── src/
│   ├── main/
│   │   ├── java/fun/commons/lotask4j/demo/
│   │   │   ├── DemoApplication.java         # 主应用类
│   │   │   ├── client/
│   │   │   │   └── AstsClient.java          # ASTS 客户端 (Client 端)
│   │   │   ├── worker/
│   │   │   │   └── SimpleWorkerExample.java # Worker 客户端示例 (Worker 端)
│   │   │   └── controller/
│   │   │       └── DemoController.java      # 演示接口
│   │   └── resources/
│   │       └── application.yml              # 配置文件
│   └── test/                                # 测试代码
├── pom.xml                                  # Maven 配置
├── WORKER_MIGRATION_GUIDE.md                # Worker 心跳迁移指南 ⭐ NEW
└── README.md                                # 本文件
```

## 功能介绍

### 1. AstsClient - ASTS 客户端

提供以下核心功能：

- **submitTask()** - 提交异步任务
  ```java
  Map<String, Object> payload = new HashMap<>();
  payload.put("query", "SELECT * FROM users");
  payload.put("format", "xlsx");

  Mono<TaskResponse> response = astsClient.submitTask("data_export", payload, 10);
  ```

- **getTaskDetail()** - 获取任务详情
  ```java
  Mono<TaskDetail> detail = astsClient.getTaskDetail(taskId);
  ```

- **cancelTask()** - 取消任务
  ```java
  Mono<Void> cancel = astsClient.cancelTask(taskId);
  ```

- **pollTaskStatus()** - 轮询等待任务完成
  ```java
  // 每 2 秒轮询一次，最多等待 10 分钟
  Mono<TaskDetail> completed = astsClient.pollTaskStatus(taskId, 2000, 600000);
  ```

### 2. SimpleWorkerExample - Worker 客户端示例 ⭐ NEW

> **重要**: ASTS v2.0 已重构 Worker 心跳机制，独立的 `/heartbeat` 接口已废弃。

提供完整的 Worker 客户端实现：

- **Poll 自动心跳** - 无需调用 `/heartbeat`，poll 操作自动更新心跳
- **无需注册** - 首次 poll 自动关联 Worker
- **完整工作流** - poll → process → report progress → report result

#### 运行 Worker 示例

```bash
# 确保 ASTS 后端服务已启动
cd lotask4j-demo
java -cp target/classes worker.fun.commons.lotask4j.SimpleWorkerExample
```

#### Worker 配置

修改 `SimpleWorkerExample.java` 中的参数：

```java
String baseUrl = "http://localhost:8080/api/v1/worker";
String taskType = "video_transcode"; // 修改为实际的任务类型
int pollIntervalSeconds = 5;         // 轮询间隔
```

#### Worker 迁移指南

如果你有旧版本的 Worker 客户端，请参阅 **[WORKER_MIGRATION_GUIDE.md](./WORKER_MIGRATION_GUIDE.md)** 了解如何迁移到新的心跳机制。

### 3. DemoController - 演示接口

#### 演示 1: 提交数据导出任务

```bash
curl -X POST http://localhost:8081/demo/export
```

**演示数据导出任务的提交流程**

#### 演示 2: 提交视频转码任务

```bash
curl -X POST http://localhost:8081/demo/transcode
```

**演示视频转码任务的提交流程**

#### 演示 3: 查询任务状态

```bash
curl -X GET http://localhost:8081/demo/task/{taskId}
```

**演示如何查询任务的实时进度**

#### 演示 4: 轮询等待任务完成

```bash
curl -X POST http://localhost:8081/demo/wait/{taskId}?pollInterval=2000&timeout=600000
```

**演示同步等待任务完成的完整流程**

参数说明：
- `pollInterval`: 轮询间隔（毫秒），默认 2000
- `timeout`: 超时时间（毫秒），默认 600000 (10分钟)

#### 演示 5: 取消任务

```bash
curl -X POST http://localhost:8081/demo/cancel/{taskId}
```

**演示任务取消操作**

#### 演示 6: 完整流程演示

```bash
curl -X POST http://localhost:8081/demo/full-flow
```

**演示完整的任务提交、等待、完成全生命周期**

流程步骤：
1. 提交任务到 ASTS 服务
2. 轮询等待任务完成（最多 60 秒）
3. 返回最终结果

#### 演示菜单

```bash
curl -X GET http://localhost:8081/demo/menu
```

**获取所有演示接口列表**

## 快速开始

### 前置条件

1. **后端服务已启动**
   ```bash
   cd lotask4j-backend
   mvn spring-boot:run
   ```

   服务将在 `http://localhost:8080` 启动

2. **PostgreSQL 和 Redis 已启动**
   - PostgreSQL: 运行在 localhost:5432
   - Redis: 运行在 localhost:6379

### 启动演示服务

```bash
cd lotask4j-demo
mvn spring-boot:run
```

演示服务将在 `http://localhost:8081` 启动

### 测试演示

#### 方式 1: 使用 curl

```bash
# 1. 提交任务
TASK_ID=$(curl -s -X POST http://localhost:8081/demo/export | jq -r '.data.taskId')

# 2. 查询任务状态
curl -X GET http://localhost:8081/demo/task/$TASK_ID

# 3. 等待任务完成
curl -X POST http://localhost:8081/demo/wait/$TASK_ID

# 4. 取消任务 (可选)
curl -X POST http://localhost:8081/demo/cancel/$TASK_ID
```

#### 方式 2: 使用 Postman

1. 导入本项目的 Postman 集合 (如果有)
2. 设置环境变量：
   - `base_url`: http://localhost:8081
   - `asts_url`: http://localhost:8080
3. 执行各个演示请求

#### 方式 3: 使用 API 菜单

```bash
# 获取演示菜单
curl -X GET http://localhost:8081/demo/menu
```

返回所有可用的演示接口列表

## 配置说明

### application.yml

```yaml
server:
  port: 9081                      # 演示服务端口

asts:
  server:
    url: http://localhost:9080    # ASTS 服务地址 (后端默认 :9080)
  client:
    access-key: default           # 租户标识 (client_id)
    secret: test-default-tenant-secret  # 租户密钥 (管理端创建租户时一次性下发)

logging:
  level:
    fun.commons.lotask4j.demo: DEBUG  # 演示模块日志级别
```

### 自定义配置

#### 连接到远程 ASTS 服务

修改 `application.yml`：

```yaml
asts:
  server:
    url: http://your-asts-server:9080
  client:
    access-key: <租户标识>
    secret: <租户密钥>
```

> **认证说明**：ASTS 为多租户架构 — client/worker 均以租户凭据换 TENANT 型
> Bearer token (`POST /api/v1/auth/token`, client_credentials)，demo 启动时自动
> 获取并在 401 时重取。租户在管理端"租户管理"页创建，密钥明文仅创建/reset 时显示一次。

#### 修改服务端口

```yaml
server:
  port: 9000  # 改为其他端口
```

## 示例场景

### 场景 1: 数据导出

1. 提交导出任务
   ```java
   Map<String, Object> payload = new HashMap<>();
   payload.put("query", "SELECT * FROM large_table");
   payload.put("format", "xlsx");
   payload.put("email", "user@example.com");

   astsClient.submitTask("data_export", payload, 10);
   ```

2. 等待导出完成
   ```java
   astsClient.pollTaskStatus(taskId, 2000, 600000)
       .subscribe(detail -> {
           if ("SUCCESS".equals(detail.status())) {
               log.info("导出完成: {}", detail.result());
           }
       });
   ```

### 场景 2: 视频转码

1. 提交转码任务
   ```java
   Map<String, Object> payload = new HashMap<>();
   payload.put("inputPath", "/videos/source.mp4");
   payload.put("outputPath", "/videos/output.webm");
   payload.put("codec", "vp9");

   astsClient.submitTask("video_transcode", payload, 20);
   ```

2. 监控转码进度
   ```java
   astsClient.getTaskDetail(taskId)
       .subscribe(detail -> {
           log.info("转码进度: {}%", detail.progress());
       });
   ```

### 场景 3: 报表生成

1. 提交生成任务
   ```java
   Map<String, Object> payload = new HashMap<>();
   payload.put("reportType", "monthly_sales");
   payload.put("month", "2024-01");

   astsClient.submitTask("report_generation", payload, 15);
   ```

2. 获取生成结果
   ```java
   astsClient.pollTaskStatus(taskId, 3000, 300000)
       .subscribe(detail -> {
           if ("SUCCESS".equals(detail.status())) {
               JSONObject result = (JSONObject) detail.result();
               log.info("报表链接: {}", result.getString("reportUrl"));
           }
       });
   ```

## 客户端集成指南

### 集成步骤

1. **添加依赖** (已在 pom.xml 中配置)
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-webflux</artifactId>
   </dependency>
   ```

2. **复制 AstsClient 到你的项目**
   ```
   src/main/java/com/your/project/asts/AstsClient.java
   ```

3. **配置 ASTS 服务地址**
   ```yaml
   asts:
     server:
       url: http://localhost:8080
   ```

4. **注入 AstsClient 并使用**
   ```java
   @Service
   public class YourService {
       @Autowired
       private AstsClient astsClient;

       public void exportData() {
           astsClient.submitTask("data_export", payload, 10);
       }
   }
   ```

## 常见问题

### Q: 如何修改轮询间隔？

```java
// 每 500ms 轮询一次，最多等待 5 分钟
astsClient.pollTaskStatus(taskId, 500, 300000);
```

### Q: 如何处理任务失败？

```java
astsClient.getTaskDetail(taskId)
    .subscribe(
        detail -> {
            if ("FAILED".equals(detail.status())) {
                log.error("任务失败: {}", detail.result());
            }
        },
        error -> log.error("查询失败", error)
    );
```

### Q: 如何取消一个任务？

```java
astsClient.cancelTask(taskId)
    .subscribe(
        v -> log.info("任务已取消"),
        error -> log.error("取消失败", error)
    );
```

### Q: 演示服务连接不上 ASTS 后端怎么办？

1. 检查 ASTS 后端服务是否已启动：`http://localhost:8080`
2. 检查 `application.yml` 中 `asts.server.url` 配置
3. 检查防火墙设置是否允许连接

## 相关文档

- [后端服务文档](../lotask4j-backend/README.md)
- [项目总体架构](../README.md)
- [Maven 初始化指南](../MAVEN_INIT_GUIDE.md)
- [开发规范](../documents/规范/)

## 下一步

- 修改 AstsClient 适配你的业务需求
- 添加更多演示场景
- 编写单元测试
- 部署到生产环境

## License

MIT

---

**生成日期**: 2024-11-27
**项目版本**: 1.0.0-SNAPSHOT
**维护者**: lotask4j-team
