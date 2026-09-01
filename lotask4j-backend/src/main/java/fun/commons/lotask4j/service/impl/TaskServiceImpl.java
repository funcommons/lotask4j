package fun.commons.lotask4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.commons.lotask4j.dto.PageResponse;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.service.TaskService;
import fun.commons.lotask4j.service.TaskStateMachine;
import fun.commons.lotask4j.service.TaskSubmitGuard;
import fun.commons.framework4j.tenant.context.TenantIdentity;
import fun.commons.framework4j.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 异步任务业务服务实现 — P0: 状态变更全走 {@link TaskStateMachine}。
 *
 * 租户隔离 (D 阶段): tenantId 只从 token claim 取 (TenantIdentity.currentTenantId(null),
 * body 同名字段忽略); claim 缺失 (单测/裸调) 不过滤。生产由 @TenantDomain 保证 claim 必在。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<AstTaskMapper, AstTask> implements TaskService {

    private final AstTaskTypeConfigMapper taskTypeConfigMapper;
    private final TaskStateMachine stateMachine;
    private final TaskSubmitGuard submitGuard;

    /**
     * 提交异步任务 — P0-5: 同 (type, idempotencyKey) 命中即返回首次 ID。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitTask(SubmitTaskRequest request) {
        Long tenantId = TenantIdentity.currentTenantId(null);
        try {
            // P0-5: 同 (租户, key, type) 直接命中已存在任务
            if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
                AstTask existing = stateMachine.findByIdempotencyKey(
                        request.getType(), request.getIdempotencyKey(), tenantId);
                if (existing != null) {
                    log.info("幂等命中: 已存在任务 id={}, type={}, key={}",
                            existing.getId(), request.getType(), request.getIdempotencyKey());
                    return existing.getId();
                }
            }

            // P1-5: 背压准入 (max_queued / max_concurrency)
            submitGuard.checkOrThrow(request.getType());

            AstTaskTypeConfig typeConfig = taskTypeConfigMapper.selectOne(
                    new LambdaQueryWrapper<AstTaskTypeConfig>()
                            .eq(AstTaskTypeConfig::getTypeKey, request.getType())
                            .eq(AstTaskTypeConfig::getIsDeleted, 0));

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime expiredAt;

            if (typeConfig != null && typeConfig.getTimeoutSeconds() != null
                    && typeConfig.getTimeoutSeconds() > 0) {
                expiredAt = now.plusSeconds(typeConfig.getTimeoutSeconds());
                log.debug("任务类型 {} 使用配置超时时间: {} 秒",
                        request.getType(), typeConfig.getTimeoutSeconds());
            } else {
                expiredAt = now.plusDays(7);
                log.debug("任务类型 {} 使用默认超时时间: 7 天", request.getType());
            }

            AstTask task = new AstTask();
            // 直接分配 ID / version / attempt, 不绕一圈 stateMachine.createNewTask
            // （stateMachine 主要作用于状态变更, 新任务不在状态机管理范围内）
            long newId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
            task.setId(newId);
            task.setStatus("PENDING");
            task.setAttempt(1);
            task.setMaxAttempts(1);
            task.setVersion(0);
            task.setTaskTypeKey(request.getType());
            task.setPriority(request.getPriority() != null ? request.getPriority() : 0);
            task.setPayload(request.getPayload());
            task.setCallbackUrl(request.getCallbackUrl());
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            task.setExpiredAt(expiredAt);
            task.setIdempotencyKey(request.getIdempotencyKey());
            task.setIsDeleted(0);
            task.setTenantId(tenantId);

            String payloadJson = com.alibaba.fastjson2.JSON.toJSONString(
                    request.getPayload() != null ? request.getPayload() : new java.util.HashMap<>());
            String resultJson = "{}";

            baseMapper.insertTask(task, payloadJson, resultJson);

            log.info("任务提交成功: id={}, type={}, priority={}, expiredAt={}, idempotencyKey={}",
                    newId, task.getTaskTypeKey(), task.getPriority(), task.getExpiredAt(),
                    task.getIdempotencyKey());

            return newId;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("任务提交失败", e);
            throw new ApiException(BusinessCode.TASK_SUBMIT_FAILED.getCode(),
                    "任务提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务详情。
     */
    @Override
    public TaskDetailResponse getTaskDetail(Long taskId) {
        AstTask task = baseMapper.selectByIdWithTypeName(taskId, TenantIdentity.currentTenantId(null));
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(),
                    BusinessCode.TASK_NOT_FOUND.getMessage());
        }
        return toResponse(task);
    }

    /**
     * 取消任务 — P0: 走 {@link TaskStateMachine#requestCancel}。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTask(Long taskId) {
        Long tenantId = TenantIdentity.currentTenantId(null);
        AstTask task = tenantId != null
                ? baseMapper.selectByIdWithTypeName(taskId, tenantId)
                : baseMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(),
                    BusinessCode.TASK_NOT_FOUND.getMessage());
        }

        // 终态不可取消
        if (TaskStatusIsTerminal(task.getStatus())) {
            throw new ApiException(BusinessCode.TASK_CANCEL_NOT_ALLOWED.getCode(),
                    "任务状态不允许取消: " + task.getStatus());
        }

        try {
            stateMachine.requestCancel(taskId, task.getVersion(), tenantId);
            return true;
        } catch (ApiException e) {
            // CAS 失败 — 任务已被改
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(),
                    "任务状态不允许取消: " + task.getStatus());
        }
    }

    private boolean TaskStatusIsTerminal(String status) {
        // 终态 + CANCELLING 都视为不允许再次 "发起" cancel
        // (CANCELLING 已经在等 Worker 确认, 再请求 cancel 只会重复写状态)
        return "SUCCESS".equals(status) || "FAILED".equals(status)
                || "CANCELLED".equals(status) || "CANCELLING".equals(status);
    }

    /**
     * 获取任务类型名称。
     */
    private String getTaskTypeName(String typeKey) {
        if (typeKey == null || typeKey.isEmpty()) {
            return "";
        }
        try {
            AstTaskTypeConfig config = taskTypeConfigMapper.selectByTypeKey(typeKey);
            return config != null ? config.getName() : typeKey;
        } catch (Exception e) {
            log.warn("获取任务类型名称失败: typeKey={}", typeKey, e);
            return typeKey;
        }
    }

    @Override
    public long getPendingTaskCount() {
        return baseMapper.countPendingTasks(TenantIdentity.currentTenantId(null));
    }

    @Override
    public long getRunningTaskCount() {
        return baseMapper.countRunningTasks(TenantIdentity.currentTenantId(null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupTimeoutTasks(int timeoutSeconds) {
        int count = baseMapper.resetTimeoutTasks(timeoutSeconds);
        if (count > 0) {
            log.info("Reaper 清理超时任务: count={}, timeout={}s", count, timeoutSeconds);
        }
        return count;
    }

    @Override
    public PageResponse<TaskDetailResponse> getTaskList(Long id, String status, String taskType,
                                                        Boolean isArchived,
                                                        OffsetDateTime createdAtStart,
                                                        OffsetDateTime createdAtEnd,
                                                        Integer page, Integer pageSize) {
        log.debug("获取任务列表: id={}, status={}, taskType={}, isArchived={}, createdAtStart={}, createdAtEnd={}, page={}, pageSize={}",
                id, status, taskType, isArchived, createdAtStart, createdAtEnd, page, pageSize);

        int currentPage = (page != null && page > 0) ? page : 1;
        int size = (pageSize != null && pageSize > 0) ? pageSize : 20;
        long offset = (long) (currentPage - 1) * size;
        long limit = size;

        Long tenantId = TenantIdentity.currentTenantId(null);
        long total = baseMapper.countTasks(id, status, taskType, isArchived, createdAtStart, createdAtEnd, tenantId);
        List<AstTask> tasks = baseMapper.selectPageWithTypeName(offset, limit, id, status, taskType,
                isArchived, createdAtStart, createdAtEnd, tenantId);

        List<TaskDetailResponse> list = tasks.stream().map(this::toResponse).collect(Collectors.toList());
        return PageResponse.of(list, total, currentPage, size);
    }

    /**
     * 实体 → 响应。
     */
    private TaskDetailResponse toResponse(AstTask task) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(task.getId());
        response.setType(task.getTaskTypeKey());
        response.setTypeName(task.getTypeName());
        response.setStatus(task.getStatus());
        response.setProgress(task.getProgress());
        response.setCurrentStep(task.getCurrentStepKey());
        response.setStepsDetail(task.getStepsDetail());
        response.setPayload(task.getPayload());
        response.setResult(task.getResult());
        response.setErrorMsg(task.getErrorMsg());
        response.setPriority(task.getPriority());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setTimeoutSeconds(task.getTimeoutSeconds());
        response.setExpiredAt(task.getExpiredAt());

        // P0 字段
        response.setAttempt(task.getAttempt());
        response.setMaxAttempts(task.getMaxAttempts());
        response.setVersion(task.getVersion());
        response.setExecutionId(task.getExecutionId());
        response.setExecutionToken(task.getExecutionToken());
        response.setWorkerId(task.getWorkerId());
        response.setLeaseExpireAt(task.getLeaseExpireAt());
        response.setRequestedCancelAt(task.getRequestedCancelAt());
        response.setLastErrorCode(task.getLastErrorCode());
        response.setLastErrorMessage(task.getLastErrorMessage());
        response.setIdempotencyKey(task.getIdempotencyKey());

        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            response.setDurationSeconds(
                    java.time.temporal.ChronoUnit.SECONDS.between(
                            task.getStartedAt(), task.getFinishedAt()));
        }
        return response;
    }
}
