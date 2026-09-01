package fun.commons.lotask4j.service.impl;

import com.alibaba.fastjson2.JSON;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstsOutbox;
import fun.commons.lotask4j.mapper.AstsOutboxMapper;
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
}
