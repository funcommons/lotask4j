package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建接入应用请求
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Data
public class ApplicationCreateRequest {

    /** 应用名称 (client_id 可用 name 登录, 唯一性由调用方保证) */
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称最长 100 字符")
    private String name;

    /** 应用描述 */
    @Size(max = 500, message = "应用描述最长 500 字符")
    private String description;
}
