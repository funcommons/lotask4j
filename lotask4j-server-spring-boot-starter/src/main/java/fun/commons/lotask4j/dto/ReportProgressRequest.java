package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Worker 上报进度请求 DTO
 *
 * P0 增强：要求 Worker 回传 polling 时获得的 executionToken 与 version，
 * 否则无法校验 fencing 与乐观锁。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
public class ReportProgressRequest {

    /**
     * 当前步骤 Key
     */
    @NotBlank(message = "当前步骤 Key 不能为空")
    private String currentStepKey;

    /**
     * 当前步骤内的进度 (0-100)
     */
    @Min(value = 0, message = "步骤进度不能小于 0")
    @Max(value = 100, message = "步骤进度不能大于 100")
    private Integer stepProgress;

    /**
     * Fencing token — 由 PollTaskResponse 返回。Worker 必须原值回传。
     */
    @NotNull(message = "executionToken 不能为空")
    private Long executionToken;

    /**
     * 乐观锁版本号 — 由 PollTaskResponse 返回。Worker 必须原值回传。
     */
    @NotNull(message = "version 不能为空")
    private Integer version;
}
