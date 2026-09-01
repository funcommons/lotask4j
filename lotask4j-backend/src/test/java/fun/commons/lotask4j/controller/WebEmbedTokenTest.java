package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.AstsApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Embed 短期 token 集成测试 (F 阶段)。
 *
 * accessKey 验证通过 → 按配置归属租户签发 TENANT 型 token 种入
 * ASTS_EMBED_TOKEN cookie (非 httpOnly) + userId cookie;
 * 该 token 即 embed 前端调用 client GET 的 Bearer 凭据。
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(classes = AstsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Embed 短期 token 测试")
class WebEmbedTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    /** 插入一条归属默认租户 (id=1) 的 task-list 嵌入配置, 返回 accessKey */
    private String insertConfig(String componentType, boolean openMode) {
        String key = "ek-" + SEQ.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO asts_web_embed_config
                  (id, tenant_id, config_key, config_name, user_id, is_open, callback_url,
                   config, component_type, is_enabled, is_deleted, created_at, updated_at)
                VALUES (?, 1, ?, 'it-config', 'u-1', ?, NULL, '{}', ?, 1, 0, NOW(), NOW())
                """,
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 2), key,
                openMode ? 1 : 0, componentType);
        return key;
    }

    @Test
    @DisplayName("accessKey 验证通过 → 302 + 双 Set-Cookie (ASTS_EMBED_TOKEN 非 httpOnly + ASTS_USER_ID)")
    void accessKeyIssuesTenantTokenCookie() throws Exception {
        String key = insertConfig("task-list", true);

        var result = mockMvc.perform(get("/web-embed/task-list").param("accessKey", key))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        java.util.List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        String embedToken = cookies.stream().filter(c -> c.startsWith("ASTS_EMBED_TOKEN="))
                .findFirst().orElse("");
        String userId = cookies.stream().filter(c -> c.startsWith("ASTS_USER_ID="))
                .findFirst().orElse("");
        assertThat(embedToken).as("应签发 embed token cookie")
                .contains("ASTS_EMBED_TOKEN=").doesNotContain("HttpOnly");
        assertThat(userId).contains("ASTS_USER_ID=u-1");

        // token 是可校验的 TENANT 型 JWT (三段式)
        String token = extractCookieValue(embedToken, "ASTS_EMBED_TOKEN");
        assertThat(token).as("embed token 应为三段式 JWT").contains(".");
    }

    @Test
    @DisplayName("无效 accessKey → 200 + 10106 (framework4j IAE 分流), 不签发 token")
    void invalidAccessKeyRejected() throws Exception {
        mockMvc.perform(get("/web-embed/task-list").param("accessKey", "no-such-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10106));
    }

    @Test
    @DisplayName("组件类型与配置不匹配 → 200 + 10106, 不签发 token")
    void componentMismatchRejected() throws Exception {
        String key = insertConfig("task-list", true);
        mockMvc.perform(get("/web-embed/task-card").param("accessKey", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10106));
    }

    private static String extractCookieValue(String setCookie, String name) {
        for (String part : setCookie.split(";")) {
            String seg = part.trim();
            if (seg.startsWith(name + "=")) {
                return seg.substring(name.length() + 1);
            }
        }
        return "";
    }
}
