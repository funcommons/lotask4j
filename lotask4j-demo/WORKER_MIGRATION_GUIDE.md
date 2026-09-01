# Worker 客户端迁移指南

## 📢 重要变更说明

ASTS v2.0 已重构 Worker 心跳机制，**独立的 `/heartbeat` 接口已废弃**。

### 主要变更

| ��版本 (v1.x) | 新版本 (v2.0) |
|--------------|--------------|
| ❌ 需要定期调用 `/heartbeat` 保活 | ✅ poll 自动作为心跳 |
| ❌ 需要显式注册 Worker | ✅ 首次 poll 自动关联 |
| ❌ 固定心跳超时时间 (30秒) | ✅ 动态超时（基于任务类型配置） |
| ❌ 全局 Worker ID | ✅ (IP, 任务类型) 联合唯一 |

---

## 🔐 认证要求 (2026-09 租户化)

Worker 域已收口：**所有请求必须携带 TENANT 型 Bearer token**（租户级 worker 与 client 同凭据）。

```java
// 启动先换 token (client_credentials), 401 时重取
POST {authBaseUrl}/auth/token
grant_type=client_credentials&client_id=<租户标识>&client_secret=<租户密钥>
```

- 凭据来源：管理端"租户管理"页创建租户时一次性下发（密钥明文仅显示一次）
- 租户级 worker：poll 只消费**本租户**的 PENDING 任务
- 完整示例见 `SimpleWorkerExample`（自动换 token + 401 重取）

---

## 🚀 快速迁移步骤

### 1. 删除心跳相关代码

**旧代码（需要删除）:**
```java
// ❌ 删除以下代码
public void startHeartbeat() {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(() -> {
        restTemplate.postForObject(
            baseUrl + "/heartbeat",
            null,
            String.class
        );
    }, 0, 10, TimeUnit.SECONDS);
}
```

**新代码（无需心跳代码）:**
```java
// ✅ 只需要定期 poll，无需额外心跳
public void start() {
    while (true) {
        Map<String, Object> task = pollTask(); // poll 自动更新心跳
        if (task != null) {
            processTask(task);
        }
        Thread.sleep(pollIntervalSeconds * 1000);
    }
}
```

### 2. 更新 Worker 主循环

**核心改动:**
- 移除 `startHeartbeat()` 调用
- 只保留 `pollTask()` 循环
- `pollTask()` 会自动更新 Worker 心跳记录

### 3. 无需注册逻辑

**旧版本:**
```java
// ❌ 删除注册逻辑
public void register() {
    Map<String, Object> request = new HashMap<>();
    request.put("worker_id", workerId);
    request.put("task_types", Arrays.asList("video_transcode"));
    restTemplate.postForObject(baseUrl + "/register", request, String.class);
}
```

**新版本:**
```java
// ✅ 无需注册，直接开始 poll
worker.start(); // 首次 poll 自动关联 Worker
```

---

## 📝 完整示例代码

参考项目中的示例：
- **SimpleWorkerExample.java** - 完整的 Worker 客户端实现
- 演示了正确的 poll 循环、进度上报、结果提交

### 运行示例

```bash
# 1. ��保 ASTS 后端服务已启动
cd lotask4j-backend
mvn spring-boot:run

# 2. 运行 Worker 示例
cd lotask4j-demo
java -cp target/classes worker.fun.commons.lotask4j.SimpleWorkerExample
```

### 配置参数

修改 `SimpleWorkerExample.java` 中的配置：

```java
String baseUrl = "http://localhost:8080/api/v1/worker";
String taskType = "video_transcode"; // 修改为实际的任务类型
int pollIntervalSeconds = 5; // 轮询间隔（秒）
```

---

## ⚙️ 后端配置说明

### Worker 状态维护

新版本通过 **WorkerCleaner** 定时任务自动维护 Worker 状态：

| 超时阈值 | 操作 | 说明 |
|---------|------|------|
| 2x `timeout_seconds` | 标记 OFFLINE | Worker 状态变为离线 |
| 5x `timeout_seconds` | 物理删除 | 永久删除 Worker 记录 |

**timeout_seconds** 来自任务类型配置表 (`asts_task_type_config.exec_timeout_sec`)

### 示例

如果 `video_transcode` 任务的 `timeout_seconds = 600`（10分钟）：
- **20分钟** 未 poll → Worker 标记为 OFFLINE
- **50分钟** 未 poll → Worker 被物理删除

---

## 🔍 常见问题

### Q1: 我的旧 Worker 客户端还能工作吗？

**A:** 不能。`/heartbeat` 接口已删除，调用会返回 404 错误。请按本指南迁移。

### Q2: 需要修改数据库吗？

**A:** 是的，需要运行数据库迁移脚本：

```bash
psql -U postgres -d asts -f database/sql/03_alter_worker_node_for_heartbeat_refactor.sql
```

### Q3: 如何验证 Worker 是否在线？

**A:** 查询管理端接口：

```bash
curl http://localhost:8080/api/v1/admin/workers
```

只要定期 poll，Worker 就会自动显示为 ONLINE。

### Q4: Poll 间隔应该设置多少？

**A:** 建议设置为任务类型超时时间的 **1/10 到 1/5**：

- 如果任务超时 600秒（10分钟）
- Poll 间隔建议 60-120秒

### Q5: 同一 IP 可以处理多种任务类型吗？

**A:** 可以。系统按 `(worker_ip, task_type_key)` 联合唯一，同一 IP 可以启动多个 Worker 实例处理不同任务类型。

---

## 📚 相关文档

- **接口设计文档**: `documents/异步慢任务服务 (ASTS) 接口设计文档1.8.md`
- **数据库设计**: `documents/异步慢任务服务 (ASTS) 数据库设计文档 v1.9.md`
- **迁移脚本**: `database/sql/03_alter_worker_node_for_heartbeat_refactor.sql`

---

## 🆘 支持与反馈

如有问题，请联系：
- **项目团队**: lotask4j-team
- **文档更新日期**: 2025-01-29
