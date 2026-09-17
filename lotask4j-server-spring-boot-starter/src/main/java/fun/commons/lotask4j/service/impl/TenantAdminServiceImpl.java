package fun.commons.lotask4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.commons.framework4j.tenant.auth.TenantSecretService;
import fun.commons.framework4j.tenant.entity.TenantEntity;
import fun.commons.framework4j.web.ApiException;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.lotask4j.dto.TenantCreateRequest;
import fun.commons.lotask4j.dto.TenantResponse;
import fun.commons.lotask4j.dto.TenantSecretResponse;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
import fun.commons.lotask4j.service.TenantAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 租户管理服务实现 (平台域)。
 *
 * reset-secret 委托 framework4j-tenant {@link TenantSecretService} (不重复建设):
 * 新密钥生成 + 旧密钥入 prev (grace-hours 24h 双版本宽限) + 撤销该租户全部会话。
 * 创建走本地 SecureRandom base62 (40 字符, 强度高于框架 UUID 重置路径)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminServiceImpl implements TenantAdminService {

    private final AstsTenantMapper tenantMapper;
    private final TenantSecretService tenantSecretService;

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final String BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_LENGTH = 40;

    @Override
    public TenantSecretResponse createTenant(TenantCreateRequest request) {
        AstsTenant tenant = new AstsTenant();
        tenant.setName(request.getName());
        tenant.setDescription(request.getDescription());
        tenant.setStatus("ACTIVE");
        tenant.setChannel("OPS");
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setUpdatedAt(OffsetDateTime.now());

        String secret = generateSecret();
        tenant.setTenantSecret(secret);
        tenantMapper.insert(tenant);

        log.info("创建租户: id={}, name={}", tenant.getId(), tenant.getName());
        return secretResponse(tenant, secret);
    }

    @Override
    public TenantSecretResponse resetSecret(Long id) {
        requireTenant(id);
        // 委托框架: 新密钥 + prev 双版本宽限 (grace-hours) + 撤销全部存量会话
        ApiResponse<Map<String, Object>> result = tenantSecretService.reset(id);
        Map<String, Object> data = result.getData();
        TenantSecretResponse resp = new TenantSecretResponse();
        // 框架返回 Map 的 id 为 OpenID 串 (IdObfuscator.toOpenId), 转回雪花 Long 与创建响应同型
        resp.setId(fun.commons.framework4j.id.util.IdObfuscator.fromOpenId(String.valueOf(data.get("id"))));
        resp.setName(String.valueOf(data.get("name")));
        // 框架返回 Map key 为 tenant_secret (snake); 兼容 camel 变体
        Object secret = data.containsKey("tenant_secret") ? data.get("tenant_secret") : data.get("tenantSecret");
        resp.setTenantSecret(String.valueOf(secret));
        log.info("重置租户 secret (委托 TenantSecretService): id={}", id);
        return resp;
    }

    @Override
    public void setStatus(Long id, String status) {
        // INACTIVE 兼容映射为契约状态机 SUSPEND (§6.0); 换 token 仅认 ACTIVE
        String target = "INACTIVE".equals(status) ? "SUSPEND" : status;
        if (!"ACTIVE".equals(target) && !"SUSPEND".equals(target)) {
            throw new ApiException(BusinessCode.APPLICATION_STATUS_INVALID.getCode(),
                    BusinessCode.APPLICATION_STATUS_INVALID.getMessage());
        }
        AstsTenant tenant = requireTenant(id);
        tenant.setStatus(target);
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantMapper.updateById(tenant);
        log.info("租户状态变更: id={}, status={}", id, target);
    }

    @Override
    public void deleteTenant(Long id) {
        requireTenant(id);
        tenantMapper.deleteById(id);
        log.info("删除租户 (逻辑删除): id={}", id);
    }

    @Override
    public TenantResponse getTenant(Long id) {
        return toResponse(requireTenant(id));
    }

    @Override
    public List<TenantResponse> listTenants(String keyword, String status, long page, long pageSize) {
        Page<AstsTenant> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<AstsTenant> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(AstsTenant::getName, keyword);
        }
        if (status != null && !status.isBlank()) {
            q.eq(AstsTenant::getStatus, status);
        }
        q.orderByDesc(AstsTenant::getCreatedAt);
        return tenantMapper.selectPage(p, q).getRecords()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public long countTenants(String keyword, String status) {
        LambdaQueryWrapper<AstsTenant> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(AstsTenant::getName, keyword);
        }
        if (status != null && !status.isBlank()) {
            q.eq(AstsTenant::getStatus, status);
        }
        return tenantMapper.selectCount(q);
    }

    private AstsTenant requireTenant(Long id) {
        AstsTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new ApiException(BusinessCode.APPLICATION_NOT_FOUND.getCode(),
                    "租户不存在: " + id);
        }
        return tenant;
    }

    private String generateSecret() {
        StringBuilder sb = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private TenantSecretResponse secretResponse(TenantEntity tenant, String secret) {
        TenantSecretResponse resp = new TenantSecretResponse();
        resp.setId(tenant.getId());
        resp.setName(tenant.getName());
        resp.setTenantSecret(secret);
        return resp;
    }

    private TenantResponse toResponse(AstsTenant tenant) {
        TenantResponse resp = new TenantResponse();
        resp.setId(tenant.getId());
        resp.setName(tenant.getName());
        resp.setDescription(tenant.getDescription());
        resp.setEmail(tenant.getEmail());
        resp.setChannel(tenant.getChannel());
        resp.setStatus(tenant.getStatus());
        resp.setCreatedAt(tenant.getCreatedAt());
        resp.setUpdatedAt(tenant.getUpdatedAt());
        return resp;
    }
}
