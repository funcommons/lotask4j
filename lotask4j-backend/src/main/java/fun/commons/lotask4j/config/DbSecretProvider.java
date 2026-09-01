package fun.commons.lotask4j.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.framework4j.signature.service.SecretProvider;
import fun.commons.lotask4j.entity.AstsApplication;
import fun.commons.lotask4j.mapper.AstsApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC 签名密钥提供者 — framework4j-signature SecretProvider 的 DB 实现。
 *
 * X-Access-Key 解析 (与 AuthServiceImpl.findAppByClientId 同规则简化版):
 *   1. 合成 ADMIN 凭据 client_id → 环境变量 secret (不走 DB)
 *   2. asts_application.name (ACTIVE) → AES 解密后的明文 secret
 *
 * 注册为 Bean 后自动替换 SDK 默认 InMemorySecretProvider
 * (SignatureAutoConfiguration @ConditionalOnMissingBean)。
 *
 * 签名契约 (与 frontend/src/utils/signature.ts 对齐):
 *   toSign = [METHOD, path, timestamp, nonce, MD5(body)].join("\n")
 *   X-Signature = Base64(HmacSHA256(toSign, secret))
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbSecretProvider implements SecretProvider {

    private final AstsApplicationMapper applicationMapper;

    @Value("${lotask4j.security.admin.client-id:ADMIN}")
    private String adminClientId;

    @Value("${lotask4j.security.admin.client-secret:}")
    private String adminClientSecret;

    private static final String DEV_FALLBACK_SECRET = "lotask4j-admin-dev-secret";

    @Override
    public String getSecret(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            return null;
        }
        // 1. 合成 ADMIN 凭据
        if (adminClientId.equals(accessKey)) {
            return (adminClientSecret == null || adminClientSecret.isBlank())
                    ? DEV_FALLBACK_SECRET : adminClientSecret;
        }
        // 2. 应用凭据 (select 经 typeHandler 透明解密)
        LambdaQueryWrapper<AstsApplication> q = new LambdaQueryWrapper<>();
        q.eq(AstsApplication::getName, accessKey)
                .eq(AstsApplication::getStatus, "ACTIVE");
        AstsApplication app = applicationMapper.selectOne(q);
        return app != null ? app.getAppSecret() : null;
    }
}
