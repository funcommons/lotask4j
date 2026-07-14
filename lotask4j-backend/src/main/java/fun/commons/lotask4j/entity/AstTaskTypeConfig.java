package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务类型配置实体
 * 定义系统支持的任务类型及其对应的并发限制、超时时间等元数据
 */
@Getter
@Setter
@TableName(value = "asts_task_type_config", autoResultMap = true)
public class AstTaskTypeConfig {

    /**
     * 代理主键 ID (自增)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务类型标识(业务主键)，如 video_transcode
     */
    private String typeKey;

    /**
     * 任务类型名称
     */
    @TableField("type_name")
    private String name;

    /**
     * 全局并发限制数
     */
    @TableField("max_concurrency")
    private Integer concurrencyLimit;

    /**
     * 任务超时时间(秒)
     */
    @TableField("exec_timeout_sec")
    private Integer timeoutSeconds;

    /**
     * 最大重试次数
     */
    @TableField("max_retry_count")
    private Integer maxRetries;

    /**
     * 开关状态 (0: 禁用, 1: 启用)
     */
    private Integer isEnabled;

    /**
     * 步骤配置 JSONB
     * 格式: [{"key":"init", "name":"初始化", "weight":10}, {"key":"process", "name":"处理", "weight":90}]
     */
    @TableField(value = "steps_definition", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> stepsConfig;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

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
