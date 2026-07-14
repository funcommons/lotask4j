package fun.commons.lotask4j.demo.controller;

import fun.commons.lotask4j.demo.client.AstsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * ASTS 演示控制器
 *
 * 演示如何使用 ASTS 客户端提交和管理任务
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final AstsClient astsClient;

    /**
     * 演示 1: 提交数据导出任务
     */
    @PostMapping("/export")
    public Mono<Map<String, Object>> submitExportTask(
        @RequestParam(defaultValue = "data_export") String type,
        @RequestParam(defaultValue = "10") int priority
    ) {
        log.info("演示: 提交数据导出任务");

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", "SELECT * FROM users WHERE created_at > '2024-01-01'");
        payload.put("format", "xlsx");
        payload.put("email", "user@example.com");

        return astsClient.submitTask(type, payload, priority)
            .map(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 0);
                result.put("message", "任务提交成功");
                result.put("data", Map.of("taskId", response.taskId()));
                return result;
            })
            .doOnError(error -> log.error("演示失败", error));
    }

    /**
     * 演示 2: 提交视频转码任务
     */
    @PostMapping("/transcode")
    public Mono<Map<String, Object>> submitTranscodeTask(
        @RequestParam(defaultValue = "video_transcode") String type,
        @RequestParam(defaultValue = "20") int priority
    ) {
        log.info("演示: 提交视频转码任务");

        Map<String, Object> payload = new HashMap<>();
        payload.put("inputPath", "/videos/input/demo.mp4");
        payload.put("outputPath", "/videos/output/demo.webm");
        payload.put("codec", "vp9");
        payload.put("quality", "high");

        return astsClient.submitTask(type, payload, priority)
            .map(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 0);
                result.put("message", "任务提交成功");
                result.put("data", Map.of("taskId", response.taskId()));
                return result;
            })
            .doOnError(error -> log.error("演示失败", error));
    }

    /**
     * 演示 3: 提交报表生成任务
     */
    @PostMapping("/report")
    public Mono<Map<String, Object>> submitReportTask(
        @RequestParam(defaultValue = "report_generation") String type,
        @RequestParam(defaultValue = "15") int priority
    ) {
        log.info("演示: 提交报表生成任务");

        Map<String, Object> payload = new HashMap<>();
        payload.put("reportType", "monthly_sales");
        payload.put("period", "2024-01");
        payload.put("format", "pdf");
        payload.put("recipients", new String[]{"manager@example.com"});

        return astsClient.submitTask(type, payload, priority)
            .map(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 0);
                result.put("message", "任务提交成功");
                result.put("data", Map.of("taskId", response.taskId()));
                return result;
            })
            .doOnError(error -> log.error("演示失败", error));
    }

    /**
     * 演示 4: 提交批量通知任务
     */
    @PostMapping("/batch")
    public Mono<Map<String, Object>> submitBatchTask(
        @RequestParam(defaultValue = "batch_notification") String type,
        @RequestParam(defaultValue = "5") int priority
    ) {
        log.info("演示: 提交批量通知任务");

        Map<String, Object> payload = new HashMap<>();
        payload.put("templateId", "welcome_email");
        payload.put("recipientCount", 1000);
        payload.put("channel", "email");

        return astsClient.submitTask(type, payload, priority)
            .map(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 0);
                result.put("message", "任务提交成功");
                result.put("data", Map.of("taskId", response.taskId()));
                return result;
            })
            .doOnError(error -> log.error("演示失败", error));
    }

    /**
     * 演示 5: 查询任务状态
     */
    @GetMapping("/task/{taskId}")
    public Mono<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        log.info("演示: 查询任务状态, taskId={}", taskId);

        return astsClient.getTaskDetail(taskId)
            .map(detail -> {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 0);
                result.put("message", "success");
                result.put("data", Map.of(
                    "taskId", detail.taskId(),
                    "type", detail.type(),
                    "status", detail.status(),
                    "progress", detail.progress(),
                    "currentStep", detail.currentStep()
                ));
                return result;
            })
            .doOnError(error -> log.error("查询失败", error));
    }

    /**
     * 演示 6: 轮询等待任务完成
     */
    @PostMapping("/wait/{taskId}")
    public Mono<Map<String, Object>> waitTaskCompletion(
        @PathVariable String taskId,
        @RequestParam(defaultValue = "2000") long pollInterval,
        @RequestParam(defaultValue = "600000") long timeout
    ) {
        log.info("演示: 轮询等待任务完成, taskId={}", taskId);

        return astsClient.pollTaskStatus(taskId, pollInterval, timeout)
            .map(detail -> {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 0);
                result.put("message", "success");
                result.put("data", Map.of(
                    "taskId", detail.taskId(),
                    "status", detail.status(),
                    "progress", detail.progress(),
                    "result", detail.result()
                ));
                return result;
            })
            .doOnError(error -> log.error("等待失败", error));
    }

    /**
     * 演示 7: 取消任务
     */
    @PostMapping("/cancel/{taskId}")
    public Mono<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        log.info("演示: 取消任务, taskId={}", taskId);

        return astsClient.cancelTask(taskId)
            .map(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 0);
                result.put("message", "任务取消成功");
                result.put("data", null);
                return result;
            })
            .doOnError(error -> log.error("取消失败", error));
    }

    /**
     * 演示 8: 完整流程演示
     */
    @PostMapping("/full-flow")
    public Mono<Map<String, Object>> demonstrateFullFlow(
        @RequestParam(defaultValue = "data_export") String type,
        @RequestParam(defaultValue = "10") int priority
    ) {
        log.info("演示: 完整工作流程");

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", "SELECT COUNT(*) FROM users");
        payload.put("format", "json");

        return astsClient.submitTask(type, payload, priority)
            .flatMap(response -> {
                log.info("步骤 1: 任务已提交, taskId={}", response.taskId());

                // 轮询等待任务完成 (最多等待 60 秒)
                return astsClient.pollTaskStatus(response.taskId(), 1000, 60000)
                    .map(detail -> {
                        log.info("步骤 2: 任务已完成, status={}, progress={}%", detail.status(), detail.progress());

                        Map<String, Object> result = new HashMap<>();
                        result.put("code", 0);
                        result.put("message", "完整流程演示成功");
                        result.put("data", Map.of(
                            "taskId", detail.taskId(),
                            "status", detail.status(),
                            "progress", detail.progress(),
                            "steps", Map.of(
                                "step1", "任务提交",
                                "step2", "任务执行",
                                "step3", "结果返回"
                            )
                        ));
                        return result;
                    });
            })
            .doOnError(error -> log.error("完整流程演示失败", error));
    }

    /**
     * 获取演示菜单
     */
    @GetMapping("/menu")
    public Map<String, Object> getDemoMenu() {
        return Map.of(
            "code", 0,
            "message", "success",
            "data", Map.of(
                "demos", new Object[]{
                    Map.of("id", "1", "name", "提交数据导出任务", "url", "/demo/export", "method", "POST"),
                    Map.of("id", "2", "name", "提交视频转码任务", "url", "/demo/transcode", "method", "POST"),
                    Map.of("id", "3", "name", "查询任务状态", "url", "/demo/task/{taskId}", "method", "GET"),
                    Map.of("id", "4", "name", "等待任务完成", "url", "/demo/wait/{taskId}", "method", "POST"),
                    Map.of("id", "5", "name", "取消任务", "url", "/demo/cancel/{taskId}", "method", "POST"),
                    Map.of("id", "6", "name", "完整流程演示", "url", "/demo/full-flow", "method", "POST")
                }
            )
        );
    }
}
