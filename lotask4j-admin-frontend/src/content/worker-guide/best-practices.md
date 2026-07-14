# Worker 最佳实践

## 1. Poll 频率控制

### ✅ 推荐：自适应 Poll 间隔

\`\`\`java
private long pollInterval = 5000;  // 初始 5 秒
private final long MIN_INTERVAL = 1000;   // 最小 1 秒
private final long MAX_INTERVAL = 30000;  // 最大 30 秒

while (true) {
    Map<String, Object> task = astsClient.pollTask("data_export", "PRIORITY", workerIp);

    if (task != null) {
        // 有任务，减少间隔
        pollInterval = Math.max(pollInterval / 2, MIN_INTERVAL);
        executor.execute(task);
    } else {
        // 无任务，增加间隔
        pollInterval = Math.min(pollInterval * 1.5, MAX_INTERVAL);
        Thread.sleep(pollInterval);
    }
}
\`\`\`

---

## 2. 取消信号检测

### ✅ 推荐：定期检测取消信号

\`\`\`java
protected void checkCancellation(String id) throws CancelledException {
    String status = astsClient.getTaskStatus(id);
    if ("CANCELLING".equals(status)) {
        log.warn("检测到取消信号: id={}", id);
        throw new CancelledException("任务已被取消");
    }
}

// 在业务逻辑关键点检测
for (int i = 0; i < 100; i++) {
    // 每 5 秒检测一次
    if (i % 5 == 0) {
        checkCancellation(id);
    }

    // 执行业务逻辑
    processChunk(i);
}
\`\`\`

---

## 3. 进度上报策略

### ✅ 推荐：分步骤上报

\`\`\`java
// 初始化 - 5%
reportProgress(id, 5, "初始化", "init");

// 数据查询 - 5% ~ 70%
for (int i = 0; i < totalChunks; i++) {
    processChunk(i);

    int progress = 5 + (i * 65 / totalChunks);
    reportProgress(id, progress, "数据查询", "querying");
}

// 文件写入 - 70% ~ 90%
reportProgress(id, 85, "文件写入", "writing");
writeFile();

// 文件上传 - 90% ~ 100%
reportProgress(id, 95, "文件上传", "uploading");
uploadFile();
\`\`\`

### ❌ 不推荐：过于频繁上报

\`\`\`java
// 不推荐：每条记录都上报进度
for (int i = 0; i < 100000; i++) {
    processRecord(i);
    reportProgress(id, i * 100 / 100000, "处理中", "processing");  // 10万次调用！
}
\`\`\`

---

## 4. 错误处理

### ✅ 推荐：完整错误处理

\`\`\`java
@Override
protected Map<String, Object> doExecute(String id, Map<String, Object> payload)
        throws Exception {

    try {
        // 业务逻辑
        return performTask(payload);

    } catch (SQLException e) {
        // 数据库错误
        log.error("数据库错误: id={}", id, e);
        throw new Exception("数据库连接失败: " + e.getMessage());

    } catch (IOException e) {
        // IO 错误
        log.error("IO 错误: id={}", id, e);
        throw new Exception("文件操作失败: " + e.getMessage());

    } catch (CancelledException e) {
        // 取消异常
        log.warn("任务已取消: id={}", id);
        throw e;

    } catch (Exception e) {
        // 其他异常
        log.error("未知错误: id={}", id, e);
        throw new Exception("任务执行异常: " + e.getMessage());

    } finally {
        // 清理资源
        cleanupResources();
    }
}
\`\`\`

---

## 5. 资源清理

### ✅ 推荐：使用 try-with-resources

\`\`\`java
protected Map<String, Object> doExecute(String id, Map<String, Object> payload)
        throws Exception {

    // 自动关闭资源
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query);
         ResultSet rs = stmt.executeQuery()) {

        // 处理数据
        while (rs.next()) {
            checkCancellation(id);  // 检测取消
            processRow(rs);
        }

        return buildResult();

    } catch (CancelledException e) {
        // 资源会自动关闭
        throw e;
    }
}
\`\`\`

---

## 6. 并发控制

### ✅ 推荐：使用线程池

\`\`\`java
@Component
public class WorkerRunner implements CommandLineRunner {

    private final ExecutorService executorService =
        Executors.newFixedThreadPool(5);  // 最多 5 个并发任务

    @Override
    public void run(String... args) throws Exception {
        while (true) {
            Map<String, Object> task = astsClient.pollTask("data_export", "PRIORITY", workerIp);

            if (task != null) {
                // 提交到线程池执行
                executorService.submit(() -> {
                    try {
                        dataExportExecutor.execute(task);
                    } catch (Exception e) {
                        log.error("任务执行失败", e);
                    }
                });
            } else {
                Thread.sleep(5000);
            }
        }
    }
}
\`\`\`

---

## 7. 日志记录

### ✅ 推荐：结构化日志

\`\`\`java
@Component
public class DataExportExecutor extends TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(DataExportExecutor.class);

    @Override
    protected Map<String, Object> doExecute(String id, Map<String, Object> payload)
            throws Exception {

        long startTime = System.currentTimeMillis();
        log.info("开始执行数据导出: id={}, payload={}", id, payload);

        try {
            Map<String, Object> result = performExport(id, payload);

            long costMs = System.currentTimeMillis() - startTime;
            log.info("数据导出成功: id={}, costMs={}, result={}", id, costMs, result);

            return result;

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("数据导出失败: id={}, costMs={}, error={}",
                     id, costMs, e.getMessage(), e);
            throw e;
        }
    }
}
\`\`\`

---

## 8. 超时控制

### ✅ 推荐：设置任务超时

\`\`\`java
@Component
public class WorkerRunner implements CommandLineRunner {

    private final ExecutorService executorService =
        Executors.newFixedThreadPool(5);

    @Override
    public void run(String... args) throws Exception {
        while (true) {
            Map<String, Object> task = astsClient.pollTask("data_export", "PRIORITY", workerIp);

            if (task != null) {
                Future<?> future = executorService.submit(() -> {
                    dataExportExecutor.execute(task);
                });

                try {
                    // 设置超时 10 分钟
                    future.get(600, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // 任务超时，取消执行
                    future.cancel(true);
                    log.error("任务执行超时: id={}", task.get("id"));
                    astsClient.reportResult(
                        (String) task.get("id"),
                        "FAILED",
                        null,
                        "任务执行超时"
                    );
                }
            }
        }
    }
}
\`\`\`

---

## 9. 健康检查

### ✅ 推荐：提供健康检查端点

\`\`\`java
@RestController
@RequestMapping("/health")
public class HealthController {

    private final AstsWorkerClient astsClient;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();

        try {
            // 检查 ASTS 连接
            Map<String, Object> task = astsClient.pollTask("data_export", "FIFO", null);
            health.put("asts_connection", "ok");
        } catch (Exception e) {
            health.put("asts_connection", "failed");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }

        health.put("status", "healthy");
        return ResponseEntity.ok(health);
    }
}
\`\`\`

---

## 10. Graceful Shutdown

### ✅ 推荐：优雅关闭

\`\`\`java
@Component
public class WorkerRunner implements CommandLineRunner {

    private volatile boolean running = true;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Override
    public void run(String... args) throws Exception {
        // 注册 shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        while (running) {
            // Poll 和执行任务...
        }
    }

    private void shutdown() {
        log.info("Worker 正在关闭...");
        running = false;

        // 停止接受新任务
        executorService.shutdown();

        try {
            // 等待正在执行的任务完成（最多 60 秒）
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                // 强制关闭
                executorService.shutdownNow();
            }
            log.info("Worker 已安全关闭");
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
\`\`\`
