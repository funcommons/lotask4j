package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.service.TaskService;
import fun.commons.lotask4j.service.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * client/worker 域鉴权收口守卫测试
 *
 * 收口设计: exclude 不再整域豁免 client/worker; TokenInterceptor 注解驱动 —
 * - client 域写端点 (POST /submit, POST /cancel) 方法级 @RequiresToken("client")
 * - client 域 GET (embed 依赖) 无注解 → 放行
 * - worker 域类级 @RequiresToken("worker") 全收
 *
 * token: 应用凭据换发 (scope=client / scope=worker 选 policy)。
 * 测试 context 恢复主配置 exclude (仅 auth 端点)。
 * 前置: 本地 PG + Redis :6379。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "framework4j.access-token.exclude-path-patterns[0]=/api/v1/auth/token",
})
@DisplayName("client/worker 域鉴权收口测试")
class ClientWorkerAuthGuardTest {

    @Autowired
    private MockMvc mockMvc;

    /** 真 HTTP (RANDOM_PORT 起 Tomcat) — MockMvc 伪 servlet 环境下 TokenInterceptor 进链后
     *  poll 的 @RequestBody 反序列化为空 (疑似 Mock 流交互), 真实环境不受影响, 故 worker 域
     *  用 TestRestTemplate 全链路验证 */
    @Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate rest;

    @Value("${local.server.port}")
    private int port;

    @MockBean
    private TaskService taskService;

    @MockBean
    private WorkerService workerService;

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private String clientToken;
    private String workerToken;

    @BeforeEach
    void setUp() throws Exception {
        when(taskService.submitTask(any())).thenReturn(555L);
        when(workerService.pollTask(any(), any())).thenReturn(null);

        // admin token → 建应用 → 应用凭据换 client / worker token
        String adminToken = mintToken("ADMIN", "lotask4j-admin-dev-secret", null);

        String appName = "guard-app-" + SEQ.incrementAndGet();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/applications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + appName + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String secret = extract(created.getResponse().getContentAsString(), "appSecret");

        clientToken = mintToken(appName, secret, "client");
        workerToken = mintToken(appName, secret, "worker");
        assertThat(clientToken).isNotBlank();
        assertThat(workerToken).isNotBlank();
    }

    private String mintToken(String clientId, String secret, String scope) throws Exception {
        String form = "grant_type=client_credentials&client_id=" + clientId
                + "&client_secret=" + secret + (scope != null ? "&scope=" + scope : "");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content(form))
                .andExpect(status().isOk())
                .andReturn();
        return extract(r.getResponse().getContentAsString(), "access_token");
    }

    private static String extract(String json, String key) {
        int i = json.indexOf("\"" + key + "\":\"");
        if (i < 0) return null;
        int start = i + key.length() + 4;
        return json.substring(start, json.indexOf('"', start));
    }

    @Test
    @DisplayName("client 写端点: 无 token 401; client token 放行")
    void clientWriteEndpoints() throws Exception {
        String body = "{\"type\":\"data_export\",\"payload\":{}}";

        // 无 token → 401
        mockMvc.perform(post("/api/v1/client/tasks/submit")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // client token → 200
        mockMvc.perform(post("/api/v1/client/tasks/submit")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("client GET 端点: 无 token 放行 (embed 兼容)")
    void clientGetOpenForEmbed() throws Exception {
        mockMvc.perform(get("/api/v1/client/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/client/tasks/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("worker 域: 无 token 401; worker token 放行 (真 HTTP 全链路)")
    void workerDomainGuarded() {
        String url = "http://localhost:" + port + "/api/v1/worker/tasks/poll";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 无 token → 401
        org.springframework.http.ResponseEntity<String> noToken = rest.postForEntity(url,
                new org.springframework.http.HttpEntity<>("{\"workerId\":\"w1\",\"taskType\":\"data_export\"}", headers), String.class);
        assertThat(noToken.getStatusCode().value()).isEqualTo(401);

        // worker token → 200 + body 正常绑定
        headers.add("Authorization", "Bearer " + workerToken);
        org.springframework.http.ResponseEntity<String> ok = rest.postForEntity(url,
                new org.springframework.http.HttpEntity<>("{\"workerId\":\"w1\",\"taskType\":\"data_export\"}", headers), String.class);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getBody()).contains("\"code\":0");
    }

    @Test
    @DisplayName("token type 不匹配: client token 不能过 worker 域")
    void tokenTypeMismatch() {
        String url = "http://localhost:" + port + "/api/v1/worker/tasks/poll";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + clientToken);
        org.springframework.http.ResponseEntity<String> resp = rest.postForEntity(url,
                new org.springframework.http.HttpEntity<>("{\"workerId\":\"w1\",\"taskType\":\"data_export\"}", headers), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
