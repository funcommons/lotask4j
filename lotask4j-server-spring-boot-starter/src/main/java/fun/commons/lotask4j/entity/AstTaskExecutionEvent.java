package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 任务执行事件 — P1-3 审计轨迹 (append-only)。
 *
 * 每次任务状态/字段发生重要变化时写入一条。
 * 终态任务的事件历史可被前端展示 + 故障排查。
 *
 * 事件类型 (event_type)：
 *   - TASK_CREATED       任务创建
 *   - TASK_DISPATCHED    派发给 Worker
 *   - TASK_STARTED       Worker 实际开始执行
 *   - PROGRESS_UPDATED   进度更新
 *   - RETRY_SCHEDULED    重新进入 PENDING (由 Reaper)
 *   - CANCEL_REQUESTED   用户请求取消
 *   - TASK_CANCELLED     取消完成
 *   - TASK_SUCCEEDED     业务成功
 *   - TASK_FAILED        业务失败
 *   - TASK_TIMED_OUT     超时 (max_attempts 耗尽)
 *   - LEASE_EXPIRED      lease 过期回退 (单次回收)
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@TableName(value = "asts_task_execution_event", autoResultMap = true)
public class AstTaskExecutionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;
    /** 租户归属 (租户级隔离; 只从 token claim 取, body 同名字段忽略) */
    @TableField("tenant_id")
    private Long tenantId;


    /** 同 task.execution_id */
    private Long executionId;

    /** attempt (事件发生的轮次) */
    private Integer attempt;

    /**
     * 事件类型 — 参见类注释
     */
    private String eventType;

    private String oldStatus;
    private String newStatus;

    /** 上报事件的 Worker/操作者 */
    private String workerId;

    /** W3C Trace ID，便于跨服务链路追踪 */
    private String traceId;

    /** 操作者 (user-123, system, scheduler 等) */
    private String operator;

    /** 详情 (JSONB) — 任意额外上下文 */
    @TableField(value = "detail", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> detail;

    private OffsetDateTime createdAt;
}
