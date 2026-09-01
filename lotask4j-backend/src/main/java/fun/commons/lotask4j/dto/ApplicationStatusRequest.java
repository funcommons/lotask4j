package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 应用状态变更请求
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class ApplicationStatusRequest {

    /** 目标状态 (ACTIVE / INACTIVE) */
    @NotBlank(message = "status 不能为空")
    private String status;
}
