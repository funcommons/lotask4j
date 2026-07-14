package fun.commons.lotask4j.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Web Embed 配置响应 DTO
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class WebEmbedConfigResponse {

    private Long id;
    private String configKey;
    private String configName;
    private String userId;
    private Integer isOpen;
    private String callbackUrl;
    private Map<String, Object> config;
    private String componentType;
    private String allowedDomains;
    private Integer isEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * 生成的嵌入 URL（供前端使用）
     */
    private String embedUrl;
}
