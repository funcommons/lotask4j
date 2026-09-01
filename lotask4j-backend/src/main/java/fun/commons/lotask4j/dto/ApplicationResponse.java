package fun.commons.lotask4j.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 接入应用响应 (列表/详情)
 *
 * 不含 appSecret — secret 仅在创建 / reset-secret 时明文返回一次。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class ApplicationResponse {

    private Long id;

    private String name;

    private String description;

    /** ACTIVE / INACTIVE */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
