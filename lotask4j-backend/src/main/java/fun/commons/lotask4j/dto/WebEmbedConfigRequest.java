package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Web Embed 配置请求 DTO
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class WebEmbedConfigRequest {

    /**
     * 主键 ID（更新时必填）
     */
    private Long id;

    /**
     * 租户归属 (决定 embed 短期 token 的租户 claim)。
     * 创建必填 (校验在 AdminWebEmbedServiceImpl.createConfig, update 可选=保留原归属)
     */
    private Long tenantId;

    /**
     * 访问密钥（accessKey）
     */
    @NotBlank(message = "configKey 不能为空")
    @Size(max = 64)
    private String configKey;

    /**
     * 配置名称
     */
    @NotBlank(message = "configName 不能为空")
    @Size(max = 128)
    private String configName;

    /**
     * 默认用户 ID
     */
    @NotBlank(message = "userId 不能为空")
    @Size(max = 64)
    private String userId;

    /**
     * 是否开放模式
     */
    private Integer isOpen;

    /**
     * 回调验证地址
     */
    @Size(max = 512)
    private String callbackUrl;

    /**
     * 组件配置（JSONB）
     */
    private Map<String, Object> config;

    /**
     * 限定组件类型（必填，不能为 all）
     * task-list / task-detail / task-card
     */
    @NotBlank(message = "componentType 必填")
    @Pattern(regexp = "^(task-list|task-detail|task-card)$", message = "componentType 必须是 task-list/task-detail/task-card 之一")
    private String componentType;

    /**
     * 允许的域名
     */
    @Size(max = 2048)
    private String allowedDomains;
}
