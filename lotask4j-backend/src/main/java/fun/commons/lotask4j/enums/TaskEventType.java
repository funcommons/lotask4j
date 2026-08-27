package fun.commons.lotask4j.enums;

/**
 * 任务事件类型 (P1-3)。
 *
 * 与 {@code AstTaskExecutionEvent.event_type} 字段值一一对应。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public enum TaskEventType {

    /** 任务创建 */
    TASK_CREATED,

    /** 任务被派发给 Worker (dispatch 通过) */
    TASK_DISPATCHED,

    /** Worker 报告开始执行 (与 dispatch 合并为一条, 兼容 v1) */
    TASK_STARTED,

    /** 进度更新 */
    PROGRESS_UPDATED,

    /** Reaper 把 lease 过期任务收回 PENDING 重试 */
    RETRY_SCHEDULED,

    /** 用户请求取消 */
    CANCEL_REQUESTED,

    /** Worker 确认取消完成 */
    TASK_CANCELLED,

    /** 业务成功 */
    TASK_SUCCEEDED,

    /** 业务失败 */
    TASK_FAILED,

    /** 超时 (max_attempts 耗尽) */
    TASK_TIMED_OUT,

    /** 单次 lease 过期回收 (Reaper) */
    LEASE_EXPIRED
}
