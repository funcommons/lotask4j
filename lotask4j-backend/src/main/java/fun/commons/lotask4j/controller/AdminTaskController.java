package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.entity.AstTaskExecutionEvent;
import fun.commons.lotask4j.service.AdminService;
import fun.commons.lotask4j.service.TaskEventRecorder;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.ratelimit.annotation.RateLimit;
import fun.commons.framework4j.tenant.annotation.PlatformDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin API 控制器
 * 提供管理后台调用的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@RequiresToken("TENANT")
@PlatformDomain            // 平台域: 仅平台身份 (tenant_id=0) 可达
@Tag(name = "Admin API", description = "管理后台接口")
public class AdminTaskController {

    private final AdminService adminService;
    private final TaskEventRecorder taskEventRecorder;

    /**
     * 获取在线 Worker 列表
     */
    @GetMapping("/workers")
    @Operation(summary = "获取在线 Worker 列表", description = "查询最近30秒内有心跳的 Worker 节点")
    public ApiResponse<List<WorkerNodeResponse>> getOnlineWorkers() {
        log.debug("Fetching online workers");

        List<WorkerNodeResponse> workers = adminService.getOnlineWorkers();

        return ApiResponse.success(workers);
    }

    /**
     * 任务类型配置管理
     */
    @PostMapping("/types")
    @Operation(summary = "新增或更新任务类型配置", description = "配置任务类型的并发限制、超时时间等参数")
    public ApiResponse<Void> saveTaskTypeConfig(@Valid @RequestBody TaskTypeConfigRequest request) {
        log.info("Saving task type config: {}", request.getTypeKey());

        adminService.saveTaskTypeConfig(request);

        return ApiResponse.success();
    }

    /**
     * 手动提交任务(管理员)
     */
    @PostMapping("/tasks/submit")
    @Operation(summary = "手动提交任务", description = "管理员手动触发任务,用于测试、调试或补单")
    public ApiResponse<SubmitTaskResponse> submitTask(@Valid @RequestBody SubmitTaskRequest request) {
        log.info("Admin submitting task: type={}", request.getType());

        // 管理员提交的任务默认高优先级
        if (request.getPriority() == null) {
            request.setPriority(100);
        }

        SubmitTaskResponse response = adminService.submitTask(request);

        return ApiResponse.success(response);
    }

    /**
     * 统计概览
     */
    @GetMapping("/stats/overview")
    @Operation(summary = "获取统计概览", description = "获取当前系统的整体运行状态")
    public ApiResponse<StatsOverviewResponse> getStatsOverview() {
        log.debug("Fetching stats overview");

        StatsOverviewResponse response = adminService.getStatsOverview();

        return ApiResponse.success(response);
    }

    /**
     * P1-3: 任务执行事件历史。
     * 用于排查、审计、可视化任务时间线。
     */
    @GetMapping("/tasks/{id}/events")
    @Operation(summary = "获取任务事件历史 (P1-3)", description = "返回该任务所有执行事件的倒序列表（默认最多 100 条）")
    public ApiResponse<List<AstTaskExecutionEvent>> getTaskEvents(
            @OpenId
            @Parameter(description = "任务唯一标识", required = true, example = "YeirYkxHuQ")
            @PathVariable("id") Long id,
            @Parameter(description = "返回条数 (1-1000, 超出按 100 截断)")
            @RequestParam(name = "limit", required = false, defaultValue = "100") Integer limit) {

        return ApiResponse.success(taskEventRecorder.historyOf(id, limit));
    }

    /**
     * 获取所有任务类型配置列表
     */
    @GetMapping("/types")
    @Operation(summary = "获取任务类型配置列表", description = "获取所有已配置的任务类型")
    public ApiResponse<List<TaskTypeConfigResponse>> getAllTaskTypeConfigs() {
        log.debug("Fetching all task type configs");

        List<TaskTypeConfigResponse> configs = adminService.getAllTaskTypeConfigs();

        return ApiResponse.success(configs);
    }

    /**
     * 获取单个任务类型配置
     */
    @GetMapping("/types/{typeKey}")
    @Operation(summary = "获取任务类型配置详情", description = "根据类型标识获取配置详情")
    public ApiResponse<TaskTypeConfigResponse> getTaskTypeConfig(@PathVariable("typeKey") String typeKey) {
        log.debug("Fetching task type config: {}", typeKey);

        TaskTypeConfigResponse config = adminService.getTaskTypeConfig(typeKey);

        return ApiResponse.success(config);
    }

    /**
     * 删除任务类型配置
     */
    @DeleteMapping("/types/{typeKey}")
    @Operation(summary = "删除任务类型配置", description = "逻辑删除指定的任务类型配置")
    public ApiResponse<Void> deleteTaskTypeConfig(@PathVariable("typeKey") String typeKey) {
        log.info("Deleting task type config: {}", typeKey);

        adminService.deleteTaskTypeConfig(typeKey);

        return ApiResponse.success();
    }

    /**
     * 获取任务列表（支持筛选和分页）
     */
    @GetMapping("/tasks")
    @Operation(summary = "获取任务列表", description = "支持按任务ID（精确匹配）、状态、类型筛选及分页（数据库层面分页）")
    public ApiResponse<PageResponse<TaskDetailResponse>> getTaskList(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize
    ) {
        log.debug("获取任务列表: id={}, status={}, type={}, page={}, pageSize={}", id, status, type, page, pageSize);
        PageResponse<TaskDetailResponse> pageResponse = adminService.getTaskList(id, status, type, page, pageSize);
        return ApiResponse.success(pageResponse);
    }

    /**
     * 获取系统配置信息
     */
    @GetMapping("/system/config")
    @Operation(summary = "获取系统配置信息", description = "获取系统运行参数、数据库、Redis、JVM等配置信息")
    public ApiResponse<SystemConfigResponse> getSystemConfig() {
        log.debug("Fetching system configuration");

        SystemConfigResponse response = adminService.getSystemConfig();

        return ApiResponse.success(response);
    }
}
