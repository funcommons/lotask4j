package fun.commons.lotask4j.demo.client;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.framework4j.signature.util.SignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ASTS 客户端
 *
 * 演示如何调用 ASTS API (带 HMAC 签名的完整接入姿势)。
 *
 * 签名 (framework4j-signature 契约, 与 backend DbSecretProvider 对齐):
 *   toSign = [METHOD, path, timestamp(ms), nonce, MD5hex(body)].join("\n")
 *   X-Signature = Base64(HmacSHA256(secret, toSign))   ← SignatureUtil.sign(secret, toSign)
 *   Headers: X-Access-Key / X-Timestamp / X-Nonce / X-Signature
 *
 * 签名只圈写端点 (POST /submit 与 POST /cancel); GET 查询免签名。
 * body 需自行序列化为确定字节后签名与发送共用 (保证 MD5 一致)。
 */
@Slf4j
@Component
public class AstsClient {

    private final WebClient webClient;

    @Value("${asts.server.url:http://localhost:9080}")
    private String serverUrl;

    /** 租户凭据 (管理端创建租户时一次性返回; 默认租户 secret 见 V4 迁移说明, 用前请 reset) */
    @Value("${asts.client.access-key:default}")
    private String accessKey;

    @Value("${asts.client.secret:test-default-tenant-secret}")
    private String secret;

    /** client 域已收口 (@TenantDomain): 租户凭据换 TENANT 型 Bearer */
    private volatile String accessToken;

    private synchronized Mono<String> bearerToken() {
        if (accessToken != null) return Mono.just(accessToken);
        String form = "grant_type=client_credentials&client_id=" + accessKey
                + "&client_secret=" + secret;
        return webClient.post().uri(serverUrl + "/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve().bodyToMono(String.class)
                .map(body -> com.alibaba.fastjson2.JSONObject.parseObject(body)
                        .getJSONObject("data").getString("access_token"))
                .doOnNext(t -> accessToken = t);
    }

    public AstsClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * 提交任务 (HMAC 签名)
     */
    public Mono<TaskResponse> submitTask(String type, Map<String, Object> payload, int priority) {
        log.info("提交任务: type={}, priority={}", type, priority);

        Map<String, Object> request = new HashMap<>();
        request.put("type", type);
        request.put("payload", payload);
        request.put("priority", priority);
        // 自行序列化: 签名 MD5 与实际发送 body 必须是同一份字节
        String body = JSONObject.toJSONString(request);

        return bearerToken().flatMap(token -> webClient
            .post()
            .uri(serverUrl + "/api/v1/client/tasks/submit")
            .headers(h -> {
                signInto(h, "POST", "/api/v1/client/tasks/submit", body);
                h.setBearerAuth(token);
            })
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class))
            .map(response -> {
                JSONObject json = JSONObject.parseObject(response);
                JSONObject data = json.getJSONObject("data");
                // 服务端 SubmitTaskResponse 字段为 id (OpenID 串)
                return new TaskResponse(data.getString("id"));
            })
            .doOnError(error -> log.error("提交任务失败", error));
    }

    /**
     * 获取任务详情 (GET 免签名, 但需 Bearer 认证 — client GET 已收口 @TenantDomain)
     */
    public Mono<TaskDetail> getTaskDetail(String taskId) {
        log.info("获取任务详情: taskId={}", taskId);

        return bearerToken().flatMap(token -> webClient
            .get()
            .uri(serverUrl + "/api/v1/client/tasks/{taskId}", taskId)
            .headers(h -> h.setBearerAuth(token))
            .retrieve()
            .bodyToMono(String.class))
            .map(response -> {
                JSONObject json = JSONObject.parseObject(response);
                JSONObject data = json.getJSONObject("data");
                return new TaskDetail(
                    data.getString("id"),
                    data.getString("type"),
                    data.getString("status"),
                    data.getIntValue("progress"),
                    data.getString("currentStep"),
                    data.getJSONArray("stepsDetail"),
                    data.getJSONObject("result")
                );
            })
            .doOnError(error -> log.error("获取任务详情失败", error));
    }

    /**
     * 取消任务 (HMAC 签名, path 含任务 ID)
     */
    public Mono<Void> cancelTask(String taskId) {
        log.info("取消任务: taskId={}", taskId);
        String path = "/api/v1/client/tasks/" + taskId + "/cancel";

        return bearerToken().flatMap(token -> webClient
            .post()
            .uri(serverUrl + path)
            .headers(h -> {
                signInto(h, "POST", path, "");
                h.setBearerAuth(token);
            })
            .retrieve()
            .bodyToMono(Void.class))
            .doOnError(error -> log.error("取消任务失败", error));
    }

    /**
     * 轮询检查任务状态
     */
    public Mono<TaskDetail> pollTaskStatus(String taskId, long pollIntervalMs, long timeoutMs) {
        log.info("轮询任务状态: taskId={}, interval={}ms, timeout={}ms", taskId, pollIntervalMs, timeoutMs);

        long startTime = System.currentTimeMillis();

        return Mono.defer(() -> getTaskDetail(taskId)
            .flatMap(taskDetail -> {
                // 如果任务已完成，返回结果
                if ("SUCCESS".equals(taskDetail.status()) ||
                    "FAILED".equals(taskDetail.status()) ||
                    "CANCELLED".equals(taskDetail.status())) {
                    return Mono.just(taskDetail);
                }

                // 如果超时，返回当前状态
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    log.warn("任务轮询超时: taskId={}", taskId);
                    return Mono.just(taskDetail);
                }

                // 继续轮询
                log.debug("任务未完成，继续轮询: taskId={}, progress={}%", taskId, taskDetail.progress());
                return Mono.delay(java.time.Duration.ofMillis(pollIntervalMs))
                    .then(pollTaskStatus(taskId, pollIntervalMs, timeoutMs));
            }));
    }

    /** 构造签名头 (每次请求 nonce 唯一) */
    private void signInto(org.springframework.http.HttpHeaders headers, String method, String path, String body) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String bodyMd5 = md5Hex(body);
        String toSign = SignatureUtil.buildStringToSign(method, path, timestamp, nonce, bodyMd5);
        String signature = SignatureUtil.sign(secret, toSign);

        headers.add("X-Access-Key", accessKey);
        headers.add("X-Timestamp", timestamp);
        headers.add("X-Nonce", nonce);
        headers.add("X-Signature", signature);
    }

    private static String md5Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // DTO 类
    public record TaskResponse(String taskId) {}

    public record TaskDetail(
        String taskId,
        String type,
        String status,
        int progress,
        String currentStep,
        Object stepsDetail,
        Object result
    ) {}
}
