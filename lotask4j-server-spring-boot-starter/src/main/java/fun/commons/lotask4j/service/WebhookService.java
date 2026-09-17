package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstsOutbox;

/**
 * Webhook 回调服务接口 (outbox 模式)
 *
 * enqueueFinished: 任务终态事务内写 outbox 行 (与状态变更原子提交, 不丢);
 * deliver: 由 OutboxPublisher 调用的同步 HTTP 投递 (成功返回 true)。
 */
public interface WebhookService {

    /**
     * 任务终态入队 (调用方事务内; 无 callbackUrl 则跳过)
     *
     * @param task 已完成的任务 (含 callback_url 与终态字段)
     */
    void enqueueFinished(AstTask task);

    /**
     * 同步投递单条 outbox 事件 (不修改 outbox 状态, 由调用方 CAS)
     *
     * @param event 待投递事件
     * @return HTTP 2xx 即 true
     */
    boolean deliver(AstsOutbox event);
}
