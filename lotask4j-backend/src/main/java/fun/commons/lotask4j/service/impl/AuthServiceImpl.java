package fun.commons.lotask4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.web.ApiException;
import fun.commons.lotask4j.entity.AstsApplication;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstsApplicationMapper;
import fun.commons.lotask4j.service.AuthService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证服务实现 — 仿 benefit4j DefaultBenefitAuthService
 *
 * 控制台登录走合成 ADMIN 凭据 (app_id=0, 不入库, secret 走环境变量);
 * asts_application 表为未来 client 租户凭据预留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(AccessTokenGenerator.class)
public class AuthServiceImpl implements AuthService {

    private final AstsApplicationMapper applicationMapper;
    private final ObjectProvider<AccessTokenGenerator> tokenGeneratorProvider;
    private final AccessTokenProperties tokenProperties;

    @Value("${lotask4j.security.admin.client-id:ADMIN}")
    private String adminClientId;

    @Value("${lotask4j.security.admin.client-secret:}")
    private String adminClientSecret;

    /** token 类型 — 与 application.yml framework4j.access-token.policies.ADMIN 对应 */
    private static final String TOKEN_TYPE = "ADMIN";

    private static final String DEV_FALLBACK_SECRET = "lotask4j-admin-dev-secret";

    @PostConstruct
    void warnFallbackSecret() {
        if (adminClientSecret == null || adminClientSecret.isBlank()) {
            log.warn("[Auth] lotask4j.security.admin.client-secret 未配置, 使用开发默认值 (生产环境必须通过环境变量覆盖)");
        }
    }

    @Override
    public Object postToken(String grantType, String clientId, String clientSecret, String scope) {
        if (grantType == null || grantType.isBlank()
                || clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw new ApiException(BusinessCode.AUTH_PARAM_MISSING.getCode(),
                    "grant_type / client_id / client_secret 不能为空");
        }
        if (!"client_credentials".equals(grantType)) {
            throw new ApiException(BusinessCode.AUTH_GRANT_TYPE_UNSUPPORTED.getCode(),
                    BusinessCode.AUTH_GRANT_TYPE_UNSUPPORTED.getMessage());
        }
        // scope: 应用凭据选 policy (client/worker); 合成 ADMIN 凭据忽略 scope 恒为 ADMIN
        String tokenType = TOKEN_TYPE;
        if (!adminClientId.equals(clientId)) {
            if (scope != null && !scope.isBlank() && !"client".equals(scope) && !"worker".equals(scope)) {
                throw new ApiException(BusinessCode.AUTH_PARAM_MISSING.getCode(),
                        "scope 仅支持 client / worker");
            }
            tokenType = "worker".equals(scope) ? "worker" : "client";
        }

        AstsApplication app = resolveApp(clientId, clientSecret);
        if (app == null) {
            throw new ApiException(BusinessCode.AUTH_INVALID_CREDENTIALS.getCode(),
                    BusinessCode.AUTH_INVALID_CREDENTIALS.getMessage());
        }

        AccessTokenGenerator generator = tokenGeneratorProvider.getIfAvailable();
        if (generator == null) {
            // framework4j-accesstoken 未启用 (enabled=false) 时
            return ApiResponse.fail(503, "认证服务未启用");
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("app_id", app.getId());

        String token = generator.generateToken(tokenType, claims);
        long expires = resolveExpireSeconds(tokenType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", token);
        result.put("token_type", "Bearer");
        result.put("expires_in", expires);
        return ApiResponse.success(result);
    }

    /**
     * client_id 解析 (仿 benefit4j 三级回退):
     *   0. 合成 ADMIN 凭据 (配置项, 不依赖 DB)
     *   1. OpenID (Base62 混淆串)
     *   2. 原始 Long id
     *   3. name (向后兼容)
     */
    private AstsApplication resolveApp(String clientId, String clientSecret) {
        if (adminClientId.equals(clientId) && resolveAdminSecret().equals(clientSecret)) {
            return syntheticAdminApp();
        }
        AstsApplication app = findAppByClientId(clientId);
        if (app == null || !clientSecret.equals(app.getAppSecret())) return null;
        return app;
    }

    /** ADMIN secret: 环境变量优先, 空则开发默认值 (启动期已 WARN) */
    private String resolveAdminSecret() {
        return (adminClientSecret == null || adminClientSecret.isBlank())
                ? DEV_FALLBACK_SECRET : adminClientSecret;
    }

    private AstsApplication syntheticAdminApp() {
        AstsApplication app = new AstsApplication();
        app.setId(0L);
        app.setName(adminClientId);
        app.setStatus("ACTIVE");
        app.setDescription("Synthetic admin app — console bootstrap, no DB row");
        return app;
    }

    private AstsApplication findAppByClientId(String clientId) {
        // 1. OpenID 解码
        if (IdObfuscator.isValid(clientId)) {
            try {
                long rawId = IdObfuscator.fromOpenId(clientId);
                AstsApplication app = selectActiveById(rawId);
                if (app != null) return app;
            } catch (Exception ignored) {
                // 回退
            }
        }
        // 2. 原始 Long id
        if (clientId.matches("\\d+")) {
            try {
                AstsApplication app = selectActiveById(Long.parseLong(clientId));
                if (app != null) return app;
            } catch (NumberFormatException ignored) {
                // 回退
            }
        }
        // 3. 按 name
        LambdaQueryWrapper<AstsApplication> query = new LambdaQueryWrapper<>();
        query.eq(AstsApplication::getName, clientId)
                .eq(AstsApplication::getStatus, "ACTIVE");
        return applicationMapper.selectOne(query);
    }

    private AstsApplication selectActiveById(long id) {
        LambdaQueryWrapper<AstsApplication> q = new LambdaQueryWrapper<>();
        q.eq(AstsApplication::getId, id)
                .eq(AstsApplication::getStatus, "ACTIVE");
        return applicationMapper.selectOne(q);
    }

    private long resolveExpireSeconds(String tokenType) {
        AccessTokenProperties.Policy policy = tokenProperties.getPolicies() == null
                ? null : tokenProperties.getPolicies().get(tokenType);
        if (policy != null && policy.getExpireTime() != null) {
            return policy.getExpireTime();
        }
        return tokenProperties.getExpireTime() > 0 ? tokenProperties.getExpireTime() : 7200L;
    }
}
