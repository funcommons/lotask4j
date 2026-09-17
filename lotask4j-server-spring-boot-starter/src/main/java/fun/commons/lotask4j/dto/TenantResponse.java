package fun.commons.lotask4j.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 租户响应 (列表/详情) — 不含 tenantSecret (仅创建 / reset-secret 明文返回一次)
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class TenantResponse {

    private Long id;

    private String name;

    private String description;

    private String email;

    /** 来源通道: OPS 运营创建 / SELF 自助注册 */
    private String channel;

    /** 生命周期: ACTIVE / SUSPEND (停用后不可换 token) */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
