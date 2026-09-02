package fun.commons.lotask4j.controller;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.tenant.context.TenantIdentity;
import fun.commons.framework4j.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录身份查询端点
 *
 * framework4j-tenant v1.5.1 签发的 JWT payload 不含 tenant_id claim
 * (租户上下文存于 Redis 会话侧), 前端/调用方无法从 token 自助判别身份域。
 * 此端点用请求令牌反查 TenantIdentity 并回显, 供控制台登录后判定
 * 平台 (tenant_id=0) / 租户 (>0) 身份, 路由到 /platform 或 /tenant 域。
 *
 * 双域可达: 只挂 @RequiresToken("TENANT") 不挂域注解 —
 * 平台与租户 token 同为 TENANT 型, 域守卫只约束带域注解的 controller。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiresToken("TENANT")
@Tag(name = "登录身份", description = "查询当前令牌的租户身份 (控制台双域路由用)")
public class AuthMeController {

    /**
     * 回显当前令牌身份。
     *
     * @return tenantId: 0=平台身份, &gt;0=租户 id; null=上下文缺失 (兜底放行)
     */
    @GetMapping("/me")
    @Operation(summary = "查询登录身份", description = "返回当前令牌的 tenantId (0=平台, >0=租户)")
    public ApiResponse<Map<String, Long>> me() {
        Long tenantId = TenantIdentity.currentTenantId(null);
        log.debug("身份查询: tenantId={}", tenantId);
        // LinkedHashMap: tenantId 可为 null (上下文缺失), Map.of 不允许 null 值
        Map<String, Long> data = new java.util.LinkedHashMap<>();
        data.put("tenantId", tenantId);
        return ApiResponse.success(data);
    }
}
