package fun.commons.lotask4j.service;

import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.web.ApiException;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.lotask4j.entity.AstsApplication;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstsApplicationMapper;
import fun.commons.lotask4j.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 单元测试 — 凭据解析三级回退 + 合成 ADMIN
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务单元测试")
class AuthServiceImplTest {

    @Mock
    private AstsApplicationMapper applicationMapper;

    @Mock
    private ObjectProvider<AccessTokenGenerator> tokenGeneratorProvider;

    @Mock
    private AccessTokenGenerator tokenGenerator;

    private AccessTokenProperties tokenProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        tokenProperties = new AccessTokenProperties();
        AccessTokenProperties.Policy adminPolicy = new AccessTokenProperties.Policy();
        adminPolicy.setExpireTime(7200L);
        tokenProperties.setPolicies(Map.of("ADMIN", adminPolicy));

        authService = new AuthServiceImpl(applicationMapper, tokenGeneratorProvider, tokenProperties);
        ReflectionTestUtils.setField(authService, "adminClientId", "ADMIN");
        ReflectionTestUtils.setField(authService, "adminClientSecret", "");
        org.mockito.Mockito.lenient().when(tokenGeneratorProvider.getIfAvailable()).thenReturn(tokenGenerator);
        org.mockito.Mockito.lenient().when(tokenGenerator.generateToken(eq("ADMIN"), any())).thenReturn("jwt-token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> successData(Object result) {
        ApiResponse<Map<String, Object>> resp = (ApiResponse<Map<String, Object>>) result;
        return resp.getData();
    }

    @Test
    @DisplayName("合成 ADMIN 凭据签发 — app_id=0, 不查库")
    void postToken_syntheticAdmin() {
        Object result = authService.postToken("client_credentials", "ADMIN", "lotask4j-admin-dev-secret");
        Map<String, Object> data = successData(result);
        assertThat(data.get("access_token")).isEqualTo("jwt-token");
        assertThat(data.get("token_type")).isEqualTo("Bearer");
        assertThat(data.get("expires_in")).isEqualTo(7200L);
    }

    @Test
    @DisplayName("错误凭据 → ApiException 20105")
    void postToken_invalidCredentials() {
        assertThatThrownBy(() -> authService.postToken("client_credentials", "ADMIN", "bad"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(BusinessCode.AUTH_INVALID_CREDENTIALS.getCode()));
    }

    @Test
    @DisplayName("非 client_credentials → ApiException 20104")
    void postToken_unsupportedGrant() {
        assertThatThrownBy(() -> authService.postToken("authorization_code", "ADMIN", "x"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("DB 应用行凭据 — secret 匹配签发")
    void postToken_dbApplication() {
        AstsApplication app = new AstsApplication();
        app.setId(42L);
        app.setAppSecret("app-secret");
        app.setStatus("ACTIVE");
        when(applicationMapper.selectOne(any())).thenReturn(app);

        Object result = authService.postToken("client_credentials", "42", "app-secret");
        assertThat(successData(result).get("access_token")).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("DB 应用行凭据 — secret 不匹配拒绝")
    void postToken_dbApplicationWrongSecret() {
        AstsApplication app = new AstsApplication();
        app.setId(42L);
        app.setAppSecret("app-secret");
        app.setStatus("ACTIVE");
        when(applicationMapper.selectOne(any())).thenReturn(app);

        assertThatThrownBy(() -> authService.postToken("client_credentials", "42", "wrong"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("generator 缺失 (accesstoken 未启用) → 503 envelope")
    void postToken_generatorAbsent() {
        when(tokenGeneratorProvider.getIfAvailable()).thenReturn(null);
        Object result = authService.postToken("client_credentials", "ADMIN", "lotask4j-admin-dev-secret");
        ApiResponse<Void> resp = (ApiResponse<Void>) result;
        assertThat(resp.getCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("policy 缺失时回退全局 expireTime")
    void resolveExpire_fallbackToGlobal() {
        tokenProperties.setPolicies(null);
        tokenProperties.setExpireTime(3600L);
        Object result = authService.postToken("client_credentials", "ADMIN", "lotask4j-admin-dev-secret");
        assertThat(successData(result).get("expires_in")).isEqualTo(3600L);
    }
}
