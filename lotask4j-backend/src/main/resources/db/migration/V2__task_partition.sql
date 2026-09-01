-- V2__task_partition.sql — asts_task 转按月 RANGE 分区 (benefit4j ubma_consume 模式)
--
-- 动机: asts_task 为高增长追加型表 (任务完成后只读, TaskArchiver 每日归档),
-- 按月分区后历史月分区可独立归档/删除, 查询带 created_at 条件时分区裁剪。
--
-- 代价 (已评估接受):
--   * 主键 (id, created_at) — 分区表唯一约束必须含分区键; WHERE id= 查询由复合索引前缀覆盖
--   * uq_asts_task_idem 加 created_at 列 — 幂等唯一性退化为"分区内唯一";
--     跨月重复提交由既有 findByIdempotencyKey 应用层查询兜底 (提交路径已走该检查)
--
-- 新表 → 数据迁移 → DROP 旧表 → RENAME → 索引重建 (父表索引自动传播到所有分区)
-- 预建 2026-09/10/11 三个月分区 + DEFAULT 兜底; 后续由 TaskArchiver 每日滚动预建。

CREATE TABLE asts_task_new (
    id                      BIGINT NOT NULL,
    task_type_key           VARCHAR(64) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority                INT NOT NULL DEFAULT 0,
    attempt                 INT NOT NULL DEFAULT 1,
    max_attempts            INT NOT NULL DEFAULT 1,
    next_run_at             TIMESTAMP WITH TIME ZONE,
    version                 INT NOT NULL DEFAULT 0,
    execution_id            BIGINT,
    execution_token         BIGINT,
    worker_id               VARCHAR(64),
    worker_ip               INET,
    lease_expire_at         TIMESTAMP WITH TIME ZONE,
    requested_cancel_at     TIMESTAMP WITH TIME ZONE,
    last_error_code         VARCHAR(32),
    last_error_message      TEXT,
    idempotency_key         VARCHAR(128),
    callback_url            VARCHAR(512),
    callback_status         SMALLINT DEFAULT 0,
    progress                INT NOT NULL DEFAULT 0,
    current_step_key        VARCHAR(64),
    current_step_progress   INT DEFAULT 0,
    steps_detail            JSONB NOT NULL DEFAULT '[]',
    payload                 JSONB NOT NULL DEFAULT '{}',
    result                  JSONB NOT NULL DEFAULT '{}',
    error_msg               TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE,
    expired_at              TIMESTAMP WITH TIME ZONE,
    is_deleted              SMALLINT NOT NULL DEFAULT 0,
    is_archived             SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE asts_task_202609 PARTITION OF asts_task_new
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE asts_task_202610 PARTITION OF asts_task_new
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE asts_task_202611 PARTITION OF asts_task_new
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE asts_task_default PARTITION OF asts_task_new DEFAULT;

INSERT INTO asts_task_new SELECT * FROM asts_task;

DROP TABLE asts_task;
ALTER TABLE asts_task_new RENAME TO asts_task;

-- 索引重建 (父表上建, 自动传播到分区)
CREATE INDEX idx_asts_task_poll ON asts_task (status, priority DESC, created_at ASC);
CREATE INDEX idx_asts_task_expired_at ON asts_task (expired_at);
CREATE INDEX idx_asts_task_lease_expire_at ON asts_task (lease_expire_at);
-- 幂等唯一 (分区内唯一: 必须含分区键 created_at; 跨月由 findByIdempotencyKey 兜底)
CREATE UNIQUE INDEX uq_asts_task_idem ON asts_task (task_type_key, idempotency_key, created_at)
    WHERE idempotency_key IS NOT NULL;
