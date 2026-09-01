package fun.commons.lotask4j.demo.config;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.commons.lotask4j.config.DbSecretProvider;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DbSecretProvider 单元测试（HMAC 签名密钥解析: 平台凭据 / 租户凭据）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DbSecretProvider 单元测试")
class DbSecretProviderTest {

    @Mock
    private AstsTenantMapper tenantMapper;

    private DbSecretProvider provider;

    @BeforeAll
    static void initLambdaCache() {
        // LambdaQueryWrapper 需要实体表信息缓存
        org.apache.ibatis.session.Configuration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, AstsTenant.class);
    }

    @BeforeEach
    void setUp() {
        provider = new DbSecretProvider(tenantMapper);
        ReflectionTestUtils.setField(provider, "platformClientId", "PLATFORM");
        ReflectionTestUtils.setField(provider, "platformClientSecret", "platform-secret");
    }

    @Test
    @DisplayName("accessKey null/blank → null")
    void nullOrBlank() {
        assertThat(provider.getSecret(null)).isNull();
        assertThat(provider.getSecret("")).isNull();
        assertThat(provider.getSecret("   ")).isNull();
    }

    @Test
    @DisplayName("平台凭据命中 → env secret (不走 DB)")
    void platformCredentials() {
        assertThat(provider.getSecret("PLATFORM")).isEqualTo("platform-secret");
        assertThat(provider.getSecret("PLATFORM")).isEqualTo("platform-secret");
    }

    @Test
    @DisplayName("平台 secret 未配置 (空) → null")
    void platformNoSecret() {
        ReflectionTestUtils.setField(provider, "platformClientSecret", "");
        assertThat(provider.getSecret("PLATFORM")).isNull();
        ReflectionTestUtils.setField(provider, "platformClientSecret", null);
        assertThat(provider.getSecret("PLATFORM")).isNull();
    }

    @Test
    @DisplayName("租户凭据 ACTIVE → 明文 secret (select 透明解密)")
    void tenantSecret() {
        AstsTenant t = new AstsTenant();
        t.setTenantSecret("decrypted-plain");
        when(tenantMapper.selectOne(any())).thenReturn(t);

        assertThat(provider.getSecret("order-service")).isEqualTo("decrypted-plain");
    }

    @Test
    @DisplayName("租户不存在 / 非 ACTIVE → null")
    void tenantNotFound() {
        when(tenantMapper.selectOne(any())).thenReturn(null);
        assertThat(provider.getSecret("ghost")).isNull();
    }
}
