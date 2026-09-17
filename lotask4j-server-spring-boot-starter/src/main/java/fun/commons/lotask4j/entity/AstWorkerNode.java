package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.*;
import fun.commons.lotask4j.config.PostgreSqlInetTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Worker 节点实体
 * 用于 Worker 节点的注册、心跳保活及在线状态监控
 */
@Getter
@Setter
@TableName("asts_worker_node")
public class AstWorkerNode {

    /**
     * 雪花算法 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Worker ID (唯一标识)
     */
    @TableField("worker_id")
    private String workerId;
    /** 租户归属 (租户级隔离; 只从 token claim 取, body 同名字段忽略) */
    @TableField("tenant_id")
    private Long tenantId;


    /**
     * 任务类型标识 (联合唯一键)
     * 与 asts_task_type_config.type_key 对应
     */
    @TableField("task_type_key")
    private String taskTypeKey;

    /**
     * 节点 IP 地址
     */
    @TableField(value = "worker_ip", typeHandler = PostgreSqlInetTypeHandler.class)
    private String workerIp;

    /**
     * Worker 端口
     */
    @TableField("worker_port")
    private Integer workerPort;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 支持的任务类型 (逗号分隔)
     */
    @TableField("supported_task_types")
    private String supportedTaskTypes;

    /**
     * 最大任务并发数
     */
    @TableField("max_task_count")
    private Integer maxTaskCount;

    /**
     * 当前任务数
     */
    @TableField("current_task_count")
    private Integer currentTaskCount;

    /**
     * 状态: ONLINE/OFFLINE/BUSY
     */
    private String status;

    /**
     * 最后心跳时间
     */
    @TableField("last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    /**
     * 累计完成任务数
     */
    @TableField("total_tasks_done")
    private Long totalTasksDone;

    /**
     * 累计失败任务数
     */
    @TableField("total_tasks_failed")
    private Long totalTasksFailed;

    /**
     * 注册时间
     */
    @TableField("registered_at")
    private OffsetDateTime registeredAt;

    /**
     * 更新时间(通过触发器自动维护)
     */
    private OffsetDateTime updatedAt;

    /**
     * 逻辑删除: 0=未删除, 1=已删除
     */
    @TableLogic
    private Integer isDeleted;
}
