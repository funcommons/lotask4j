package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.AstsApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * client 域类型列表端点测试 (GET /api/v1/client/tasks/types)
 *
 * 任务列表页的类型下拉数据源: 只返回 claim 租户已配置且启用的类型 (租户内可见性)。
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(classes = AstsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("Client 类型列表端点测试")
class ClientTypesEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private String tenantToken;
    private long tenantId;

    @Autowired
    private fun.commons.lotask4j.mapper.AstsTenantMapper tenantMapper;

    @BeforeEach
    void setUp() throws Exception {
        // 建租户 (走 mapper — tenant_secret 经 typeHandler AES 加密) + 换 token
        var tenant = new fun.commons.lotask4j.entity.AstsTenant();
        tenant.setName("types-t-" + SEQ.incrementAndGet());
        tenant.setTenantSecret("types-secret");
        tenant.setStatus("ACTIVE");
        tenantMapper.insert(tenant);
        tenantId = tenant.getId();
        String name = tenant.getName();

        MvcResult r = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + name + "&client_secret=types-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"access_token\":\"");
        tenantToken = body.substring(i + 16, body.indexOf('"', i + 16));
    }

    private void insertType(String key, String name, int enabled) {
        jdbcTemplate.update("""
                INSERT INTO asts_task_type_config
                  (id, tenant_id, type_key, type_name, max_concurrency, exec_timeout_sec, max_retry_count,
                   is_enabled, is_deleted, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, 600, 0, ?, 0, NOW(), NOW())
                """, ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 2), tenantId, key, name, enabled);
    }

    @Test
    @DisplayName("类型列表: 只含本租户启用类型, 含 typeKey/name")
    void listEnabledTypes_scoped() throws Exception {
        insertType("fe-export", "前端联调导出", 1);
        insertType("fe-disabled", "已禁用类型", 0);

        mockMvc.perform(get("/api/v1/client/tasks/types")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.typeKey=='fe-export')].name").value("前端联调导出"))
                .andExpect(jsonPath("$.data[?(@.typeKey=='fe-disabled')]").isEmpty());
    }

    @Test
    @DisplayName("无任何类型 → 空数组")
    void listEnabledTypes_empty() throws Exception {
        mockMvc.perform(get("/api/v1/client/tasks/types")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
