package fun.commons.lotask4j.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户管理集成测试 (平台域 /api/v1/admin/tenants)
 *
 * 覆盖:
 * 1. 平台 token 门禁 (无 token 401)
 * 2. 创建 → 一次性明文 secret → 落库为 AES-GCM 密文 → select 透明解密可换 token
 * 3. reset-secret 旧凭据宽限期内仍可换 (委托 TenantSecretService 双版本)
 * 4. 停用 (SUSPEND) 后不可换 token
 * 5. 列表不含 secret
 *
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 与 AdminAuthGuardTest 相同: 恢复主配置 exclude (仅放行 auth 端点), 让平台域守卫生效
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("租户管理测试")
class AdminTenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String platformToken;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=PLATFORM&client_secret=lotask4j-platform-dev-secret"))
                .andExpect(status().isOk())
                .andReturn();
        // form 端点返回 envelope; 提取 token
        String body = r.getResponse().getContentAsString();
        platformToken = extractJsonString(body, "access_token");
        assertThat(platformToken).isNotBlank();
    }

    private static String extractJsonString(String json, String key) {
        int i = json.indexOf("\"" + key + "\":\"");
        if (i >= 0) {
            int start = i + key.length() + 4;
            return json.substring(start, json.indexOf('"', start));
        }
        // 数字形式 (id 无 OpenId 序列化时为数字)
        i = json.indexOf("\"" + key + "\":");
        if (i < 0) return null;
        int start = i + key.length() + 3;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return json.substring(start, end);
    }

    private static final java.util.concurrent.atomic.AtomicLong SEQ =
            new java.util.concurrent.atomic.AtomicLong(System.nanoTime());

    private static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    private MvcResult createTenant(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
    }

    @Test
    @DisplayName("无 token 401; 创建返回一次性明文 secret, 落库为密文")
    void create_secretEncryptedAtRest() throws Exception {
        // 门禁
        mockMvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        String appName = unique("tn-enc");
        String body = createTenant(appName).getResponse().getContentAsString();
        String secret = extractJsonString(body, "tenantSecret");
        assertThat(secret).isNotBlank().hasSize(40);
        long id = Long.parseLong(extractJsonString(body, "id"));

        // 落库为 AES-GCM 密文: JDBC 直读原始列值 (绕过 typeHandler), 不等于明文
        String rawColumn = jdbcTemplate.queryForObject(
                "SELECT tenant_secret FROM asts_tenant WHERE id = " + id, String.class);
        assertThat(rawColumn)
                .as("落库应为密文而非明文")
                .isNotEqualTo(secret)
                .isNotBlank();

        // 明文 secret 可换 client token (端到端)
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + appName + "&client_secret=" + secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("reset-secret: 新 secret 可用; 旧 secret 宽限期内仍可换")
    void resetSecret_oldInvalid() throws Exception {
        String name = unique("tn-reset");
        String body = createTenant(name).getResponse().getContentAsString();
        String oldSecret = extractJsonString(body, "tenantSecret");
        long id = Long.parseLong(extractJsonString(body, "id"));

        String newBody = mockMvc.perform(post("/api/v1/admin/tenants/" + id + "/reset-secret")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String newSecret = extractJsonString(newBody, "tenantSecret");
        assertThat(newSecret).isNotEqualTo(oldSecret);

        // 旧 secret 在宽限期内 (grace-hours=24h) 仍可换 token (框架双版本语义)
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + name + "&client_secret=" + oldSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        // 新 secret 通过
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + name + "&client_secret=" + newSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

@Test
    @DisplayName("reset-secret 宽限期过期 (prev_at 拨到 25h 前) → 旧 secret 拒绝")
    void resetSecret_graceExpired() throws Exception {
        String name = unique("tn-grace");
        String body = createTenant(name).getResponse().getContentAsString();
        String oldSecret = extractJsonString(body, "tenantSecret");
        long id = Long.parseLong(extractJsonString(body, "id"));

        mockMvc.perform(post("/api/v1/admin/tenants/" + id + "/reset-secret")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // prev_at 拨到 25h 前 (grace-hours=24 已过)
        jdbcTemplate.update(
                "UPDATE asts_tenant SET tenant_secret_prev_at = NOW() - INTERVAL '25 hours' WHERE id = " + id);

        // 宽限期已过 → 旧 secret 拒绝 (内置端点凭据失败 → 200 envelope + code=401)
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + name + "&client_secret=" + oldSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

        @Test
    @DisplayName("停用后不可换 token; 列表不含 secret")
    void inactivate_blocksToken_and_listHasNoSecret() throws Exception {
        String name = unique("tn-disable");
        String body = createTenant(name).getResponse().getContentAsString();
        String secret = extractJsonString(body, "tenantSecret");
        long id = Long.parseLong(extractJsonString(body, "id"));

        // 停用
        mockMvc.perform(post("/api/v1/admin/tenants/" + id + "/status")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + name + "&client_secret=" + secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        // 列表无 secret 字段
        mockMvc.perform(get("/api/v1/admin/tenants")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[?(@.name=='app-disable')].appSecret").doesNotExist());
    }
}
