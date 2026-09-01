package fun.commons.lotask4j.service;

import fun.commons.lotask4j.dto.ApplicationCreateRequest;
import fun.commons.lotask4j.dto.ApplicationResponse;
import fun.commons.lotask4j.dto.ApplicationSecretResponse;

import java.util.List;

/**
 * 接入应用管理服务 — client_credentials 凭据签发
 *
 * secret 生成后 AES-GCM 密文落库 (framework4j-sensitive typeHandler),
 * 明文仅在创建 / reset-secret 响应中出现一次。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public interface ApplicationService {

    /** 创建应用, 返回一次性明文 secret */
    ApplicationSecretResponse createApplication(ApplicationCreateRequest request);

    /** 重新生成 secret, 返回一次性明文 */
    ApplicationSecretResponse resetSecret(Long id);

    /** 启用/停用 (ACTIVE / INACTIVE) */
    void setStatus(Long id, String status);

    /** 逻辑删除 */
    void deleteApplication(Long id);

    /** 应用详情 (无 secret) */
    ApplicationResponse getApplication(Long id);

    /** 分页列表 (无 secret), keyword 模糊匹配 name */
    List<ApplicationResponse> listApplications(String keyword, String status, long page, long pageSize);

    /** 计数 (配合分页) */
    long countApplications(String keyword, String status);
}
