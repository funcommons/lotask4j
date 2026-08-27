package fun.commons.lotask4j.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试 — client_credentials 签发
 *
 * 前置: 本地 Redis :6379 (token metadata 写入; docker compose redis)
 * 合成 ADMIN 凭据: client_id=ADMIN, secret 未配置时用开发默认值
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("认证端点测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String DEV_SECRET = "lotask4j-admin-dev-secret";

    @Test
    @DisplayName("client_credentials 签发成功 — form-encoded")
    void postToken_success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "ADMIN")
                        .param("client_secret", DEV_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andExpect(jsonPath("$.data.expires_in").value(7200));
    }

    @Test
    @DisplayName("client_credentials 签发成功 — query 参数兜底")
    void postToken_queryFallback() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token?grant_type=client_credentials&client_id=ADMIN&client_secret=" + DEV_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("错误 secret → 业务码 20105 (HTTP 200 envelope)")
    void postToken_wrongSecret() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "ADMIN")
                        .param("client_secret", "wrong-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20105))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("不支持的 grant_type → 业务码 20104")
    void postToken_unsupportedGrantType() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", "ADMIN")
                        .param("client_secret", DEV_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20104));
    }

    @Test
    @DisplayName("缺少参数 → 业务码 20103")
    void postToken_missingParams() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20103));
    }

    @Test
    @DisplayName("原始 body (非 param) 的 form 编码也能解析")
    void postToken_rawFormBody() throws Exception {
        String body = "grant_type=client_credentials&client_id=ADMIN&client_secret=" + DEV_SECRET;
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        assert result.getResponse().getContentAsString().contains("access_token");
    }
}
