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

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin Web Embed 配置管理集成测试 (平台域 /api/v1/admin/embed-config)
 *
 * 覆盖 7 端点: 分页列表 / 详情 / 创建 / 更新 / 逻辑删除 / 启停 / 预览 URL
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 恢复主配置 exclude (仅 auth 端点), 让平台域守卫生效
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("Admin Web Embed 配置管理测试")
class AdminWebEmbedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String platformToken;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet();
    }

    @BeforeEach
    void setUp() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=PLATFORM&client_secret=lotask4j-platform-dev-secret"))
                .andExpect(status().isOk())
                .andReturn();
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"access_token\":\"");
        platformToken = body.substring(i + 16, body.indexOf('"', i + 16));
        assertThat(platformToken).isNotBlank();
    }

    private String createConfig(String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/admin/embed-config/configs")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configKey":"%s","configName":"it-cfg","userId":"u-1",
                                 "componentType":"task-list","config":{"theme":"dark"}}
                                """.formatted(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"data\":");
        // data 为数字 id
        int start = body.indexOf(':', i) + 1;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
        return body.substring(start, end);
    }

    @Test
    @DisplayName("无 token → 401 (平台域门禁)")
    void noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/embed-config/configs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("创建 → 详情 → 列表含该配置 → 预览 URL (绝对地址)")
    void create_get_list_preview() throws Exception {
        String key = unique("ek-cgl");
        String id = createConfig(key);

        // 详情
        mockMvc.perform(get("/api/v1/admin/embed-config/configs/" + id)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configKey").value(key))
                .andExpect(jsonPath("$.data.embedUrl").value("/web-embed/task-list?accessKey=" + key));

        // 列表含该配置
        mockMvc.perform(get("/api/v1/admin/embed-config/configs")
                        .param("keyword", key)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].configKey").value(key));

        // 预览 URL: 默认用配置的 componentType; 显式指定 + taskId 覆盖
        mockMvc.perform(get("/api/v1/admin/embed-config/configs/" + id + "/preview-url")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url")
                        .value("http://localhost:9080/web-embed/task-list?accessKey=" + key));

        mockMvc.perform(get("/api/v1/admin/embed-config/configs/" + id + "/preview-url")
                        .param("componentType", "task-card")
                        .param("taskId", "12345")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url")
                        .value("http://localhost:9080/web-embed/task-card?accessKey=" + key + "&taskId=12345"));
    }

    @Test
    @DisplayName("详情不存在 → 200 + 10106 (IAE 业务校验契约)")
    void get_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/embed-config/configs/999999999")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10106));
    }

    @Test
    @DisplayName("更新 → 启停 → 逻辑删除后详情 404 (10106)")
    void update_toggle_delete() throws Exception {
        String key = unique("ek-utd");
        String id = createConfig(key);
        String newKey = key + "-x";

        // 更新
        mockMvc.perform(put("/api/v1/admin/embed-config/configs/" + id)
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configKey":"%s","configName":"it-cfg-2","userId":"u-2",
                                 "componentType":"task-card","isEnabled":0}
                                """.formatted(newKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 启停 (禁用后再启用)
        mockMvc.perform(post("/api/v1/admin/embed-config/configs/" + id + "/toggle")
                        .param("isEnabled", "1")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 逻辑删除
        mockMvc.perform(delete("/api/v1/admin/embed-config/configs/" + id)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 删除后详情不可见
        mockMvc.perform(get("/api/v1/admin/embed-config/configs/" + id)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10106));
    }

    @Test
    @DisplayName("创建重复 configKey → 200 + 10106")
    void create_duplicateKey() throws Exception {
        String key = unique("ek-dup");
        createConfig(key);

        mockMvc.perform(post("/api/v1/admin/embed-config/configs")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configKey":"%s","configName":"n","userId":"u",
                                 "componentType":"task-list"}
                                """.formatted(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10106));
    }
}
