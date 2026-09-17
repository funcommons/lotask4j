package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Worker 上报结果请求 DTO
 *
 * P0 增强：要求 Worker 回传 polling 时获得的 executionToken 与 version。
 *
 * @author lotask4j-team
 * @version 1.0.0
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

    /**
     * 上次错误码 (用于 BusinessCode 审计)
     */
    private String lastErrorCode;

    /**
     * 上次错误描述
     */
    private String lastErrorMessage;

    /**
     * Fencing token — 由 PollTaskResponse 返回。
     */
    @NotNull(message = "executionToken 不能为空")
    private Long executionToken;

    /**
     * 乐观锁版本号 — 由 PollTaskResponse 返回。
     */
    @NotNull(message = "version 不能为空")
    private Integer version;
}
