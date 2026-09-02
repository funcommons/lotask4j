package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 任务类型配置请求 DTO
 */
@Getter
@Setter
public class TaskTypeConfigRequest {

    /**
     * 租户归属 (平台替租户建类型; 创建必填 — V5 起 tenant_id NOT NULL,
     * 校验在 AdminServiceImpl.saveTaskTypeConfig, update 缺省保留原归属)
     */
    private Long tenantId;

    /**
     * 任务类型标识
     */
    @NotBlank(message = "任务类型标识不能为空")
    private String typeKey;

    /**
     * 任务类型名称
     */
    @NotBlank(message = "任务类型名称不能为空")
    private String name;

    /**
     * 全局并发限制数
     */
    @NotNull(message = "并发限制不能为空")
    @Min(value = 1, message = "并发限制至少为 1")
    private Integer concurrencyLimit;

    /**
     * 任务超时时间(秒)
     */
    @NotNull(message = "超时时间不能为空")
    @Min(value = 1, message = "超时时间至少为 1 秒")
    private Integer timeoutSeconds;

    /**
     * 最大重试次数
     */
    @NotNull(message = "最大重试次数不能为空")
    @Min(value = 0, message = "最大重试次数不能为负数")
    private Integer maxRetries;

    /**
     * 是否启用
     */
    @NotNull(message = "启用状态不能为空")
    private Boolean isEnabled;

    /**
     * 步骤配置
     */
    private List<Map<String, Object>> stepsConfig;
}
