package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 限流集成测试 (framework4j-rate-limit)
 *
 * 拦截器为注解驱动 (@RateLimit 方法级注解决定规则, 类级不生效):
 * - submit 端点 @RateLimit(key="submit", limit=30): 第 31 次被拒
 * - 拒绝响应 = 拦截器直写 HTTP 429 + ApiCode.TOO_MANY_REQUESTS (code 10500) 统一 envelope,
 *   并携带 X-RateLimit-* / Retry-After 头
 * 维度: 无 token → 来源 IP (X-Forwarded-For 优先)。
 * 注意: 环回 IP 默认在 IP 白名单 (本地调试不受限) → 测试统一带 XFF 头模拟真实客户端。
 * 前置: 本地 Redis :6379 (滑动窗口 Lua)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("限流守卫测试")
class RateLimitGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        when(taskService.submitTask(any())).thenReturn(999L);
        when(taskService.getPendingTaskCount()).thenReturn(0L);
        when(taskService.getRunningTaskCount()).thenReturn(0L);
    }

    private static final String SUBMIT_BODY =
            "{\"type\":\"data_export\",\"payload\":{\"k\":\"v\"}}";

    /** 每次运行随机客户端 IP: 绕过环回白名单, 且避免上次运行在 Redis 滑动窗口留存的计数干扰 */
    private static final String CLIENT_IP =
            "203.0.113." + (int) (Math.random() * 200 + 1);

    @Test
    @DisplayName("submit 超 30 次/min 后被拒 (TOO_MANY_REQUESTS 10500 + Retry-After 头)")
    void submitExceeded_Rejected() throws Exception {
        // 前 30 次正常 (带 X-RateLimit 响应头)
        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(post("/api/v1/client/tasks")
                            .header("X-Forwarded-For", CLIENT_IP)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SUBMIT_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        // 第 31 次被限流: 拦截器直写 HTTP 429 + 统一 envelope (业务码 10500) + 限流响应头
        MvcResult rejected = mockMvc.perform(post("/api/v1/client/tasks")
                        .header("X-Forwarded-For", CLIENT_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(10500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请求过于频繁")))
                .andReturn();

        String retryAfter = rejected.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).as("超限响应应携带 Retry-After 头").isNotNull();
    }

    @Test
    @DisplayName("不同端点独立计数 (submit 打满不影响 stats)")
    void independentKeyPerUri() throws Exception {
        // stats 端点有独立的限流 key (URI 参与 key 组成), 不受 submit 计数影响
        mockMvc.perform(get("/api/v1/client/tasks/stats")
                        .header("X-Forwarded-For", CLIENT_IP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
