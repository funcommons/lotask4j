-- V3__outbox.sql — webhook 可靠投递 outbox 表
--
-- 任务终态与 outbox 行同事务写入 (不丢); OutboxPublisher 定时扫描投递,
-- 指数退避重试, 超过 max_attempts 进 FAILED 终态。
-- 本表在最小 outbox 结构上增加 attempt_count/next_retry_at,
-- 支撑真实 HTTP 投递与指数退避。

CREATE TABLE asts_outbox (
    id              BIGINT PRIMARY KEY,
    aggregate_type  VARCHAR(32)  NOT NULL,           -- 'TASK'
    aggregate_id    BIGINT       NOT NULL,           -- asts_task.id
    event_type      VARCHAR(32)  NOT NULL,           -- 'TASK_FINISHED'
    callback_url    VARCHAR(512) NOT NULL,           -- 投递目标 (快照, 任务删除后仍可投)
    payload         TEXT         NOT NULL,           -- 完整 webhook body (JSON 快照)
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',   -- PENDING / SENT / FAILED
    attempt_count   INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 8,
    next_retry_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         TIMESTAMP WITH TIME ZONE
);

-- 扫描索引: PENDING 且到期可投
CREATE INDEX idx_asts_outbox_pending ON asts_outbox (status, next_retry_at)
    WHERE status = 'PENDING';
