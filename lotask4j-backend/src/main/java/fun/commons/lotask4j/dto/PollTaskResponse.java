package fun.commons.lotask4j.dto;

import fun.commons.framework4j.openid.annotation.OpenId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Worker 抢占任务响应 DTO
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
}
