# 最佳实践

## 1. 轮询策略

### ✅ 推荐：指数退避

\`\`\`typescript
let delay = 1000      // 初始 1 秒
const maxDelay = 30000 // 最大 30 秒

while (true) {
  const task = await getTaskStatus(id)

  if (isTerminalState(task.status)) {
    break
  }

  await sleep(delay)
  delay = Math.min(delay * 1.5, maxDelay) // 指数增长
}
\`\`\`

### ❌ 不推荐：固定间隔

\`\`\`typescript
// 不推荐：频繁轮询浪费资源
setInterval(() => {
  checkTaskStatus(id)
}, 500) // 每 500ms 轮询一次
\`\`\`

---

## 2. 错误处理

### ✅ 推荐：完整错误处理

\`\`\`java
try {
    String id = astsClient.submitTask("data_export", payload, 50, null);
    Map<String, Object> task = astsClient.waitForTask(id, 600000);

    String status = (String) task.get("status");
    if ("SUCCESS".equals(status)) {
        // 处理成功结果
        Map<String, Object> result = (Map<String, Object>) task.get("result");
        handleSuccess(result);
    } else if ("FAILED".equals(status)) {
        // 处理失败情况
        String errorMsg = (String) task.get("errorMsg");
        handleFailure(errorMsg);
    } else if ("CANCELLED".equals(status)) {
        // 处理取消情况
        handleCancellation();
    }

} catch (InterruptedException e) {
    // 处理中断
    Thread.currentThread().interrupt();
    handleInterruption();
} catch (RuntimeException e) {
    // 处理运行时异常
    handleRuntimeError(e);
}
\`\`\`

### ❌ 不推荐：忽略错误

\`\`\`java
// 不推荐：没有错误处理
String id = astsClient.submitTask("data_export", payload, 50, null);
// 假设任务一定会成功...
\`\`\`

---

## 3. 超时控制

### ✅ 推荐：设置合理超时

\`\`\`typescript
// 不同任务类型设置不同超时
const timeouts = {
  'data_export': 600000,      // 10 分钟
  'video_transcode': 1800000,  // 30 分钟
  'batch_email': 300000        // 5 分钟
}

const timeout = timeouts[taskType] || 600000
const task = await astsClient.waitForTask(id, timeout)
\`\`\`

---

## 4. Webhook 回调

### ✅ 推荐：使用 Webhook 替代轮询

\`\`\`java
// 提交任务时指定回调 URL
String id = astsClient.submitTask(
    "data_export",
    payload,
    50,
    "https://your-app.com/webhook/task-completed"  // 回调 URL
);

// 业务系统提供 Webhook 接收端点
@PostMapping("/webhook/task-completed")
public ResponseEntity<Void> handleTaskCompleted(
        @RequestBody WebhookEvent event) {

    String id = event.getId();
    String status = event.getStatus();

    if ("SUCCESS".equals(status)) {
        // 处理任务完成
        processTaskResult(id, event.getResult());
    } else {
        // 处理任务失败
        handleTaskFailure(id, event.getErrorMsg());
    }

    return ResponseEntity.ok().build();
}
\`\`\`

**优势**:
- ✅ 减少 API 调用次数，降低服务器压力
- ✅ 任务完成后立即通知，响应更及时
- ✅ 不需要客户端长时间保持连接

---

## 5. 任务优先级

### ✅ 推荐：合理设置优先级

\`\`\`typescript
// 用户触发的任务 → 高优先级
await astsClient.submitTask({
  type: 'data_export',
  payload: { query: 'SELECT * FROM orders' },
  priority: 80  // 高优先级
})

// 后台定时任务 → 低优先级
await astsClient.submitTask({
  type: 'data_cleanup',
  payload: { days: 90 },
  priority: 20  // 低优先级
})
\`\`\`

**优先级范围**: 0-100（数字越大越优先）

---

## 6. Payload 大小控制

### ✅ 推荐：控制 Payload 大小

\`\`\`java
// ✅ 推荐：传递文件 URL 而不是文件内容
Map<String, Object> payload = new HashMap<>();
payload.put("fileUrl", "https://oss.example.com/input.mp4");
payload.put("format", "720p");

// ❌ 不推荐：传递 Base64 文件内容（会超出 10MB 限制）
// payload.put("fileContent", largeBase64String);
\`\`\`

**Payload 大小限制**: 10 MB

---

## 7. 并发控制

### ✅ 推荐：使用任务类型并发限制

任务类型配置中设置 \`max_concurrency\`，避免过多任务同时执行：

\`\`\`json
{
  "typeKey": "data_export",
  "max_concurrency": 10,  // 最多 10 个并发
  "exec_timeout_sec": 600
}
\`\`\`

### ❌ 不推荐：无限制提交

\`\`\`java
// 不推荐：短时间内提交大量任务
for (int i = 0; i < 10000; i++) {
    astsClient.submitTask("data_export", payload, 50, null);
}
\`\`\`

---

## 8. 任务取消

### ✅ 推荐：提供取消功能

\`\`\`typescript
// 前端提供取消按钮
const handleCancel = async () => {
  try {
    await astsClient.cancelTask(id)
    message.success('取消请求已发送')

    // 轮询等待取消完成
    const task = await astsClient.waitForTask(id, 60000)
    if (task.status === 'CANCELLED') {
      message.info('任务已取消')
    }
  } catch (error) {
    message.error('取消失败: ' + error.message)
  }
}
\`\`\`

---

## 9. 日志记录

### ✅ 推荐：记录关键操作

\`\`\`java
@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);

    public String exportUsers(String query, String format) {
        log.info("开始提交数据导出任务, query={}, format={}", query, format);

        String id = astsClient.submitTask("data_export", payload, 80, null);

        log.info("数据导出任务已提交, id={}", id);

        return id;
    }
}
\`\`\`

---

## 10. 幂等性保证

### ✅ 推荐：使用业务 ID 防止重复提交

\`\`\`java
// 在 payload 中包含业务唯一标识
Map<String, Object> payload = new HashMap<>();
payload.put("businessId", "ORDER-20250102-001");  // 业务订单号
payload.put("query", "SELECT * FROM orders WHERE id = 'ORDER-20250102-001'");

String id = astsClient.submitTask("data_export", payload, 50, null);

// 业务系统维护 businessId → id 的映射
taskRepository.save(new TaskMapping("ORDER-20250102-001", id));
\`\`\`
