package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建租户请求 (租户即接入方; benefit4j UbmaTenant 模式)
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class TenantCreateRequest {

    /** 租户名称 (name 可作 client_id 换 token) */
    @NotBlank(message = "租户名称不能为空")
    @Size(max = 64, message = "租户名称最长 64 字符")
    private String name;

    /** 租户描述 */
    @Size(max = 512, message = "租户描述最长 512 字符")
    private String description;
}
