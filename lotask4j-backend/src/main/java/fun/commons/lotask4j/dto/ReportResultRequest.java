package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Worker 上报结果请求 DTO
 */
@Getter
@Setter
public class ReportResultRequest {

    /**
     * 任务最终状态: SUCCESS, FAILED, CANCELLED
     */
    @NotBlank(message = "任务状态不能为空")
    @Pattern(regexp = "^(SUCCESS|FAILED|CANCELLED)$", message = "状态必须是 SUCCESS、FAILED 或 CANCELLED")
    private String status;

    /**
     * 执行结果 JSON (状态为 SUCCESS 时必填)
     */
    private Map<String, Object> result;

    /**
     * 错误信息 (状态为 FAILED 时必填)
     */
    private String errorMsg;
}
