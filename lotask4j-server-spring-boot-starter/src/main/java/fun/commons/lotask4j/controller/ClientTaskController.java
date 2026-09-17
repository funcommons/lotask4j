package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.dto.PageResponse;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.SubmitTaskResponse;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.service.TaskService;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.ratelimit.annotation.RateLimit;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.tenant.annotation.TenantDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端任务 API 控制器
 *
 * 提供客户端与异步任务服务的交互接口:
 * - 提交任务
 * - 查询任务详情
 * - 取消任务
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/client/tasks")
@RequiredArgsConstructor
@RequiresToken("TENANT")
@TenantDomain             // 租户域: 仅真实租户身份 (tenant_id>0) 可达; embed 走短期 token (F 阶段)
@Tag(name = "客户端任务管理", description = "提交、查询、取消异步任务")
public class ClientTaskController {

    private final TaskService taskService;

    /**
     * 提交新的异步任务
     *
     * 路径为 /submit 子路径 (2026-09 起): GET 列表与 POST 提交共用根路径时,
     * 路径级的 HMAC 签名/限流圈定无法区分方法, embed 组件的 GET 会被误伤。
     * 提交迁移到 POST-only 子路径后, 签名 path-patterns 只圈写端点。
     *
     * @param request 任务提交请求
     * @return 任务 ID (OpenID 混淆字符串)
     */
    @PostMapping("/submit")
    @RateLimit(key = "submit", limit = 30, window = "1m")
    @Operation(summary = "提交异步任务", description = "客户端提交一个新的异步任务，立即返回任务ID (OpenID格式)")
    public ApiResponse<SubmitTaskResponse> submitTask(
            @Valid @RequestBody SubmitTaskRequest request
    ) {
        log.info("收到任务提交请求: type={}, priority={}", request.getType(), request.getPriority());

        Long id = taskService.submitTask(request);

        return ApiResponse.success(new SubmitTaskResponse(id));
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID (对外接口自动转换 OpenID)
     * @return 任务详情信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询任务详情", description = "根据任务ID获取任务实时状态、进度和步骤详情")
    public ApiResponse<TaskDetailResponse> getTaskDetail(
            @OpenId
            @Parameter(description = "任务唯一标识", required = true, example = "YeirYkxHuQ")
            @PathVariable("id") Long id
    ) {
        log.info("查询任务详情: id={}", id);

        TaskDetailResponse detail = taskService.getTaskDetail(id);

        return ApiResponse.success(detail);
    }

    /**
     * 取消正在执行的任务
     *
     * @param id 任务 ID (对外接口自动转换 OpenID)
     * @return 取消结果
     */
    @PostMapping("/{id}/cancel")
    @RateLimit(key = "cancel", limit = 60, window = "1m")
    @Operation(summary = "取消任务", description = "向服务端发送取消信号，Worker 会在循环中检测并停止执行")
    public ApiResponse<Void> cancelTask(
            @OpenId
            @Parameter(description = "任务唯一标识", required = true, example = "YeirYkxHuQ")
            @PathVariable("id") Long id
    ) {
        log.info("收到任务取消请求: id={}", id);

        taskService.cancelTask(id);

        return ApiResponse.success();
    }

    /**
     * 获取任务列表 (支持筛选和分页)
     *
     * @param id 任务ID筛选（可选，精确匹配，传入OpenID字符串）
     * @param status 任务状态筛选
     * @param taskType 任务类型筛选
     * @param isArchived 是否查询归档任务（true: 归档, false: 当前, null: 全部）
     * @param createdAtStart 创建时间起始（ISO 8601格式，如：2024-01-01T00:00:00+08:00）
     * @param createdAtEnd 创建时间结束（ISO 8601格式，如：2024-12-31T23:59:59+08:00）
     * @param page 页码
     * @param pageSize 每页数量
     * @return 分页任务列表
     */
    @GetMapping
    @Operation(summary = "获取任务列表", description = "查询任务列表，支持按任务ID（精确匹配）、状态、类型、归档状态、创建时间范围筛选及分页（数据库层面分页）")
    public ApiResponse<PageResponse<TaskDetailResponse>> getTaskList(
            @Parameter(description = "任务ID筛选（精确匹配，OpenID格式）", example = "YeirYkxHuQ")
            @RequestParam(name = "id", required = false) Long id,
            @Parameter(description = "任务状态筛选", example = "PENDING")
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "任务类型筛选", example = "video_transcode")
            @RequestParam(name = "taskType", required = false) String taskType,
            @Parameter(description = "是否查询归档任务（true: 归档, false: 当前, null: 全部）", example = "false")
            @RequestParam(name = "isArchived", required = false) Boolean isArchived,
            @Parameter(description = "创建时间起始（ISO 8601格式）", example = "2024-01-01T00:00:00+08:00")
            @RequestParam(name = "createdAtStart", required = false) OffsetDateTime createdAtStart,
            @Parameter(description = "创建时间结束（ISO 8601格式）", example = "2024-12-31T23:59:59+08:00")
            @RequestParam(name = "createdAtEnd", required = false) OffsetDateTime createdAtEnd,
            @Parameter(description = "页码", example = "1")
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(name = "pageSize", required = false) Integer pageSize
    ) {
        log.info("获取任务列表: id={}, status={}, taskType={}, isArchived={}, createdAtStart={}, createdAtEnd={}, page={}, pageSize={}",
                 id, status, taskType, isArchived, createdAtStart, createdAtEnd, page, pageSize);

        PageResponse<TaskDetailResponse> pageResponse = taskService.getTaskList(id, status, taskType, isArchived, createdAtStart, createdAtEnd, page, pageSize);

        return ApiResponse.success(pageResponse);
    }

    /**
     * 获取任务统计信息
     *
     * @return 任务统计数据
     */
    /**
     * 当前租户可用的任务类型列表 (启用) — 提交表单的类型下拉数据源
     */
    @GetMapping("/types")
    @Operation(summary = "可用任务类型列表", description = "当前租户已配置且启用的任务类型 (typeKey + name)")
    public ApiResponse<List<java.util.Map<String, Object>>> listEnabledTypes() {
        return ApiResponse.success(taskService.listEnabledTypes(
                fun.commons.framework4j.tenant.context.TenantIdentity.currentTenantId(null)));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取任务统计", description = "获取待处理、运行中等任务的统计信息")
    public ApiResponse<Map<String, Long>> getTaskStats() {
        log.info("获取任务统计信息");

        Map<String, Long> stats = new HashMap<>();
        stats.put("pending", taskService.getPendingTaskCount());
        stats.put("running", taskService.getRunningTaskCount());

        return ApiResponse.success(stats);
    }
}
