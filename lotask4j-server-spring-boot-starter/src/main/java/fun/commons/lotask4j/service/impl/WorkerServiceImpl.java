package fun.commons.lotask4j.service.impl;

import com.alibaba.fastjson2.JSON;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.entity.AstWorkerNode;
import fun.commons.lotask4j.enums.TaskStatus;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.mapper.AstWorkerNodeMapper;
import fun.commons.lotask4j.service.TaskStateMachine;
import fun.commons.lotask4j.service.WebhookService;
import fun.commons.lotask4j.service.WorkerService;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.web.ApiException;
import fun.commons.framework4j.tenant.context.TenantIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker 服务实现 — P0 改动：所有状态变更走 {@link TaskStateMachine}。
 *
 * 流程：
 * <ol>
 *   <li>pollTask: Worker 拉取 → 分配 fencing token（dispatch + start）</li>
 *   <li>reportProgress: 上报进度（CAS by version + token）</li>
 *   <li>reportResult: 上报结果（CAS by version + token）</li>
 *   <li>getTaskStatus: Worker 轮询检查是否被取消</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    private final AstWorkerNodeMapper workerNodeMapper;
    private final AstTaskMapper taskMapper;
    private final AstTaskTypeConfigMapper taskTypeConfigMapper;
    private final WebhookService webhookService;
    private final SnowflakeDistributor snowflakeDistributor;
    private final TaskStateMachine stateMachine;

    @Override
    @Transactional
    public PollTaskResponse pollTask(PollTaskRequest request, String workerIp) {
        String taskType = request.getTaskType();
        String strategy = request.getStrategy() != null ? request.getStrategy() : "PRIORITY";
        String workerId = request.getWorkerId();
        Long tenantId = TenantIdentity.currentTenantId(null);

        log.debug("Worker poll: type={}, strategy={}, ip={}, workerId={}",
                taskType, strategy, workerIp, workerId);

        // 校验任务类型存在且启用 (租户内唯一: 同 typeKey 跨租户各自配置)
        AstTaskTypeConfig typeConfig = taskTypeConfigMapper.selectByTypeKey(taskType, tenantId);
        if (typeConfig == null) {
            throw new ApiException(BusinessCode.TASK_TYPE_UNKNOWN.getCode(),
                    "未知的任务类型: " + taskType);
        }
        if (typeConfig.getIsEnabled() == null || typeConfig.getIsEnabled() != 1) {
            throw new ApiException(BusinessCode.TASK_TYPE_DISABLED.getCode(),
                    "该任务类型已被禁用: " + taskType);
        }

        // 心跳容错
        updateWorkerHeartbeatOnPoll(workerIp, taskType, workerId, tenantId);

        // 抢占（保留旧的 SKIP LOCKED + UPDATE 一体化 SQL; 租户级 worker 只消费本租户任务）
        AstTask task = taskMapper.pollAndLockTask(taskType, strategy, workerIp, tenantId);
        if (task == null) {
            log.debug("无可用任务: type={}", taskType);
            return null;
        }

        log.info("Worker {} (id={}) 抢占任务: type={}, taskId={}, version={}",
                workerIp, workerId, taskType, task.getId(), task.getVersion());

        // P0 dispatch：通过 TaskStateMachine 分配 fencing token
        // pollAndLockTask 已经 PENDING→RUNNING 并设置 started_at，但还没 execution_id/token。
        // 这里补一次：dispatch 会 CAS 期望 version = task.version，并把 PENDING 状态变为已锁；
        // 但因为 pollAndLockTask 已经改了 status=RUNNING/version+1，dispatch 的 PENDING CAS 条件
        // 会失败。所以此处改用 dispatch 路径只在 task 的 version 基础上扩字段。
        //
        // 简化：直接把 dispatch 逻辑下推到 pollAndLockTask 内联（SQL 已经设 worker_ip），
        // 同时在这里给 task 分配 execution_id + token 并 UPDATE 一遍（用 CAS by version 校验）。
        Long executionToken = stateMachine.dispatch(task.getId(), task.getVersion(), workerId, tenantId);
        // 重新读一次, 获取最新的 version 与 leaseExpireAt
        AstTask reloaded = taskMapper.selectByIdWithTypeName(task.getId(), tenantId);

        PollTaskResponse response = new PollTaskResponse();
        response.setId(reloaded.getId());
        response.setType(reloaded.getTaskTypeKey());
        response.setPayload(reloaded.getPayload());
        response.setPriority(reloaded.getPriority());
        response.setExecutionToken(executionToken);
        response.setVersion(reloaded.getVersion());
        response.setAttempt(reloaded.getAttempt());
        response.setLeaseExpireAt(reloaded.getLeaseExpireAt());
        return response;
    }

    @Override
    public TaskDetailResponse getTaskStatus(Long id) {
        AstTask task = taskMapper.selectByIdWithTypeName(id, TenantIdentity.currentTenantId(null));
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(),
                    "任务不存在: " + id);
        }
        return toResponse(task);
    }

    @Override
    @Transactional
    public void reportProgress(Long id, ReportProgressRequest request) {
        log.debug("Worker 上报进度: id={}, step={}, execToken={}, version={}",
                id, request.getCurrentStepKey(), request.getExecutionToken(), request.getVersion());

        // 校验任务存在 (租户过滤: 跨租户 id 视为不存在)
        Long tenantId = TenantIdentity.currentTenantId(null);
        AstTask task = taskMapper.selectByIdWithTypeName(id, tenantId);
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务不存在: " + id);
        }

        // P0-1 防御性前置校验: 状态必须是 RUNNING / CANCELLING (CANCELLING 时 Worker 应尽快停止)
        // 数据库侧 CAS 会再次校验, 此处只是 fail-fast
        if (!"RUNNING".equals(task.getStatus()) && !"CANCELLING".equals(task.getStatus())) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(),
                    "任务状态不允许上报进度,当前状态: " + task.getStatus());
        }

        // 构造 stepsDetail：把当前 step 标为 processing 或追加新 step
        List<Map<String, Object>> stepsDetail = task.getStepsDetail();
        if (stepsDetail == null) {
            stepsDetail = new ArrayList<>();
            task.setStepsDetail(stepsDetail);
        }

        boolean stepFound = false;
        for (Map<String, Object> step : stepsDetail) {
            if (request.getCurrentStepKey().equals(step.get("key"))) {
                step.put("status", "processing");
                step.put("progress", request.getStepProgress());
                step.put("updated_at", OffsetDateTime.now().toString());
                stepFound = true;
                break;
            }
        }
        if (!stepFound) {
            Map<String, Object> newStep = new HashMap<>();
            newStep.put("key", request.getCurrentStepKey());
            newStep.put("status", "processing");
            newStep.put("progress", request.getStepProgress());
            newStep.put("start_time", OffsetDateTime.now().toString());
            stepsDetail.add(newStep);
        }

        int globalProgress = calculateGlobalProgress(
                task.getTaskTypeKey(),
                request.getCurrentStepKey(),
                request.getStepProgress(),
                tenantId);

        // P0: CAS by version + token (含租户条件, 防跨租户篡改)
        stateMachine.reportProgress(id, request.getVersion(), request.getExecutionToken(),
                request.getCurrentStepKey(), request.getStepProgress(),
                stepsDetail, globalProgress, tenantId);

        log.info("任务进度已更新: id={}, step={}, globalProgress={}%",
                id, request.getCurrentStepKey(), globalProgress);
    }

    private int calculateGlobalProgress(String taskTypeKey, String currentStepKey, int stepProgress, Long tenantId) {
        AstTaskTypeConfig taskTypeConfig = taskTypeConfigMapper.selectByTypeKey(taskTypeKey, tenantId);
        if (taskTypeConfig == null || taskTypeConfig.getStepsConfig() == null) {
            return stepProgress;
        }

        List<Map<String, Object>> stepsDefinition = taskTypeConfig.getStepsConfig();
        if (stepsDefinition.isEmpty()) {
            return stepProgress;
        }

        int totalWeight = 0;
        int completedWeight = 0;
        int currentStepWeight = 0;
        boolean foundCurrentStep = false;

        for (Map<String, Object> step : stepsDefinition) {
            String stepKey = (String) step.get("key");
            Integer weight = step.get("weight") != null
                    ? ((Number) step.get("weight")).intValue() : 0;
            totalWeight += weight;
            if (stepKey.equals(currentStepKey)) {
                currentStepWeight = weight;
                foundCurrentStep = true;
            } else if (!foundCurrentStep) {
                completedWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return stepProgress;
        }

        double progress = (completedWeight + currentStepWeight * stepProgress / 100.0) * 100.0 / totalWeight;
        return Math.max(0, Math.min(100, (int) Math.round(progress)));
    }

    @Override
    @Transactional
    public void reportResult(Long id, ReportResultRequest request) {
        log.info("Worker 上报结果: id={}, status={}, execToken={}, version={}",
                id, request.getStatus(), request.getExecutionToken(), request.getVersion());

        Long tenantId = TenantIdentity.currentTenantId(null);
        AstTask task = taskMapper.selectByIdWithTypeName(id, tenantId);
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务不存在: " + id);
        }

        // P0-1 防御性前置校验
        if (!"RUNNING".equals(task.getStatus()) && !"CANCELLING".equals(task.getStatus())) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(),
                    "任务状态不允许上报结果,当前状态: " + task.getStatus());
        }

        TaskStatus finalStatus;
        try {
            finalStatus = TaskStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(),
                    "无效的终态: " + request.getStatus());
        }

        if (!TaskStatus.TERMINAL.contains(finalStatus)) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(),
                    "reportResult 仅接受终态: " + request.getStatus());
        }

        // P0: CAS by version + token (含租户条件)
        stateMachine.completeAs(id,
                request.getVersion(),
                request.getExecutionToken(),
                finalStatus,
                request.getResult(),
                request.getErrorMsg(),
                request.getLastErrorCode(),
                request.getLastErrorMessage(),
                tenantId);

        log.info("任务终态已提交: id={}, status={}", id, finalStatus);

        // Webhook 回调（仅 SUCCESS/FAILED/CANCELLED 终态触发）
        if (task.getCallbackUrl() != null && !task.getCallbackUrl().isEmpty()) {
            AstTask updated = taskMapper.selectByIdWithTypeName(id, tenantId);
            if (updated != null) {
                webhookService.enqueueFinished(updated);
            }
        }
    }

    private void updateWorkerHeartbeatOnPoll(String workerIp, String taskTypeKey, String workerId, Long tenantId) {
        try {
            AstWorkerNode worker = new AstWorkerNode();
            worker.setId(snowflakeDistributor.nextId());
            worker.setTenantId(tenantId);
            worker.setWorkerId(workerId != null ? workerId
                    : "worker-" + workerIp.replace(".", "-") + "-" + taskTypeKey);
            worker.setTaskTypeKey(taskTypeKey);
            worker.setWorkerIp(workerIp);
            worker.setWorkerPort(8080);
            worker.setHostname(workerIp);
            worker.setSupportedTaskTypes(taskTypeKey);
            worker.setMaxTaskCount(10);
            worker.setCurrentTaskCount(0);
            worker.setStatus("ONLINE");

            int result = workerNodeMapper.upsertWorkerHeartbeat(worker);
            if (result > 0) {
                log.debug("Worker 心跳已更新: ip={}, taskType={}, workerId={}",
                        workerIp, taskTypeKey, workerId);
            } else {
                log.warn("Worker 心跳更新失败: ip={}, taskType={}", workerIp, taskTypeKey);
            }
        } catch (Exception e) {
            log.error("更新 Worker 心跳异常: ip={}, taskType={}", workerIp, taskTypeKey, e);
        }
    }

    private TaskDetailResponse toResponse(AstTask task) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(task.getId());
        response.setType(task.getTaskTypeKey());
        response.setStatus(task.getStatus());
        response.setProgress(task.getProgress());
        response.setCurrentStep(task.getCurrentStepKey());
        response.setStepsDetail(task.getStepsDetail());
        response.setPayload(task.getPayload());
        response.setResult(task.getResult());
        response.setErrorMsg(task.getErrorMsg());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        response.setAttempt(task.getAttempt());
        response.setMaxAttempts(task.getMaxAttempts());
        response.setVersion(task.getVersion());
        response.setExecutionId(task.getExecutionId());
        response.setExecutionToken(task.getExecutionToken());
        response.setWorkerId(task.getWorkerId());
        response.setLeaseExpireAt(task.getLeaseExpireAt());
        return response;
    }
}
