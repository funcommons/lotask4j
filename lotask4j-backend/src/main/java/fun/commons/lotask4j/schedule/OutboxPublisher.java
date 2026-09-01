package fun.commons.lotask4j.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.lotask4j.entity.AstsOutbox;
import fun.commons.lotask4j.mapper.AstsOutboxMapper;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Outbox 事件发布器 — webhook 可靠投递 (benefit4j OutboxPublisher 模式增强)
 *
 * 定时扫描 PENDING 且到期的事件 → 同步投递:
 * - 成功: CAS 标 SENT + sent_at (CAS 条件 status=PENDING 防多实例重复投递)
 * - 失败: attempt+1, next_retry_at = now + min(2^attempt, 3600)s 指数退避;
 *         超过 max_attempts 标 FAILED 终态 (不再重试)
 *
 * 事件行在任务终态事务内写入 (WebhookServiceImpl#enqueueFinished), 天然不丢。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final AstsOutboxMapper outboxMapper;
    private final AstTaskMapper taskMapper;
    private final WebhookService webhookService;

    /** 单次批量上限: 避免单轮拉取过多长时间占住调度线程 */
    private static final int BATCH_LIMIT = 100;

    /** 退避上限 (秒) */
    private static final long MAX_BACKOFF_SECONDS = 3600;

    @Scheduled(fixedDelayString = "${app.asts.outbox-interval:5000}")
    public void publishPending() {
        try {
            LambdaQueryWrapper<AstsOutbox> q = new LambdaQueryWrapper<>();
            q.eq(AstsOutbox::getStatus, AstsOutbox.STATUS_PENDING)
                    .le(AstsOutbox::getNextRetryAt, OffsetDateTime.now())
                    .orderByAsc(AstsOutbox::getCreatedAt)
                    .last("LIMIT " + BATCH_LIMIT);
            List<AstsOutbox> pending = outboxMapper.selectList(q);
            if (pending.isEmpty()) {
                return;
            }

            int sent = 0;
            for (AstsOutbox event : pending) {
                if (webhookService.deliver(event)) {
                    if (markSent(event)) sent++;
                } else {
                    markFailedAttempt(event);
                }
            }
            if (sent > 0) {
                log.info("[Outbox] 本轮投递成功 {} 条", sent);
            }
        } catch (Exception e) {
            log.error("[Outbox] 扫描投递失败", e);
        }
    }

    /** CAS: PENDING → SENT (多实例下仅一个成功); 同步任务行 callbackStatus=1 (前端展示) */
    private boolean markSent(AstsOutbox event) {
        LambdaUpdateWrapper<AstsOutbox> u = new LambdaUpdateWrapper<>();
        u.eq(AstsOutbox::getId, event.getId())
                .eq(AstsOutbox::getStatus, AstsOutbox.STATUS_PENDING)
                .set(AstsOutbox::getStatus, AstsOutbox.STATUS_SENT)
                .set(AstsOutbox::getSentAt, OffsetDateTime.now());
        boolean sent = outboxMapper.update(null, u) > 0;
        if (sent) {
            try {
                taskMapper.updateCallbackStatus(event.getAggregateId(), 1, null);
            } catch (Exception e) {
                log.warn("[Outbox] callbackStatus 同步失败: task={}", event.getAggregateId(), e);
            }
        }
        return sent;
    }

    /** 失败: attempt+1 + 指数退避; 超限 → FAILED 终态 */
    private void markFailedAttempt(AstsOutbox event) {
        int attempts = event.getAttemptCount() == null ? 0 : event.getAttemptCount();
        attempts++;

        if (event.getMaxAttempts() != null && attempts >= event.getMaxAttempts()) {
            LambdaUpdateWrapper<AstsOutbox> u = new LambdaUpdateWrapper<>();
            u.eq(AstsOutbox::getId, event.getId())
                    .eq(AstsOutbox::getStatus, AstsOutbox.STATUS_PENDING)
                    .set(AstsOutbox::getStatus, AstsOutbox.STATUS_FAILED)
                    .set(AstsOutbox::getAttemptCount, attempts);
            outboxMapper.update(null, u);
            try {
                taskMapper.updateCallbackStatus(event.getAggregateId(), 2, null);
            } catch (Exception ignored) {
                // 任务行可能已归档; 投递终态以 outbox 为准
            }
            log.warn("[Outbox] 事件 {} 达重试上限 {} 次, 标记 FAILED (task={})",
                    event.getId(), attempts, event.getAggregateId());
            return;
        }

        long backoff = Math.min((1L << attempts), MAX_BACKOFF_SECONDS);
        LambdaUpdateWrapper<AstsOutbox> u = new LambdaUpdateWrapper<>();
        u.eq(AstsOutbox::getId, event.getId())
                .eq(AstsOutbox::getStatus, AstsOutbox.STATUS_PENDING)
                .set(AstsOutbox::getAttemptCount, attempts)
                .set(AstsOutbox::getNextRetryAt, OffsetDateTime.now().plusSeconds(backoff));
        outboxMapper.update(null, u);
        log.debug("[Outbox] 事件 {} 第 {} 次投递失败, {}s 后重试", event.getId(), attempts, backoff);
    }
}
