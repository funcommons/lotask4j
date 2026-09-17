package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Webhook 投递 outbox (asts_outbox)
 *
 * 任务终态事务内入队 (与状态变更原子提交, 不丢事件);
 * {@code OutboxPublisher} 扫描投递, 指数退避, 超限进 FAILED 终态。
 * payload/callback_url 为任务完成时的快照 — 任务行后续归档/删除不影响投递。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@TableName("asts_outbox")
public class AstsOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final int MAX_ATTEMPTS = 8;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 聚合类型 (恒 TASK) */
    private String aggregateType;

    /** asts_task.id */
    private Long aggregateId;

    /** 事件类型 (恒 TASK_FINISHED) */
    private String eventType;

    /** 投递目标 URL (快照) */
    private String callbackUrl;

    /** 完整 webhook body JSON (快照) */
    private String payload;

    /** PENDING / SENT / FAILED */
    private String status;

    private Integer attemptCount;

    private Integer maxAttempts;

    /** 下次可投递时间 (退避) */
    private OffsetDateTime nextRetryAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime sentAt;
}
