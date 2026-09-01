package fun.commons.lotask4j.tenant;

import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.lotask4j.AstsApplication;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户隔离 Web 层端到端测试 (带真实 TENANT token 走完整拦截链)。
 *
 * 设计文档 §3.2 规约 3 的端到端缺口补齐: 合法租户 A 的 token 查询租户 B 的任务
 * → TASK_NOT_FOUND (不泄露存在性)。域互斥 403 由 ClientWorkerAuthGuardTest 覆盖。
 * 前置: 本地 PG (schema-postgres.sql 重建) + Redis :6379。
 */
@SpringBootTest(classes = AstsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 恢复主配置 exclude (仅 auth 端点): 让 TokenInterceptor 填充 claim 走完整链
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@Transactional
@DisplayName("租户隔离 Web 层测试 (真实 token)")
class TenantIsolationWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AstsTenantMapper tenantMapper;

    @Autowired
    private AstTaskMapper taskMapper;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private String tenantAToken;
    private Long taskBId;

    @BeforeEach
    void setUp() throws Exception {
        // 租户 A: 建租户 → 换 TENANT token (完整内置端点链)
        String nameA = "iso-web-a-" + SEQ.incrementAndGet();
        AstsTenant a = new AstsTenant();
        a.setName(nameA);
        a.setTenantSecret("secret-web-a");
        a.setStatus("ACTIVE");
        tenantMapper.insert(a);

        tenantAToken = mintToken(nameA, "secret-web-a");

        // 租户 B: 只造数据 (B 的任务, tenant_id = B)
        AstsTenant b = new AstsTenant();
        b.setName("iso-web-b-" + SEQ.incrementAndGet());
        b.setTenantSecret("secret-web-b");
        b.setStatus("ACTIVE");
        tenantMapper.insert(b);

        AstTask t = new AstTask();
        t.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 2));
        t.setTaskTypeKey("data_export");
        t.setStatus("PENDING");
        t.setPriority(0);
        t.setAttempt(1);
        t.setMaxAttempts(1);
        t.setVersion(0);
        t.setProgress(0);
        t.setIsDeleted(0);
        t.setTenantId(b.getId());
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        taskMapper.insertTask(t, "{}", "{}");
        taskBId = t.getId();
    }

    private String mintToken(String clientId, String secret) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"access_token\":\"");
        return body.substring(i + 16, body.indexOf('"', i + 16));
    }

    @Test
    @DisplayName("A 的 token 查 B 的任务 → TASK_NOT_FOUND (不泄露存在性)")
    void crossTenantReadReturnsNotFound() throws Exception {
        String openId = IdObfuscator.toOpenId(taskBId);
        mockMvc.perform(get("/api/v1/client/tasks/" + openId)
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20100));
    }

    @Test
    @DisplayName("A 的 token 查自己的域: 列表不含 B 的任务")
    void listIsScopedToOwnTenant() throws Exception {
        mockMvc.perform(get("/api/v1/client/tasks")
                        .header("Authorization", "Bearer " + tenantAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
