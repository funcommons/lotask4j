package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.enums.BusinessCode;
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
 * AdminTaskController 端点集成测试 (平台域) — 类型配置 CRUD / 手动提交 / 任务列表 / 系统配置
 *
 * 补 AdminTaskControllerEventsTest (events/workers/stats 已覆盖) 之外的端点缺口。
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 恢复主配置 exclude (仅 auth 端点), 让平台域守卫生效
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("AdminTaskController 端点测试")
class AdminTaskControllerEndpointsTest {

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

    private String saveType(String typeKey) throws Exception {
        mockMvc.perform(post("/api/v1/admin/types")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeKey":"%s","tenantId":1,"name":"it-type","concurrencyLimit":2,
                                 "timeoutSeconds":600,"maxRetries":1,"isEnabled":true}
                                """.formatted(typeKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        return typeKey;
    }

    @Test
    @DisplayName("类型配置: 创建 → 再存 (更新路径) → 列表 → 详情 → 删除 → 详情 404")
    void typeConfigCrud() throws Exception {
        String typeKey = unique("it-type");

        saveType(typeKey);
        saveType(typeKey); // 第二次走更新分支

        // 列表包含
        mockMvc.perform(get("/api/v1/admin/types")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.typeKey=='" + typeKey + "')]").isNotEmpty());

        // 详情
        mockMvc.perform(get("/api/v1/admin/types/" + typeKey)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.typeKey").value(typeKey));

        // 删除
        mockMvc.perform(delete("/api/v1/admin/types/" + typeKey)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 删除后详情 → TASK_NOT_FOUND (ApiException 200 envelope)
        mockMvc.perform(get("/api/v1/admin/types/" + typeKey)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(BusinessCode.TASK_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("类型配置不存在 → TASK_NOT_FOUND envelope")
    void typeConfigNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/types/ghost-" + SEQ.incrementAndGet())
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(BusinessCode.TASK_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("管理员手动提交任务 → 返回任务 id; 任务列表可按状态/类型筛选")
    void adminSubmit_andTaskList() throws Exception {
        String typeKey = unique("it-submit");
        saveType(typeKey);

        // 手动提交 (不带 priority → 默认 100)
        mockMvc.perform(post("/api/v1/admin/tasks/submit")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + typeKey + "\",\"payload\":{\"x\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        // 带 priority 提交
        mockMvc.perform(post("/api/v1/admin/tasks/submit")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + typeKey + "\",\"payload\":{},\"priority\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 任务列表 (带筛选参数)
        mockMvc.perform(get("/api/v1/admin/tasks")
                        .param("status", "PENDING")
                        .param("type", typeKey)
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2));

        // 按不存在的 id 过滤 → total 0
        mockMvc.perform(get("/api/v1/admin/tasks")
                        .param("id", "999888777")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("系统配置端点返回系统信息")
    void systemConfig() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system/config")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.systemInfo").exists());
    }
}
