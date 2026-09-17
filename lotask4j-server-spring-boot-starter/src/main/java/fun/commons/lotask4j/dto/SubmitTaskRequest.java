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
 * P0 增强：支持幂等键（idempotencyKey）、最大尝试次数（maxAttempts）
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
     * null = 未指定: client 提交由 service 兜底 0; admin 提交由 controller 兜底 100 (高优先级)
     * 注意: 不要写字段初始化器 (=0), 否则 admin 默认优先级分支永远不可达
     */
    @Min(value = 0, message = "优先级最小值为 0")
    @Max(value = 100, message = "优先级最大值为 100")
    @Schema(description = "任务优先级 (0-100)", example = "10")
    private Integer priority;

    /**
     * 幂等键（P0-5）。同 (type, idempotencyKey) 重复提交返首次任务 ID。
     * 客户端常用 UUID/GUID，幂等键有效期建议 ≤ 7 天。
     */
    @Size(max = 128, message = "幂等键长度不应超过 128 字符")
    @Schema(description = "幂等键 (相同任务类型下,同 key 重复提交会返回已存在任务 ID)",
            example = "ord-2024-01-01-abcd")
    private String idempotencyKey;

    /**
     * Webhook 回调地址 (可选)
     * 任务完成后将向此 URL 发送 POST 请求
     */
    @Nullable
    @Pattern(regexp = "^https?://.*", message = "回调地址必须是有效的 HTTP/HTTPS URL")
    @Schema(description = "Webhook 回调地址", example = "https://example.com/webhook")
    private String callbackUrl;
}
