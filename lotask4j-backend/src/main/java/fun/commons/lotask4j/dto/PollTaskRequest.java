package fun.commons.lotask4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 抢占任务请求 DTO
 *
 * P0 增强：要求 Worker 上报 workerId（用于派发记录与审计）。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Worker 抢占任务请求")
public class PollTaskRequest {

    @NotBlank(message = "任务类型不能为空")
    @Size(max = 64)
    @Schema(description = "任务类型标识", example = "data_export",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskType;

    @Schema(description = "调度策略: PRIORITY (优先级优先) / FIFO (先入先出)",
            example = "PRIORITY", allowableValues = {"PRIORITY", "FIFO"})
    private String strategy;

    /**
     * Worker 实例 ID（P0）。由客户端（Worker）自行生成并保持稳定，用于派发记录与 fencing。
     * 推荐格式：{@code wkr-<ip>-<uuid-prefix>} 或 Worker 自定义标签。
     */
    @NotBlank(message = "workerId 不能为空")
    @Size(max = 64, message = "workerId 长度不应超过 64 字符")
    @Schema(description = "Worker 实例 ID — 用于 fencing token 与审计",
            example = "wkr-10-0-0-1-u1234567",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String workerId;
}
