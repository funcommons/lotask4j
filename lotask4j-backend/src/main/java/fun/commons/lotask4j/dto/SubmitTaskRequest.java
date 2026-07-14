package fun.commons.lotask4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 提交任务请求 DTO
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "提交异步任务请求")
public class SubmitTaskRequest {

    /**
     * 任务类型 Key
     * 示例: video_transcode, data_export
     */
    @NotBlank(message = "任务类型不能为空")
    @Size(min = 1, max = 64, message = "任务类型长度应在 1-64 之间")
    @Schema(description = "任务类型标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "data_export")
    private String type;

    /**
     * 任务入参 (JSON)
     * 根据不同的任务类型自定义结构
     */
    @NotNull(message = "任务入参不能为空")
    @Schema(description = "任务入参 (JSON格式)", requiredMode = Schema.RequiredMode.REQUIRED, example = "{\"url\": \"http://oss/v.mp4\"}")
    private Map<String, Object> payload;

    /**
     * 任务优先级 (0-100)
     * 默认值: 0
     */
    @Min(value = 0, message = "优先级最小值为 0")
    @Max(value = 100, message = "优先级最大值为 100")
    @Schema(description = "任务优先级 (0-100)", example = "10")
    private Integer priority = 0;

    /**
     * Webhook 回调地址 (可选)
     * 任务完成后将向此 URL 发送 POST 请求
     */
    @Nullable
    @Pattern(regexp = "^https?://.*", message = "回调地址必须是有效的 HTTP/HTTPS URL")
    @Schema(description = "Webhook 回调地址", example = "https://example.com/webhook")
    private String callbackUrl;
}
