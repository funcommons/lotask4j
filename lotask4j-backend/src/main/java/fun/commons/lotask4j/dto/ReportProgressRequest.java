package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Worker 上报进度请求 DTO
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
}
