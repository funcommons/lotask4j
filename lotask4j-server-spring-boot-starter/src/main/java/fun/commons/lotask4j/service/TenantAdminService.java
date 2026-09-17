package fun.commons.lotask4j.service;

import fun.commons.lotask4j.dto.TenantCreateRequest;
import fun.commons.lotask4j.dto.TenantResponse;
import fun.commons.lotask4j.dto.TenantSecretResponse;

import java.util.List;

/**
 * 租户管理服务 (平台域) — client_credentials 凭据签发
 *
 * secret 生成后 AES-GCM 密文落库 (framework4j-sensitive typeHandler),
 * 明文仅在创建 / reset-secret 响应中出现一次。
 * reset 委托 framework4j-tenant {@code TenantSecretService}:
 * 旧密钥入 prev 双版本 (grace-hours 宽限期) + 批量撤销该租户全部存量会话。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public interface TenantAdminService {

    /** 创建租户, 返回一次性明文 secret */
    TenantSecretResponse createTenant(TenantCreateRequest request);

    /** 重置 secret (宽限期双版本 + 撤会话), 返回一次性明文 */
    TenantSecretResponse resetSecret(Long id);

    /** 启用/停用 (ACTIVE / SUSPEND; INACTIVE 入参兼容映射 SUSPEND) */
    void setStatus(Long id, String status);

    /** 逻辑删除 */
    void deleteTenant(Long id);

    /** 租户详情 (无 secret) */
    TenantResponse getTenant(Long id);

    /** 分页列表 (无 secret), keyword 模糊匹配 name */
    List<TenantResponse> listTenants(String keyword, String status, long page, long pageSize);

    /** 计数 (配合分页) */
    long countTenants(String keyword, String status);
}
