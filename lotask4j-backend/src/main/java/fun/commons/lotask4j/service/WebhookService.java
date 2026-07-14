package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.AstTask;

/**
 * Webhook 回调服务接口
 */
public interface WebhookService {

    /**
     * 异步发送 Webhook 回调
     * @param task 已完成的任务
     */
    void sendWebhookAsync(AstTask task);
}
