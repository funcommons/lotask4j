package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.service.WorkerService;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.ratelimit.annotation.RateLimit;
import fun.commons.framework4j.tenant.annotation.TenantDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Worker API 控制器
 * 提供 Worker 节点调用的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/worker")
@RequiredArgsConstructor
@RequiresToken("TENANT")
@TenantDomain             // 租户域: 租户级 worker, 各租户只消费自己的任务
@Tag(name = "Worker API", description = "Worker 节点接口")
public class WorkerTaskController {

    private final WorkerService workerService;

    /**
     * Worker 抢占任务
     */
    @PostMapping("/tasks/poll")
    @RateLimit(key = "poll", limit = 600, window = "1m")      // Worker 拉取高频
    @Operation(summary = "抢占任务", description = "Worker 从队列中抢占一个待处理任务")
    public ApiResponse<PollTaskResponse> pollTask(
            @Valid @RequestBody PollTaskRequest request,
            HttpServletRequest httpRequest) {

        String workerIp = getClientIp(httpRequest);
        log.debug("Worker {} polling task: type={}", workerIp, request.getTaskType());

        PollTaskResponse response = workerService.pollTask(request, workerIp);

        return ApiResponse.success(response);
    }

    /**
     * Worker 查询任务状态 (用于检测取消信号)
     */
    @GetMapping("/tasks/{id}/status")
    @RateLimit(key = "status", limit = 600, window = "1m")
    @Operation(summary = "查询任务状态", description = "Worker 查询任务状态以检测取消信号")
    public ApiResponse<TaskDetailResponse> getTaskStatus(
            @OpenId
            @Parameter(description = "任务唯一标识", required = true, example = "YeirYkxHuQ")
            @PathVariable("id") Long id) {
        log.debug("Worker querying task status: {}", id);

        TaskDetailResponse response = workerService.getTaskStatus(id);

        return ApiResponse.success(response);
    }

    /**
     * Worker 上报任务进度
     */
    @PostMapping("/tasks/{id}/progress")
    @RateLimit(key = "progress", limit = 1200, window = "1m")  // 进度上报最高频
    @Operation(summary = "上报任务进度", description = "Worker 实时上报任务执行进度")
    public ApiResponse<Void> reportProgress(
            @OpenId
            @Parameter(description = "任务唯一标识", required = true, example = "YeirYkxHuQ")
            @PathVariable("id") Long id,
            @Valid @RequestBody ReportProgressRequest request) {

        log.debug("Worker reporting progress: id={}, step={}", id, request.getCurrentStepKey());

        workerService.reportProgress(id, request);

        return ApiResponse.success();
    }

    /**
     * Worker 上报任务最终结果
     */
    @PostMapping("/tasks/{id}/result")
    @RateLimit(key = "result", limit = 600, window = "1m")
    @Operation(summary = "上报任务结果", description = "Worker 上报任务执行的最终结果")
    public ApiResponse<Void> reportResult(
            @OpenId
            @Parameter(description = "任务唯一标识", required = true, example = "YeirYkxHuQ")
            @PathVariable("id") Long id,
            @Valid @RequestBody ReportResultRequest request) {

        log.info("Worker reporting result: id={}, status={}", id, request.getStatus());

        workerService.reportResult(id, request);

        return ApiResponse.success();
    }

    /**
     * 获取客户端真实 IP ��址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个代理的情况,取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
