package fun.commons.lotask4j.service;

/**
 * 认证服务 — client_credentials token 签发
 *
 * 蓝本: benefit4j BenefitAuthService
 */
public interface AuthService {

    /**
     * client_credentials 换 access_token
     *
     * @param grantType    授权类型 (仅 client_credentials)
     * @param clientId     client_id (ADMIN 合成凭据 / asts_application OpenID|原始ID|name)
     * @param clientSecret client_secret
     * @param scope        应用凭据选 policy (client / worker, 可选默认 client); ADMIN 合成凭据忽略
     * @return ApiResponse: { access_token, token_type: "Bearer", expires_in }
     */
    Object postToken(String grantType, String clientId, String clientSecret, String scope);
}
