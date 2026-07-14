package fun.commons.lotask4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务类型配置响应 DTO
 */
@Getter
@Setter
@Schema(description = "任务类型配置信息")
public class TaskTypeConfigResponse {

    @Schema(description = "配置 ID")
    private Long id;

    @Schema(description = "任务类型标识", example = "data_export")
    private String typeKey;

    @Schema(description = "任务类型名称", example = "数据导出任务")
    private String name;

    @Schema(description = "并发限制", example = "10")
    private Integer concurrencyLimit;

    @Schema(description = "超时时间(秒)", example = "600")
    private Integer timeoutSeconds;

    @Schema(description = "最大重试次数", example = "3")
    private Integer maxRetries;

    @Schema(description = "是否启用", example = "true")
    private Boolean isEnabled;

    @Schema(description = "步骤配置 (JSON)")
    private List<Map<String, Object>> stepsConfig;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}
