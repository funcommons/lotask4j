package fun.commons.lotask4j.dto;

import lombok.Data;

/**
 * 租户凭据响应 — 创建 / reset-secret 时返回, secret 明文仅此一次
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class TenantSecretResponse {

    private Long id;

    /** 租户名称 (name 可作 client_id 换 token) */
    private String name;

    /** 明文 secret — 仅本次响应可见, 落库为 AES-GCM 密文 */
    private String tenantSecret;
}
