package fun.commons.lotask4j.demo.client;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * ASTS 客户端
 *
 * 演示如何调用 ASTS API
 */
@Slf4j
@Component
public class AstsClient {

    private final WebClient webClient;

    @Value("${asts.server.url:http://localhost:8080}")
    private String serverUrl;

    public AstsClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * 提交任务
     */
    public Mono<TaskResponse> submitTask(String type, Map<String, Object> payload, int priority) {
        log.info("提交任务: type={}, priority={}", type, priority);

        Map<String, Object> request = new HashMap<>();
        request.put("type", type);
        request.put("payload", payload);
        request.put("priority", priority);

        return webClient
            .post()
            .uri(serverUrl + "/api/v1/client/tasks")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> {
                JSONObject json = JSONObject.parseObject(response);
                JSONObject data = json.getJSONObject("data");
                return new TaskResponse(data.getString("taskId"));
            })
            .doOnError(error -> log.error("提交任务失败", error));
    }

    /**
     * 获取任务详情
     */
    public Mono<TaskDetail> getTaskDetail(String taskId) {
        log.info("获取任务详情: taskId={}", taskId);

        return webClient
            .get()
            .uri(serverUrl + "/api/v1/client/tasks/{taskId}", taskId)
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> {
                JSONObject json = JSONObject.parseObject(response);
                JSONObject data = json.getJSONObject("data");
                return new TaskDetail(
                    data.getString("taskId"),
                    data.getString("type"),
                    data.getString("status"),
                    data.getIntValue("progress"),
                    data.getString("currentStep"),
                    data.getJSONArray("stepsDetail"),
                    data.getJSONObject("result")
                );
            })
            .doOnError(error -> log.error("获取任务详情失败", error));
    }

    /**
     * 取消任务
     */
    public Mono<Void> cancelTask(String taskId) {
        log.info("取消任务: taskId={}", taskId);

        return webClient
            .post()
            .uri(serverUrl + "/api/v1/client/tasks/{taskId}/cancel", taskId)
            .retrieve()
            .bodyToMono(Void.class)
            .doOnError(error -> log.error("取消任务失败", error));
    }

    /**
     * 轮询检查任务状态
     */
    public Mono<TaskDetail> pollTaskStatus(String taskId, long pollIntervalMs, long timeoutMs) {
        log.info("轮询任务状态: taskId={}, interval={}ms, timeout={}ms", taskId, pollIntervalMs, timeoutMs);

        long startTime = System.currentTimeMillis();

        return Mono.defer(() -> getTaskDetail(taskId)
            .flatMap(taskDetail -> {
                // 如果任务已完成，返回结果
                if ("SUCCESS".equals(taskDetail.status()) ||
                    "FAILED".equals(taskDetail.status()) ||
                    "CANCELLED".equals(taskDetail.status())) {
                    return Mono.just(taskDetail);
                }

                // 如果超时，返回当前状态
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    log.warn("任务轮询超时: taskId={}", taskId);
                    return Mono.just(taskDetail);
                }

                // 继续轮询
                log.debug("任务未完成，继续轮询: taskId={}, progress={}%", taskId, taskDetail.progress());
                return Mono.delay(java.time.Duration.ofMillis(pollIntervalMs))
                    .then(pollTaskStatus(taskId, pollIntervalMs, timeoutMs));
            }));
    }

    // DTO 类
    public record TaskResponse(String taskId) {}

    public record TaskDetail(
        String taskId,
        String type,
        String status,
        int progress,
        String currentStep,
        Object stepsDetail,
        Object result
    ) {}
}
