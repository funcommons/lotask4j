package fun.commons.lotask4j.service.impl;

import com.alibaba.fastjson2.JSON;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.entity.AstWorkerNode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.mapper.AstWorkerNodeMapper;
import fun.commons.lotask4j.service.WebhookService;
import fun.commons.lotask4j.service.WorkerService;
import fun.commons.framework4j.web.ApiException;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker 服务实现
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

    @Override
    @Transactional
    public PollTaskResponse pollTask(PollTaskRequest request, String workerIp) {
        String taskType = request.getTaskType();
        String strategy = request.getStrategy() != null ? request.getStrategy() : "PRIORITY";

        log.debug("Worker polling task: type={}, strategy={}, ip={}", taskType, strategy, workerIp);

        // 检查任���类型是否存在且已启用
        AstTaskTypeConfig typeConfig = taskTypeConfigMapper.selectByTypeKey(taskType);
        if (typeConfig == null) {
            throw new ApiException(BusinessCode.TASK_TYPE_UNKNOWN.getCode(), "未知的任务类型: " + taskType);
        }
        if (typeConfig.getIsEnabled() == null || typeConfig.getIsEnabled() != 1) {
            throw new ApiException(BusinessCode.TASK_TYPE_DISABLED.getCode(), "该任务类型已被禁用: " + taskType);
        }

        // 更新 Worker 心跳 (poll 即心跳)
        updateWorkerHeartbeatOnPoll(workerIp, taskType);

        // 尝试抢占任务
        AstTask task = taskMapper.pollAndLockTask(taskType, strategy, workerIp);

        if (task == null) {
            log.debug("No available tasks for type: {}", taskType);
            return null;
        }

        log.info("Worker {} acquired task: id={}", workerIp, task.getId());

        // 构造响应
        PollTaskResponse response = new PollTaskResponse();
        response.setId(task.getId());  // taskId → id
        response.setType(task.getTaskTypeKey());
        response.setPayload(task.getPayload());
        response.setPriority(task.getPriority());

        return response;
    }

    @Override
    public TaskDetailResponse getTaskStatus(Long id) {
        log.debug("Worker querying task status: {}", id);

        AstTask task = taskMapper.selectById(id);  // selectByTaskUuid → selectById
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务不存在: " + id);
        }

        // 构造响应
        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(task.getId());  // taskId → id
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

        return response;
    }

    @Override
    @Transactional
    public void reportProgress(Long id, ReportProgressRequest request) {
        log.debug("Worker reporting progress: id={}, step={}, progress={}",
                id, request.getCurrentStepKey(), request.getStepProgress());

        // 查询任务
        AstTask task = taskMapper.selectById(id);  // selectByTaskUuid → selectById
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务不存在: " + id);
        }

        // 检查任务状态
        if (!"RUNNING".equals(task.getStatus())) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(), "任务状态已变更,当前状态: " + task.getStatus());
        }

        // 更新步骤详情
        List<Map<String, Object>> stepsDetail = task.getStepsDetail();
        if (stepsDetail == null) {
            stepsDetail = new java.util.ArrayList<>();
            task.setStepsDetail(stepsDetail);
        }

        // 查找并更新当前步骤
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

        // 如果步骤不存在,添加新步骤
        if (!stepFound) {
            Map<String, Object> newStep = new HashMap<>();
            newStep.put("key", request.getCurrentStepKey());
            newStep.put("status", "processing");
            newStep.put("progress", request.getStepProgress());
            newStep.put("start_time", OffsetDateTime.now().toString());
            stepsDetail.add(newStep);
        }

        // 计算全局进度
        int globalProgress = calculateGlobalProgress(task.getTaskTypeKey(),
                request.getCurrentStepKey(), request.getStepProgress());

        // 更新任务进度
        String stepsDetailJson = JSON.toJSONString(stepsDetail);
        int result = taskMapper.updateTaskProgress(id, request.getCurrentStepKey(),
                request.getStepProgress(), stepsDetailJson, globalProgress);

        if (result == 0) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(), "任务进度更新失败,可能任务状态已变更");
        }

        log.info("Task progress updated: id={}, step={}, globalProgress={}%",
                id, request.getCurrentStepKey(), globalProgress);
    }

    /**
     * 计算全局进度 (基于步骤权重)
     *
     * @param taskTypeKey 任务类型
     * @param currentStepKey 当前步骤键
     * @param stepProgress 当前步骤进度 (0-100)
     * @return 全局进度 (0-100)
     */
    private int calculateGlobalProgress(String taskTypeKey, String currentStepKey, int stepProgress) {
        // 获取任务类型配置
        AstTaskTypeConfig taskTypeConfig = taskTypeConfigMapper.selectByTypeKey(taskTypeKey);
        if (taskTypeConfig == null || taskTypeConfig.getStepsConfig() == null) {
            // 如果没有步骤定义,返回步骤进度作为全局进度
            return stepProgress;
        }

        List<Map<String, Object>> stepsDefinition = taskTypeConfig.getStepsConfig();
        if (stepsDefinition.isEmpty()) {
            return stepProgress;
        }

        // 计算总权重和当前步骤之前的权重
        int totalWeight = 0;
        int completedWeight = 0;
        int currentStepWeight = 0;
        boolean foundCurrentStep = false;

        for (Map<String, Object> step : stepsDefinition) {
            String stepKey = (String) step.get("key");
            Integer weight = step.get("weight") != null ?
                    ((Number) step.get("weight")).intValue() : 0;

            totalWeight += weight;

            if (stepKey.equals(currentStepKey)) {
                currentStepWeight = weight;
                foundCurrentStep = true;
            } else if (!foundCurrentStep) {
                // 当前步骤之前的所有步骤权重累加
                completedWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return stepProgress;
        }

        // 全局进度 = (已完成步骤权重 + 当前步骤权重 * 当前步骤进度 / 100) / 总权重 * 100
        double progress = (completedWeight + currentStepWeight * stepProgress / 100.0) * 100.0 / totalWeight;
        return Math.max(0, Math.min(100, (int) Math.round(progress)));
    }

    @Override
    @Transactional
    public void reportResult(Long id, ReportResultRequest request) {
        log.debug("Worker reporting result: id={}, status={}", id, request.getStatus());

        // 查询任务
        AstTask task = taskMapper.selectById(id);  // selectByTaskUuid → selectById
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务不存在: " + id);
        }

        // 检查任务状态
        if (!"RUNNING".equals(task.getStatus()) && !"CANCELLING".equals(task.getStatus())) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(), "任务状态不允许上报结果,当前状态: " + task.getStatus());
        }

        // 转换 result 为 JSON 字符串
        String resultJson = null;
        if (request.getResult() != null) {
            resultJson = JSON.toJSONString(request.getResult());
        }

        // 更新任务最终结果
        int result = taskMapper.updateTaskResult(id, request.getStatus(),
                resultJson, request.getErrorMsg());

        if (result == 0) {
            throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(), "任务结果更新失败,可能任务状态已变更");
        }

        log.info("Task result reported: id={}, status={}", id, request.getStatus());

        // 触发 Webhook 回调(如果配置了 callback_url)
        if (task.getCallbackUrl() != null && !task.getCallbackUrl().isEmpty()) {
            // 重新查询任务以获取最新状态
            AstTask updatedTask = taskMapper.selectById(id);  // selectByTaskUuid → selectById
            if (updatedTask != null) {
                webhookService.sendWebhookAsync(updatedTask);
            }
        }
    }

    /**
     * 在 Worker poll 时更新心跳记录
     * Poll 即心跳：Worker 每次 poll 任务时,自动 UPSERT 心跳记录
     *
     * @param workerIp Worker IP 地址
     * @param taskTypeKey 任务类型标识
     */
    private void updateWorkerHeartbeatOnPoll(String workerIp, String taskTypeKey) {
        try {
            // 构造 Worker 节点实体
            AstWorkerNode worker = new AstWorkerNode();
            worker.setId(snowflakeDistributor.nextId());
            worker.setWorkerId("worker-" + workerIp.replace(".", "-") + "-" + taskTypeKey);
            worker.setTaskTypeKey(taskTypeKey);
            worker.setWorkerIp(workerIp);
            worker.setWorkerPort(8080); // 默认端口
            worker.setHostname(workerIp); // 默认使用 IP 作为 hostname
            worker.setSupportedTaskTypes(taskTypeKey);
            worker.setMaxTaskCount(10); // 默认最大并发数
            worker.setCurrentTaskCount(0);
            worker.setStatus("ONLINE");

            // UPSERT 心跳记录 (ON CONFLICT DO UPDATE)
            int result = workerNodeMapper.upsertWorkerHeartbeat(worker);

            if (result > 0) {
                log.debug("Worker heartbeat updated: ip={}, taskType={}", workerIp, taskTypeKey);
            } else {
                log.warn("Failed to update worker heartbeat: ip={}, taskType={}", workerIp, taskTypeKey);
            }
        } catch (Exception e) {
            // 心跳更新失败不应影响 poll 操作,仅记录日志
            log.error("Error updating worker heartbeat: ip={}, taskType={}", workerIp, taskTypeKey, e);
        }
    }
}
