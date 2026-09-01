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
 * 接入应用管理集成测试
 *
 * 覆盖:
 * 1. ADMIN token 门禁 (无 token 401)
 * 2. 创建 → 一次性明文 secret → 落库为 AES-GCM 密文 → select 透明解密可换 token
 * 3. reset-secret 旧凭据失效
 * 4. 停用后不可换 token
 * 5. 列表不含 secret
 *
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 与 AdminAuthGuardTest 相同: 恢复主配置 exclude (仅放行 auth 端点), 让 ADMIN 守卫生效
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("接入应用管理测试")
class AdminApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=PLATFORM&client_secret=lotask4j-platform-dev-secret"))
                .andExpect(status().isOk())
                .andReturn();
        // form 端点返回 envelope; 提取 token
        String body = r.getResponse().getContentAsString();
        adminToken = extractJsonString(body, "access_token");
        assertThat(adminToken).isNotBlank();
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

    private MvcResult createApp(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/applications")
                        .header("Authorization", "Bearer " + adminToken)
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
        mockMvc.perform(post("/api/v1/admin/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        String appName = unique("app-enc");
        String body = createApp(appName).getResponse().getContentAsString();
        String secret = extractJsonString(body, "appSecret");
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
    @DisplayName("reset-secret 后旧 secret 失效, 新 secret 可用")
    void resetSecret_oldInvalid() throws Exception {
        String name = unique("app-reset");
        String body = createApp(name).getResponse().getContentAsString();
        String oldSecret = extractJsonString(body, "appSecret");
        long id = Long.parseLong(extractJsonString(body, "id"));

        String newBody = mockMvc.perform(post("/api/v1/admin/applications/" + id + "/reset-secret")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String newSecret = extractJsonString(newBody, "appSecret");
        assertThat(newSecret).isNotEqualTo(oldSecret);

        // 旧 secret 拒绝 (内置端点凭据失败 → 200 envelope + code=401;
        // E1 委托 TenantSecretService 后宽限期内旧钥可用, 届时反转)
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + name + "&client_secret=" + oldSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        // 新 secret 通过
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + name + "&client_secret=" + newSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("停用后不可换 token; 列表不含 secret")
    void inactivate_blocksToken_and_listHasNoSecret() throws Exception {
        String name = unique("app-disable");
        String body = createApp(name).getResponse().getContentAsString();
        String secret = extractJsonString(body, "appSecret");
        long id = Long.parseLong(extractJsonString(body, "id"));

        // 停用
        mockMvc.perform(post("/api/v1/admin/applications/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
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
        mockMvc.perform(get("/api/v1/admin/applications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[?(@.name=='app-disable')].appSecret").doesNotExist());
    }
}
