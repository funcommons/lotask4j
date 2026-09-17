package fun.commons.lotask4j.dto;

import fun.commons.framework4j.openid.annotation.OpenId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Worker 抢占任务响应 DTO
 *
 * P0 增强：携带 fencing token (executionToken) 与 expectedVersion，
 * Worker 在上报进度/结果时必须回传这两个字段。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@Schema(description = "Worker 抢占任务响应")
public class PollTaskResponse {

    /**
     * 任务 ID (对外使用 OpenID 混淆)
     */
    @OpenId
    @Schema(description = "任务唯一标识", example = "YeirYkxHuQ")
    private Long id;

    /**
     * 任务类型
     */
    private String type;

    /**
     * 任务入参 JSON
     */
    private Map<String, Object> payload;

    /**
     * 任务优先级
     */
    private Integer priority;

    /**
     * 当前执行的 fencing token。Worker 上报进度/结果时必须回传。
     * Worker 切换或重新派发后, token 会变, 老 token 上报会被丢。
     */
    @Schema(description = "fencing token — 上报进度/结果时必须回传")
    private Long executionToken;

    /**
     * 乐观锁版本号。Worker 上报进度/结果时必须回传。
     */
    @Schema(description = "乐观锁版本号 — 上报进度/结果时必须回传")
    private Integer version;

    /**
     * 当前 attempt（重试轮次）。
     */
    @Schema(description = "当前 attempt (重试轮次)")
    private Integer attempt;

    /**
     * Lease 到期时间。Worker 必须在此之前续约或完成上报。
     */
    @Schema(description = "Lease 到期时间 — Worker 必须在此之前续约或完成上报")
    private java.time.OffsetDateTime leaseExpireAt;
}
