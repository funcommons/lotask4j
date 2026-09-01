package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.enums.TaskEventType;
import fun.commons.lotask4j.enums.TaskStatus;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.metrics.TaskMetrics;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.web.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务状态机 — P0 实施的中心化服务。
 *
 * P1-1 增强：在每个状态迁移埋点 Micrometer 指标。
 * 所有指标由 {@link TaskMetrics} 聚合。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStateMachine {

    private final AstTaskMapper taskMapper;
    private final SnowflakeDistributor snowflakeDistributor;
    private final TaskMetrics metrics;
    private final TaskEventRecorder eventRecorder;

    /** 默认租约秒数（可通过 application.yml 的 app.asts.default-lease-seconds 覆盖） */
    @Value("${app.asts.default-lease-seconds:120}")
    private int defaultLeaseSeconds;

    @PostConstruct
    public void init() {
        // 注册 Gauge（启动时一次, 不会重复）
        metrics.registerActiveWorkersGauge();
    }

    // =====================================================================
    // 入队
    // =====================================================================

    /**
     * 提交新任务（直接 INSERT + 初始 version=0）。
     * 注意：本方法本身只生成 ID 与默认值, 不负责插入 — 由 submitTask 调用方负责 INSERT。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long createNewTask(AstTask task) {
        long newId = snowflakeDistributor.nextId();
        task.setId(newId);
        task.setStatus(TaskStatus.PENDING.wireValue());
        task.setAttempt(1);
        if (task.getMaxAttempts() == null || task.getMaxAttempts() < 1) {
            task.setMaxAttempts(1);
        }
        task.setVersion(0);
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(task.getCreatedAt());
        if (task.getIsDeleted() == null) {
            task.setIsDeleted(0);
        }
        // P1-1: 提交计数 (TaskServiceImpl.submitTask 调用本方法后会再 INSERT, P1-B 事件再记录)
        metrics.submitted(task.getTaskTypeKey()).increment();
        return newId;
    }

    /**
     * 按幂等键查已存在任务（P0-5）。
     * 命中 → 返回任务；未命中 → 返回 null。
     */
    public AstTask findByIdempotencyKey(String taskTypeKey, String idempotencyKey, Long tenantId) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return null;
        }
        return taskMapper.findByIdempotencyKey(taskTypeKey, idempotencyKey, tenantId);
    }

    // =====================================================================
    // 派发 — Worker 拉起任务
    // =====================================================================

    /**
     * 派发任务：PENDING/CANCELLING → RUNNING，分配 execution_id + execution_token + lease。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long dispatch(Long taskId, Integer expectedVersion, String workerId, Long tenantId) {
        // 先读历史任务以便计量 queue_delay
        AstTask before = taskMapper.selectById(taskId);

        long newExecutionId = snowflakeDistributor.nextId();
        long newToken = (System.nanoTime() & 0x7FFFFFFFFFFFFFFFL) ^ newExecutionId;

        OffsetDateTime now = OffsetDateTime.now();

        int rows = taskMapper.dispatchTask(
                taskId, expectedVersion, workerId, newExecutionId, newToken,
                defaultLeaseSeconds, now, tenantId);

        if (rows == 0) {
            log.warn("CAS 失败 dispatch: id={}, expectedVersion={}, worker={}",
                    taskId, expectedVersion, workerId);
            throw casFailure("派发失败：任务状态已变更 (id=" + taskId + ", expected=" + expectedVersion + ")");
        }

        // P1-1: 队列延迟（创建 → 派发）
        if (before != null && before.getCreatedAt() != null) {
            metrics.recordQueueDelay(before.getTaskTypeKey(),
                    Duration.between(before.getCreatedAt(), now));
        }
        metrics.workerAcquired();

        // P1-3: 写一条 TASK_DISPATCHED 事件
        String oldStatus = before == null ? null : before.getStatus();
        eventRecorder.record(taskId, TaskEventType.TASK_DISPATCHED,
                before == null ? null : before.getAttempt(),
                oldStatus, "RUNNING", workerId, java.util.Map.of(
                        "execution_id", newExecutionId,
                        "execution_token", newToken,
                        "lease_seconds", defaultLeaseSeconds));

        log.info("派发任务: id={}, worker={}, executionId={}, token={}, lease={}s",
                taskId, workerId, newExecutionId, newToken, defaultLeaseSeconds);
        return newToken;
    }

    /**
     * Dispatch + start (保留语义)。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Long dispatchAndStart(Long taskId, Integer expectedVersion, String workerId, Long tenantId) {
        return dispatch(taskId, expectedVersion, workerId, tenantId);
    }

    // =====================================================================
    // 续约 — Worker 心跳
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRED)
    public void extendLease(Long taskId, Integer expectedVersion, Long executionToken, Long tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        int rows = taskMapper.extendLease(taskId, expectedVersion, executionToken,
                defaultLeaseSeconds, now, tenantId);
        if (rows == 0) {
            throw casFailure("续约失败：lease 已过期或被抢占 (id=" + taskId + ")");
        }
    }

    // =====================================================================
    // 进度上报
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRED)
    public void reportProgress(Long taskId,
                                Integer expectedVersion,
                                Long executionToken,
                                String currentStepKey,
                                Integer stepProgress,
                                List<Map<String, Object>> stepsDetail,
                                Integer globalProgress,
                                Long tenantId) {
        String stepsDetailJson = stepsDetail == null
                ? null
                : com.alibaba.fastjson2.JSON.toJSONString(stepsDetail);

        OffsetDateTime now = OffsetDateTime.now();

        int rows = taskMapper.progressWithVersion(taskId, expectedVersion, executionToken,
                currentStepKey, stepProgress, stepsDetailJson, globalProgress, now, tenantId);

        if (rows == 0) {
            throw casFailure("进度上报失败：状态已被改或 fencing 不匹配 (id=" + taskId + ")");
        }
    }

    // =====================================================================
    // 取消 - 用户侧和 Worker 侧分开
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRED)
    public void requestCancel(Long taskId, Integer expectedVersion, Long tenantId) {
        AstTask before = taskMapper.selectById(taskId);
        OffsetDateTime now = OffsetDateTime.now();
        int rows = taskMapper.markCancelRequested(taskId, expectedVersion, now, now, tenantId);
        if (rows == 0) {
            throw casFailure("取消失败：任务已不可取消或已被改 (id=" + taskId + ")");
        }
        if (before != null) {
            metrics.canceled(before.getTaskTypeKey()).increment();
        }
        // P1-3 事件
        String oldStatus = before == null ? null : before.getStatus();
        Integer attempt = before == null ? null : before.getAttempt();
        eventRecorder.record(taskId, TaskEventType.CANCEL_REQUESTED, attempt,
                oldStatus, "CANCELLING", null, null);
        log.info("任务取消请求已记录: id={}", taskId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void confirmCancellation(Long taskId,
                                     Integer expectedVersion,
                                     Long executionToken,
                                     Long tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        int rows = taskMapper.confirmCancel(taskId, expectedVersion, executionToken, now, tenantId);
        if (rows == 0) {
            throw casFailure("确认取消失败：fencing 不匹配 (id=" + taskId + ")");
        }
        // 释放 Worker gauge
        metrics.workerReleased();
        // P1-3 事件
        AstTask before = taskMapper.selectById(taskId);
        String workerId = before == null ? null : before.getWorkerId();
        Integer attempt = before == null ? null : before.getAttempt();
        eventRecorder.record(taskId, TaskEventType.TASK_CANCELLED, attempt,
                "CANCELLING", "CANCELLED", workerId, null);
        log.info("任务取消已完成: id={}", taskId);
    }

    // =====================================================================
    // 终态提交 — Worker 主动上报成功/失败
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRED)
    public void completeAs(Long taskId,
                            Integer expectedVersion,
                            Long executionToken,
                            TaskStatus finalStatus,
                            Map<String, Object> result,
                            String errorMsg,
                            String lastErrorCode,
                            String lastErrorMessage,
                            Long tenantId) {
        if (finalStatus != TaskStatus.SUCCESS
                && finalStatus != TaskStatus.FAILED
                && finalStatus != TaskStatus.CANCELLED) {
            throw new IllegalArgumentException("completeAs 仅接受终态: " + finalStatus);
        }

        // 先读用于指标计算
        AstTask before = taskMapper.selectById(taskId);

        String resultJson = result == null ? null : com.alibaba.fastjson2.JSON.toJSONString(result);
        OffsetDateTime now = OffsetDateTime.now();

        int rows = taskMapper.completeWithToken(taskId, expectedVersion, executionToken,
                finalStatus.wireValue(), resultJson, errorMsg, lastErrorCode, lastErrorMessage, now, tenantId);

        if (rows == 0) {
            throw casFailure("提交结果失败：fencing 不匹配或状态已变更 (id=" + taskId + ")");
        }

        // P1-3 事件 — 把终态映射到对应事件类型
        TaskEventType eventType = switch (finalStatus) {
            case SUCCESS -> TaskEventType.TASK_SUCCEEDED;
            case FAILED -> TaskEventType.TASK_FAILED;
            case CANCELLED -> TaskEventType.TASK_CANCELLED;
            default -> TaskEventType.TASK_FAILED;
        };
        String workerId = before == null ? null : before.getWorkerId();
        Integer attempt = before == null ? null : before.getAttempt();
        String oldStatus = before == null ? null : before.getStatus();
        eventRecorder.record(taskId, eventType, attempt,
                oldStatus, finalStatus.wireValue(), workerId,
                java.util.Map.of("last_error_code",
                        lastErrorCode == null ? "" : lastErrorCode));

        // P1-1 指标埋点
        if (before != null) {
            String taskType = before.getTaskTypeKey();
            OffsetDateTime startedAt = before.getStartedAt();
            OffsetDateTime createdAt = before.getCreatedAt();

            if (finalStatus == TaskStatus.SUCCESS) {
                metrics.succeeded(taskType).increment();
            } else if (finalStatus == TaskStatus.FAILED) {
                metrics.failed(taskType, lastErrorCode == null ? "UNKNOWN" : lastErrorCode).increment();
            } else if (finalStatus == TaskStatus.CANCELLED) {
                metrics.canceled(taskType).increment();
            }

            if (startedAt != null) {
                metrics.recordExec(taskType, Duration.between(startedAt, now));
            }
            if (createdAt != null) {
                metrics.recordE2E(taskType, Duration.between(createdAt, now));
            }
        }
        metrics.workerReleased();

        log.info("任务终态提交: id={}, status={}", taskId, finalStatus);
    }

    // =====================================================================
    // Reaper 路径（专用）
    // =====================================================================

    @Transactional(propagation = Propagation.REQUIRED)
    public int recoverExpiredLeases(OffsetDateTime leaseCutoff) {
        int rows = taskMapper.resetExpiredLeases(leaseCutoff, OffsetDateTime.now());
        if (rows > 0) {
            // Reaper 不能精确知道 task type, 只记 total retry 计数 (type 标签为 unknown)
            metrics.retry("unknown").increment(rows);
            // worker gauge 释放一部分
            for (int i = 0; i < rows; i++) {
                metrics.workerReleased();
            }
        }
        return rows;
    }

    // =====================================================================
    // helper
    // =====================================================================

    private static ApiException casFailure(String msg) {
        return new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(), msg);
    }
}
