package fun.commons.lotask4j.controller;

import fun.commons.framework4j.signature.util.SignatureUtil;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HMAC 签名守卫集成测试 (framework4j-signature, 只圈 client 域写端点)
 *
 * 契约 (源码 SignatureUtil/SignatureService 确认, 与前端 signature.ts 一致):
 *   toSign = [METHOD, path, timestamp(ms), nonce, MD5hex(body)].join("\n")
 *   X-Signature = Base64(HmacSHA256(secret, toSign))   ← 注意 SDK sign(secret, toSign) secret 在前
 * bodyMd5 覆盖请求体依赖 SignatureBodyCachingFilter 在拦截器前 cacheBody()。
 *
 * 测试 context 单独开启 signature.enabled (application-test.yml 默认关闭)。
 * 前置: 本地 PG + Redis :6379 (nonce SETNX)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "framework4j.signature.enabled=true",
})
@DisplayName("HMAC 签名守卫测试")
class SignatureGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    private static final String SUBMIT_PATH = "/api/v1/client/tasks/submit";
    private static final String SUBMIT_BODY = "{\"type\":\"data_export\",\"payload\":{\"k\":\"v\"}}";
    private static final String PLATFORM_SECRET = "lotask4j-platform-dev-secret";

    /** 签名头四元组 */
    private record SigHeaders(String accessKey, String timestamp, String nonce, String signature) {}

    @BeforeEach
    void setUp() {
        when(taskService.submitTask(any())).thenReturn(777L);
        when(taskService.getPendingTaskCount()).thenReturn(0L);
        when(taskService.getRunningTaskCount()).thenReturn(0L);
    }

    private static SigHeaders sign(String secret, String fixedNonce) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = fixedNonce != null ? fixedNonce : UUID.randomUUID().toString();
        String bodyMd5 = md5Hex(SUBMIT_BODY);
        String toSign = SignatureUtil.buildStringToSign("POST", SUBMIT_PATH, timestamp, nonce, bodyMd5);
        return new SigHeaders("PLATFORM", timestamp, nonce, SignatureUtil.sign(secret, toSign));
    }

    private static String md5Hex(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 带签名头的 submit 构造器 */
    private MockHttpServletRequestBuilder signedPost(SigHeaders h) {
        return post(SUBMIT_PATH)
                .header("X-Access-Key", h.accessKey())
                .header("X-Timestamp", h.timestamp())
                .header("X-Nonce", h.nonce())
                .header("X-Signature", h.signature())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SUBMIT_BODY);
    }

    @Test
    @DisplayName("无签名头拒; 合法签名放行; 错签名拒; nonce 重放拒; GET 列表免签名")
    void signatureFlow() throws Exception {
        // 1. 无签名头 → 401 (签名头缺失)
        mockMvc.perform(post(SUBMIT_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(SUBMIT_BODY))
                .andExpect(status().isUnauthorized());

        // 2. 合法签名 (md5(body) 覆盖请求体) → 200
        SigHeaders ok = sign(PLATFORM_SECRET, null);
        mockMvc.perform(signedPost(ok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 错误 secret 签的签名 → 401 (签名值不匹配 10302)
        SigHeaders bad = sign("wrong-secret", null);
        mockMvc.perform(signedPost(bad))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10302));

        // 4. nonce 重放 (同 nonce 第二次) → 401
        SigHeaders replay = sign(PLATFORM_SECRET, ok.nonce());
        mockMvc.perform(signedPost(replay))
                .andExpect(status().isUnauthorized());

        // 5. 超前时间戳 (超出 ±5min 容差) → 401
        SigHeaders future = new SigHeaders("PLATFORM",
                String.valueOf(System.currentTimeMillis() + 600_000),
                UUID.randomUUID().toString(), Base64.getEncoder().encodeToString("x".getBytes()));
        mockMvc.perform(signedPost(future))
                .andExpect(status().isUnauthorized());

        // 6. GET 列表 (embed 依赖, 根路径不在 path-patterns) → 免签名放行
        mockMvc.perform(get("/api/v1/client/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
