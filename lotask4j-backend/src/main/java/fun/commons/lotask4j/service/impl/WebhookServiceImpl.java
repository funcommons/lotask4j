package fun.commons.lotask4j.service.impl;

import com.alibaba.fastjson2.JSON;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 回调服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    @Autowired
    @Qualifier("webhookRestTemplate")
    private RestTemplate restTemplate;

    private final AstTaskMapper taskMapper;

    @Override
    @Async("asyncExecutor")
    public void sendWebhookAsync(AstTask task) {
        if (task.getCallbackUrl() == null || task.getCallbackUrl().isEmpty()) {
            log.debug("Task {} has no callback URL, skipping webhook", task.getId());
            return;
        }

        log.info("Sending webhook for task: {} to {}", task.getId(), task.getCallbackUrl());

        try {
            // 构造 Webhook 请求体
            Map<String, Object> webhookBody = new HashMap<>();
            webhookBody.put("event", "TASK_FINISHED");
            webhookBody.put("task_id", task.getId()); // 注意: 实际发送时会自动转换为 OpenID 字符串
            webhookBody.put("type", task.getTaskTypeKey());
            webhookBody.put("status", task.getStatus());
            webhookBody.put("result", task.getResult());
            webhookBody.put("timestamp", Instant.now().toEpochMilli());

            // 发送 HTTP POST 请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(JSON.toJSONString(webhookBody), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    task.getCallbackUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook sent successfully for task: {}", task.getId());
                updateCallbackStatus(task.getId(), 1); // 1 = 发送成功
            } else {
                log.warn("Webhook failed for task: {}, status: {}", task.getId(), response.getStatusCode());
                updateCallbackStatus(task.getId(), 2); // 2 = 发送失败
            }

        } catch (Exception e) {
            log.error("Error sending webhook for task: {}", task.getId(), e);
            updateCallbackStatus(task.getId(), 2); // 2 = 发送失败

            // TODO: 实现重试机制(指数退避)
        }
    }

    /**
     * 更新回调状态
     */
    private void updateCallbackStatus(Long id, int status) {
        try {
            int updated = taskMapper.updateCallbackStatus(id, status);
            if (updated == 0) {
                log.warn("Failed to update callback status for task: {}, task may not exist", id);
            }
        } catch (Exception e) {
            log.error("Failed to update callback status for task: {}", id, e);
        }
    }
}
