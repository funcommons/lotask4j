package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.AstTaskExecutionEvent;
import fun.commons.lotask4j.enums.TaskEventType;
import fun.commons.lotask4j.mapper.AstTaskExecutionEventMapper;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务事件记录器 (P1-3) — append-only 审计。
 *
 * 责任：
 * <ol>
 *   <li>从 {@code task_state_machine} 的每次状态迁移接收一个事件并 INSERT。</li>
 *   <li>自动加上 trace_id (从 Micrometer Tracer 当前 Span 取)。</li>
 *   <li>写错误时仅记日志（不阻塞主流程）—— 事件不应反过来破坏状态机一致性。</li>
 * </ol>
 *
 * 该服务由 {@link TaskStateMachine} 调用；不应对外暴露写入接口。
 * 查询端点请直接使用 {@link AstTaskExecutionEventMapper#selectByTaskIdLimit}。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskEventRecorder {

    private final AstTaskExecutionEventMapper eventMapper;

    /** Optional — Tracing 没启用时为 null */
    @Autowired(required = false)
    private Tracer tracer;

    /**
     * 记录事件 (P1-3 入口)。
     *
     * @param taskId    任务 ID
     * @param type      事件类型
     * @param attempt   当前 attempt（可空）
     * @param oldStatus 旧状态（可空）
     * @param newStatus 新状态（可空）
     * @param workerId  Worker ID 或 operator（可空）
     * @param detail    详情 JSON Map（可空）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long taskId,
                       TaskEventType type,
                       Integer attempt,
                       String oldStatus,
                       String newStatus,
                       String workerId,
                       Map<String, Object> detail) {
        try {
            AstTaskExecutionEvent event = new AstTaskExecutionEvent();
            event.setTaskId(taskId);
            // 租户归属随任务 (claim 缺失/后台路径为 null, 不阻断 append-only)
            event.setTenantId(fun.commons.framework4j.tenant.context.TenantIdentity.currentTenantId(null));
            event.setExecutionId(null);
            event.setAttempt(attempt);
            event.setEventType(type.name());
            event.setOldStatus(oldStatus);
            event.setNewStatus(newStatus);
            event.setWorkerId(workerId);
            event.setOperator(workerId);
            event.setDetail(detail);
            event.setTraceId(currentTraceId());
            event.setCreatedAt(OffsetDateTime.now());

            String detailJson = detail == null ? null
                    : com.alibaba.fastjson2.JSON.toJSONString(detail);
            eventMapper.insertDefault(event, detailJson);
        } catch (Exception e) {
            // append-only 故障不应破坏主流程, 只记日志
            log.warn("任务事件记录失败 (忽略): taskId={}, type={}, reason={}",
                    taskId, type, e.getMessage());
        }
    }

    /**
     * 记录事件 (重载)。
     */
    public void record(Long taskId, TaskEventType type, String oldStatus, String newStatus) {
        record(taskId, type, null, oldStatus, newStatus, null, null);
    }

    /**
     * 查询一个任务的事件历史 (按时间倒序)。
     */
    public List<AstTaskExecutionEvent> historyOf(Long taskId, int limit) {
        if (limit <= 0 || limit > 1000) {
            limit = 100;
        }
        return eventMapper.selectByTaskIdLimit(taskId, limit);
    }

    private String currentTraceId() {
        if (tracer == null) {
            return null;
        }
        try {
            var currentSpan = tracer.currentSpan();
            if (currentSpan == null) {
                return null;
            }
            return currentSpan.context().traceId();
        } catch (Exception e) {
            return null;
        }
    }
}
