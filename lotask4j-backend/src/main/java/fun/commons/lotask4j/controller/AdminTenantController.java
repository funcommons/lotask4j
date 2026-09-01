package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.dto.TenantCreateRequest;
import fun.commons.lotask4j.dto.TenantStatusRequest;
import fun.commons.lotask4j.dto.TenantResponse;
import fun.commons.lotask4j.dto.TenantSecretResponse;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.tenant.annotation.PlatformDomain;
import fun.commons.lotask4j.service.TenantAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台域租户管理 Controller — 租户即接入方 (benefit4j 同款)
 *
 * secret 明文仅创建 / reset-secret 响应出现一次, 列表与详情不含 secret。
 * reset-secret 委托 framework4j-tenant (旧钥宽限期 + 撤全部会话)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@RequiresToken("TENANT")
@PlatformDomain            // 平台域: 仅平台身份 (tenant_id=0) 可达
@Tag(name = "Admin Tenants", description = "租户管理 (client_credentials 凭据)")
public class AdminTenantController {

    private final TenantAdminService tenantAdminService;

    @GetMapping
    @Operation(summary = "分页查询租户列表", description = "keyword 模糊匹配名称; 不含 secret")
    public ApiResponse<Map<String, Object>> listTenants(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize) {

        long total = tenantAdminService.countTenants(keyword, status);
        List<TenantResponse> items = tenantAdminService.listTenants(keyword, status, page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("items", items);
        return ApiResponse.success(data);
    }

    @GetMapping("/{id}")
    @Operation(summary = "租户详情", description = "不含 secret")
    public ApiResponse<TenantResponse> getTenant(@PathVariable("id") Long id) {
        return ApiResponse.success(tenantAdminService.getTenant(id));
    }

    @PostMapping
    @Operation(summary = "创建租户", description = "返回一次性明文 secret")
    public ApiResponse<TenantSecretResponse> createTenant(
            @Valid @RequestBody TenantCreateRequest request) {
        return ApiResponse.success(tenantAdminService.createTenant(request));
    }

    @PostMapping("/{id}/reset-secret")
    @Operation(summary = "重置 secret", description = "旧密钥进入 24h 宽限期; 撤销该租户全部存量会话; 返回一次性明文")
    public ApiResponse<TenantSecretResponse> resetSecret(@PathVariable("id") Long id) {
        return ApiResponse.success(tenantAdminService.resetSecret(id));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "启停租户", description = "ACTIVE / SUSPEND (INACTIVE 兼容映射 SUSPEND); 停用后不可换 token")
    public ApiResponse<Void> setStatus(@PathVariable("id") Long id,
                                       @Valid @RequestBody TenantStatusRequest request) {
        tenantAdminService.setStatus(id, request.getStatus());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除租户", description = "逻辑删除")
    public ApiResponse<Void> deleteTenant(@PathVariable("id") Long id) {
        tenantAdminService.deleteTenant(id);
        return ApiResponse.success();
    }
}
