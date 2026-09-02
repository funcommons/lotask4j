package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.AstsApplication;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/auth/me — 登录身份查询端点 (前端双域路由判定依据)。
 *
 * framework4j-tenant JWT payload 不含 tenant_id claim, 前端无法自助判身份,
 * 由本端点反查 TenantIdentity 回显。断言双身份语义:
 *   平台凭据 → tenantId=0; 租户凭据 → tenantId=租户雪花 id (>0)。
 *
 * TestPropertySource 把 exclude-path-patterns 整组替换为仅放行 /auth/token —
 * /me 恢复 @RequiresToken("TENANT") 真实守卫语义 (无 token 请求 401)。
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(classes = AstsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("AuthMe 身份查询端点测试")
class AuthMeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AstsTenantMapper tenantMapper;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private String tenantName;

    @BeforeEach
    void setUp() {
        // 建租户 (走 mapper — tenant_secret 经 typeHandler AES 加密)
        var tenant = new AstsTenant();
        tenant.setName("me-t-" + SEQ.incrementAndGet());
        tenant.setTenantSecret("me-secret");
        tenant.setStatus("ACTIVE");
        tenantMapper.insert(tenant);
        tenantName = tenant.getName();
    }

    private String issueToken(String clientId, String clientSecret) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"access_token\":\"");
        return body.substring(i + 16, body.indexOf('"', i + 16));
    }

    @Test
    @DisplayName("租户凭据 → me 返回本租户雪花 id (>0)")
    void me_TenantIdentity() throws Exception {
        String token = issueToken(tenantName, "me-secret");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tenantId").isNumber());
    }

    @Test
    @DisplayName("平台凭据 → me 返回 tenantId=0 (合成租户)")
    void me_PlatformIdentity() throws Exception {
        String token = issueToken("PLATFORM", "lotask4j-platform-dev-secret");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tenantId").value(0));
    }

    @Test
    @DisplayName("无 token → 401 (exclude 列表仅放行 /auth/token)")
    void me_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
