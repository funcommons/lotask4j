package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.dto.StatsOverviewResponse;
import fun.commons.lotask4j.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 平台域 (admin) token 守卫集成测试 (租户化后)
 *
 * 通过 @TestPropertySource 恢复主配置的 exclude 列表 (仅放行 auth 端点),
 * 验证 @RequiresToken("TENANT") + @PlatformDomain (tenant_id=0) 的 401 行为
 * 与平台凭据 (PLATFORM) 换 token 的放行。
 * 前置: 本地 Redis :6379 (token 签发 + 校验)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 列表属性整组覆盖 application-test.yml 的 /api/v1/** 全放行
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("平台域 (admin) token 守卫测试")
class AdminAuthGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    private static final String PLATFORM_SECRET = "lotask4j-platform-dev-secret";

    @BeforeEach
    void setUp() {
        StatsOverviewResponse stats = new StatsOverviewResponse();
        when(adminService.getStatsOverview()).thenReturn(stats);
    }

    private String mintToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "PLATFORM")
                        .param("client_secret", PLATFORM_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // 简易提取 (避免引 jpath 依赖): "access_token":"xxx"
        int idx = body.indexOf("\"access_token\":\"");
        assertThat(idx).isGreaterThan(-1);
        int start = idx + "\"access_token\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    @Test
    @DisplayName("无 token 访问 admin → HTTP 401 + envelope")
    void adminEndpoint_noToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("伪造 token → HTTP 401")
    void adminEndpoint_garbageToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview")
                        .header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("合法平台 token (tenant_id=0) → 200 放行")
    void adminEndpoint_validToken_200() throws Exception {
        String token = mintToken();
        mockMvc.perform(get("/api/v1/admin/stats/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("auth 端点本身在 exclude 列表 — 免 token")
    void authEndpoint_excluded() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "PLATFORM")
                        .param("client_secret", PLATFORM_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
