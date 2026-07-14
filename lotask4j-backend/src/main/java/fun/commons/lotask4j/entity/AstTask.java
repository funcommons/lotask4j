package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.lotask4j.config.PostgreSqlInetTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异步任务核心实体类
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@TableName(value = "asts_task", autoResultMap = true)
public class AstTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID (雪花算法 + OpenID 混淆)
     * 前端收到的是混淆后的字符串，如 "YeirYkxHuQ"
     */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /**
     * 任务类型 Key (如 video_transcode, data_export)
     */
    private String taskTypeKey;

    /**
     * 任务状态
     * 枚举值: PENDING, RUNNING, SUCCESS, FAILED, CANCELLING, CANCELLED
     */
    private String status = "PENDING";

    /**
     * 优先级 (0-100, 数字越大越优先)
     */
    private Integer priority = 0;

    /**
     * 重试次数
     */
    private Integer retryCount = 0;

    /**
     * 当前执行该任务的 Worker IP
     */
    @TableField(value = "worker_ip", typeHandler = PostgreSqlInetTypeHandler.class)
    private String workerIp;

    /**
     * Webhook 回调地址
     */
    private String callbackUrl;

    /**
     * 回调状态 (0: 无/未发送, 1: 发送成功, 2: 发送失败)
     */
    private Integer callbackStatus = 0;

    /**
     * 全局进度百分比 (0-100)
     */
    private Integer progress = 0;

    /**
     * 当前执行的步骤 Key
     */
    private String currentStepKey;

    /**
     * 当前步骤内的进度 (0-100)
     */
    private Integer currentStepProgress = 0;

    /**
     * 步骤详情快照 (JSONB 格式)
     * 格式示例:
     * [
     *   {
     *     "key": "init",
     *     "name": "初始化",
     *     "status": "finished",
     *     "detail": "资源加载完毕",
     *     "start_time": "2024-01-01T10:00:00Z",
     *     "end_time": "2024-01-01T10:00:05Z",
     *     "cost_ms": 5000
     *   }
     * ]
     */
    @TableField(value = "steps_detail", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> stepsDetail;

    /**
     * 任务入参 (JSONB 格式)
     */
    @TableField(value = "payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /**
     * 执行结果 (JSONB 格式)
     */
    @TableField(value = "result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> result;

    /**
     * 错误堆栈信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 最后更新时间 (也用作 Worker 心跳时间)
     */
    private OffsetDateTime updatedAt;

    /**
     * 任务开始执行时间
     */
    private OffsetDateTime startedAt;

    /**
     * 任务完成时间
     */
    private OffsetDateTime finishedAt;

    /**
     * 任务过期时间
     * 超过此时间未完成的任务可以被清理或标记为过期
     */
    private OffsetDateTime expiredAt;

    /**
     * 逻辑删除标志 (0: 未删除, 1: 已删除)
     */
    private Integer isDeleted = 0;

    /**
     * 任务类型名称（非数据库字段，用于JOIN查询）
     */
    @TableField(exist = false)
    private String typeName;

    /**
     * 任务超时时间配置（非数据库字段，从 task_type_config 表 JOIN 查询获取）
     */
    @TableField(exist = false)
    private Integer timeoutSeconds;
}
