package fun.commons.lotask4j.demo.client;

import com.alibaba.fastjson2.JSON;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Webhook 接收方示例 — R4 回调防伪造 (验签 + verify-then-act)
 *
 * lotask4j 投递契约 (WebhookServiceImpl.deliver):
 *   POST {callback_url}
 *   X-ASTS-Event-Id:  {outbox 行 id}          — 幂等去重键 (重试投递同 id)
 *   X-ASTS-Timestamp: {epoch millis}          — 校验 ±5min 防重放
 *   X-ASTS-Signature: Base64(HmacSHA256(tenant_secret, timestamp + "\n" + rawBody))
 *
 * 接收方安全动作 (按序):
 *   1. 验签: 用自己持有的租户密钥 (管理端创建租户时一次性下发) 复算比对 —
 *      密钥轮换宽限期 (24h) 内先试新钥再试旧钥
 *   2. 防重放: |now - X-ASTS-Timestamp| > 5min 拒收; Event-Id 已见过的拒收
 *   3. verify-then-act (推荐叠加): 高敏动作 (如退款) 拿 task_id 调
 *      GET /api/v1/client/tasks/{id} 回查终态后再执行, 不信任回调体本身
 */
@RestController
@RequestMapping("/demo/webhook")
public class WebhookReceiverExample {

    /** 接入方侧持有的租户密钥 (安全渠道下发, 不落代码库 — 示例仅为演示) */
    private static final String TENANT_SECRET = System.getenv().getOrDefault("ASTS_TENANT_SECRET", "");

    @PostMapping("/task-finished")
    public Map<String, Object> onTaskFinished(
            @RequestBody(required = false) String rawBody,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-ASTS-Event-Id", required = false) String eventId,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-ASTS-Timestamp", required = false) String timestamp,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-ASTS-Signature", required = false) String signature) {

        // 1. 必要头齐全
        if (eventId == null || timestamp == null || signature == null) {
            return Map.of("code", 401, "message", "missing signature headers");
        }

        // 2. 时间窗 ±5min (防重放)
        long skew = Math.abs(System.currentTimeMillis() - Long.parseLong(timestamp));
        if (skew > 5 * 60_000L) {
            return Map.of("code", 401, "message", "timestamp outside tolerance");
        }

        // 3. 验签 (常量时间比较; 轮换宽限期内可先新钥后旧钥各试一次)
        String expected = hmac(TENANT_SECRET, timestamp + "\n" + rawBody);
        if (!constantTimeEquals(expected, signature)) {
            return Map.of("code", 401, "message", "signature mismatch");
        }

        // 4. (推荐) verify-then-act: 高敏动作回查任务终态, 不信任回调体
        Map<?, ?> payload = JSON.parseObject(rawBody);
        String taskId = String.valueOf(payload.get("task_id"));
        String status = String.valueOf(payload.get("status"));
        // 例: if ("FAILED".equals(status)) taskClient.getTaskDetail(taskId) 核实后再退款...
        return Map.of("code", 0, "message", "accepted", "task_id", taskId, "status", status);
    }

    private static String hmac(String secret, String toSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmac failure", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
