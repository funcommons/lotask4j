package fun.commons.lotask4j.demo.worker;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Worker 客户端示例
 *
 * 演示如何正确集成 ASTS (Asynchronous Slow Task Service)
 *
 * 重要变更说明：
 * - ❌ 不再需要调用 /heartbeat 接口
 * - ✅ poll 操作自动更新心跳记录
 * - ✅ Worker 无需显式注册，首次 poll 自动关联
 *
 * @author lotask4j-team
 * @version 2.0.0 (心跳机制重构版本)
 */
@Slf4j
public class SimpleWorkerExample {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String taskType;
    private final int pollIntervalSeconds;

    /**
     * 构造 Worker 实例
     *
     * @param baseUrl Worker API 基础 URL (例如: http://localhost:8080/api/v1/worker)
     * @param taskType 处理的任务类型 (例如: video_transcode)
     * @param pollIntervalSeconds 轮询间隔（秒）
     */
    public SimpleWorkerExample(String baseUrl, String taskType, int pollIntervalSeconds) {
        this.baseUrl = baseUrl;
        this.taskType = taskType;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    /**
     * 启动 Worker 主循环
     */
    public void start() {
        log.info("Worker started: taskType={}, pollInterval={}s", taskType, pollIntervalSeconds);
        log.info("⚠️ 注意：新版本不再需要调用 /heartbeat，poll 自动作为心跳");

        while (true) {
            try {
                // 轮询任务 (同时作为心跳保活)
                Map<String, Object> task = pollTask();

                if (task != null) {
                    // 获取任务信息
                    String taskId = (String) task.get("task_id");
                    String type = (String) task.get("type");
                    Map<String, Object> payload = (Map<String, Object>) task.get("payload");

                    log.info("Acquired task: taskId={}, type={}", taskId, type);

                    // 执行任务
                    processTask(taskId, payload);
                } else {
                    log.debug("No tasks available, waiting {}s...", pollIntervalSeconds);
                }

                // 等待下次轮询
                TimeUnit.SECONDS.sleep(pollIntervalSeconds);

            } catch (InterruptedException e) {
                log.warn("Worker interrupted, shutting down...");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in worker loop", e);
                try {
                    TimeUnit.SECONDS.sleep(pollIntervalSeconds);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("Worker stopped");
    }

    /**
     * 轮询任务 (Poll Task)
     *
     * ✅ 重要：此操作同时作为心跳保活
     * 服务端会自动更新 Worker 的在线状态和心跳时间
     *
     * @return 任务信息，如果没有可用任务则返回 null
     */
    private Map<String, Object> pollTask() {
        String url = baseUrl + "/tasks/poll";

        // 构造请求体
        Map<String, Object> request = new HashMap<>();
        request.put("task_type", taskType);
        request.put("strategy", "PRIORITY"); // 可选: PRIORITY 或 FIFO

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
                Integer code = (Integer) result.get("code");

                if (code == 0) {
                    return (Map<String, Object>) result.get("data");
                } else {
                    log.warn("Poll failed: code={}, message={}", code, result.get("message"));
                }
            }
        } catch (Exception e) {
            log.error("Error polling task", e);
        }

        return null;
    }

    /**
     * 处理任务
     *
     * @param taskId 任务 ID
     * @param payload 任务载荷
     */
    private void processTask(String taskId, Map<String, Object> payload) {
        try {
            log.info("Processing task: {}", taskId);

            // 模拟任务执行（分多个步骤）
            String[] steps = {"init", "download", "process", "upload", "finalize"};
            for (int i = 0; i < steps.length; i++) {
                String stepKey = steps[i];
                int stepProgress = 0;

                // 模拟步骤内的进度
                while (stepProgress < 100) {
                    // 检查任务状态（用于检测取消信号）
                    if (i % 2 == 0) { // 每隔一个步骤检查一次
                        checkTaskStatus(taskId);
                    }

                    // 模拟工作
                    TimeUnit.SECONDS.sleep(1);
                    stepProgress += 20;

                    // 上报进度
                    reportProgress(taskId, stepKey, stepProgress);
                }
            }

            // 任务成功完成，上报结果
            Map<String, Object> result = new HashMap<>();
            result.put("output_file", "http://cdn.example.com/result.mp4");
            result.put("duration", 120);
            result.put("quality", "1080p");

            reportResult(taskId, "SUCCESS", result, null);

            log.info("Task completed successfully: {}", taskId);

        } catch (InterruptedException e) {
            log.warn("Task interrupted: {}", taskId);
            reportResult(taskId, "FAILED", null, "Task interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Task failed: {}", taskId, e);
            reportResult(taskId, "FAILED", null, e.getMessage());
        }
    }

    /**
     * 检查任务状态 (用于检测取消信号)
     *
     * @param taskId 任务 ID
     */
    private void checkTaskStatus(String taskId) {
        String url = baseUrl + "/tasks/" + taskId + "/status";

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                String status = (String) data.get("status");

                if (!"RUNNING".equals(status)) {
                    log.warn("Task status changed to {}, should stop execution", status);
                    throw new RuntimeException("Task cancelled or status changed");
                }
            }
        } catch (Exception e) {
            log.error("Error checking task status", e);
        }
    }

    /**
     * 上报任务进度
     *
     * @param taskId 任务 ID
     * @param currentStepKey 当前步骤键
     * @param stepProgress 步骤进度 (0-100)
     */
    private void reportProgress(String taskId, String currentStepKey, int stepProgress) {
        String url = baseUrl + "/tasks/" + taskId + "/progress";

        Map<String, Object> request = new HashMap<>();
        request.put("current_step_key", currentStepKey);
        request.put("step_progress", stepProgress);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.debug("Progress reported: taskId={}, step={}, progress={}%", taskId, currentStepKey, stepProgress);
        } catch (Exception e) {
            log.error("Error reporting progress", e);
        }
    }

    /**
     * 上报任务最终结果
     *
     * @param taskId 任务 ID
     * @param status 最终状态 (SUCCESS/FAILED/CANCELLED)
     * @param result 结果数据
     * @param errorMsg 错误信息 (失败时提供)
     */
    private void reportResult(String taskId, String status, Map<String, Object> result, String errorMsg) {
        String url = baseUrl + "/tasks/" + taskId + "/result";

        Map<String, Object> request = new HashMap<>();
        request.put("status", status);
        if (result != null) {
            request.put("result", result);
        }
        if (errorMsg != null) {
            request.put("error_msg", errorMsg);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(request), headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Result reported: taskId={}, status={}", taskId, status);
        } catch (Exception e) {
            log.error("Error reporting result", e);
        }
    }

    /**
     * 主函数 - 启动 Worker
     */
    public static void main(String[] args) {
        // 配置参数
        String baseUrl = "http://localhost:8080/api/v1/worker";
        String taskType = "video_transcode"; // 修改为实际的任务类型
        int pollIntervalSeconds = 5; // 轮询间隔

        // 创建并启动 Worker
        SimpleWorkerExample worker = new SimpleWorkerExample(baseUrl, taskType, pollIntervalSeconds);
        worker.start();
    }
}
