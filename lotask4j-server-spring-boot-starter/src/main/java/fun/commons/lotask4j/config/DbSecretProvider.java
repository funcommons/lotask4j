package fun.commons.lotask4j.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.framework4j.signature.service.SecretProvider;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC 签名密钥提供者 — framework4j-signature SecretProvider 的 DB 实现。
 *
 * X-Access-Key 解析:
 *   1. 平台凭据 (framework4j.tenant.platform.client-id, 合成租户) → env secret (不走 DB)
 *   2. asts_tenant.name (ACTIVE) → AES 解密后的明文 secret
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

    private final AstsTenantMapper applicationMapper;

    @Value("${framework4j.tenant.platform.client-id:PLATFORM}")
    private String platformClientId;

    @Value("${framework4j.tenant.platform.client-secret:}")
    private String platformClientSecret;

    @Override
    public String getSecret(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            return null;
        }
        // 1. 平台凭据 (合成租户, 不入库)
        if (platformClientId.equals(accessKey)) {
            return (platformClientSecret == null || platformClientSecret.isBlank())
                    ? null : platformClientSecret;
        }
        // 2. 租户凭据 (select 经 typeHandler 透明解密)
        LambdaQueryWrapper<AstsTenant> q = new LambdaQueryWrapper<>();
        q.eq(AstsTenant::getName, accessKey)
                .eq(AstsTenant::getStatus, "ACTIVE");
        AstsTenant app = applicationMapper.selectOne(q);
        return app != null ? app.getTenantSecret() : null;
    }
}
