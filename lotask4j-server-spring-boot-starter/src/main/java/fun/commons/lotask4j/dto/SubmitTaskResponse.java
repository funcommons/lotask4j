package fun.commons.lotask4j.dto;

import fun.commons.framework4j.openid.annotation.OpenId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 提交任务响应 DTO
 */
@Getter
@Setter
@Schema(description = "任务提交响应")
public class SubmitTaskResponse {

    /**
     * 任务 ID (对外使用 OpenID 混淆)
     */
    @OpenId
    @Schema(description = "任务唯一标识", example = "YeirYkxHuQ")
    private Long id;

    public SubmitTaskResponse() {
    }

    public SubmitTaskResponse(Long id) {
        this.id = id;
    }
}
