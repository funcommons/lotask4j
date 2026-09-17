package fun.commons.lotask4j.service.impl;

import com.alibaba.fastjson2.JSON;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.signature.util.SignatureUtil;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstsOutbox;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstsOutboxMapper;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
import fun.commons.lotask4j.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 回调服务实现 (outbox 模式)
 *
 * enqueueFinished: 终态事务内落 asts_outbox (payload/callback_url 快照);
 * deliver: 同步 HTTP POST, 2xx 即成功 — 状态迁移由 OutboxPublisher CAS 完成。
 *
 * R4 回调防伪造: 投递携带 HMAC 签名头 (密钥 = 归属租户 tenant_secret, AES 解密明文):
 *   X-ASTS-Event-Id  = outbox 行 id (接收方幂等去重键)
 *   X-ASTS-Timestamp = epoch millis (接收方校验 ±5min 防重放)
 *   X-ASTS-Signature = Base64(HmacSHA256(tenant_secret, timestamp + "\n" + rawBody))
 * 密钥轮换 (reset-secret) 后新投递用新钥; 接收方在 grace-hours 内双钥验签。
 * 无租户归属 (平台任务/存量行) 不签名 — 接收方推荐 verify-then-act 回查兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    @Autowired
    @Qualifier("webhookRestTemplate")
    private RestTemplate restTemplate;

    private final AstsOutboxMapper outboxMapper;

    private final SnowflakeDistributor snowflakeDistributor;

    private final AstTaskMapper taskMapper;

    private final AstsTenantMapper tenantMapper;

    @Override
    public void enqueueFinished(AstTask task) {
        if (task.getCallbackUrl() == null || task.getCallbackUrl().isEmpty()) {
            log.debug("Task {} has no callback URL, skipping webhook enqueue", task.getId());
            return;
        }

        Map<String, Object> webhookBody = new HashMap<>();
        webhookBody.put("event", "TASK_FINISHED");
        webhookBody.put("task_id", String.valueOf(task.getId()));
        webhookBody.put("type", task.getTaskTypeKey());
        webhookBody.put("status", task.getStatus());
        webhookBody.put("result", task.getResult());
        webhookBody.put("timestamp", System.currentTimeMillis());

        AstsOutbox event = new AstsOutbox();
        event.setId(snowflakeDistributor.nextId());
        event.setAggregateType("TASK");
        event.setAggregateId(task.getId());
        event.setEventType("TASK_FINISHED");
        event.setCallbackUrl(task.getCallbackUrl());
        event.setPayload(JSON.toJSONString(webhookBody));
        event.setStatus(AstsOutbox.STATUS_PENDING);
        event.setAttemptCount(0);
        event.setMaxAttempts(AstsOutbox.MAX_ATTEMPTS);
        event.setNextRetryAt(OffsetDateTime.now());
        event.setCreatedAt(OffsetDateTime.now());

        outboxMapper.insert(event);
        log.info("Webhook 入队: task={}, event={}, url={}",
                task.getId(), event.getId(), task.getCallbackUrl());
    }

    @Override
    public boolean deliver(AstsOutbox event) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            signCallback(event, headers);

            HttpEntity<String> request = new HttpEntity<>(event.getPayload(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    event.getCallbackUrl(), HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook 投递成功: event={}, task={}, url={}",
                        event.getId(), event.getAggregateId(), event.getCallbackUrl());
                return true;
            }
            log.warn("Webhook 投递非 2xx: event={}, status={}", event.getId(), response.getStatusCode());
            return false;
        } catch (Exception e) {
            log.warn("Webhook 投递异常: event={}, url={}, err={}",
                    event.getId(), event.getCallbackUrl(), e.getMessage());
            return false;
        }
    }

    /**
     * R4: 回调签名头 (无租户归属则不签 — 接收方 verify-then-act 兜底)
     */
    private void signCallback(AstsOutbox event, HttpHeaders headers) {
        try {
            AstTask task = taskMapper.selectById(event.getAggregateId());
            Long tenantId = task != null ? task.getTenantId() : null;
            if (tenantId == null) {
                return;
            }
            AstsTenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null || tenant.getTenantSecret() == null) {
                return;
            }
            String timestamp = String.valueOf(System.currentTimeMillis());
            // 密码学原语复用 SDK (HmacSHA256 + Base64); toSign = timestamp + "\n" + rawBody
            String signature = SignatureUtil.sign(tenant.getTenantSecret(),
                    timestamp + "\n" + event.getPayload());
            headers.set("X-ASTS-Event-Id", String.valueOf(event.getId()));
            headers.set("X-ASTS-Timestamp", timestamp);
            headers.set("X-ASTS-Signature", signature);
        } catch (Exception e) {
            // 签名失败不阻断投递 (降级为无签名; 密钥解密异常只告警)
            log.warn("Webhook 签名失败 (无签名投递): event={}, err={}", event.getId(), e.getMessage());
        }
    }
}
