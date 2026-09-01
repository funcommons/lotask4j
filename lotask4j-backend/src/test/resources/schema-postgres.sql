-- PostgreSQL 集成测试 schema (真实 PostgreSQL 类型)
-- 用于 Testcontainers + pollAndLockTask 并发抢占测试
-- P0 增强：乐观锁 (version)、execution_token、lease、attempt、idempotency_key
-- 分区: 与 Flyway V2 后生产结构对齐 (PARTITION BY RANGE created_at + default 兜底;
--       月分区由 TaskArchiver 运维逻辑建, 测试只保证 default 兜底即可写入)

DROP TABLE IF EXISTS asts_task_execution_event CASCADE;
DROP TABLE IF EXISTS asts_outbox CASCADE;
DROP TABLE IF EXISTS asts_task CASCADE;
DROP TABLE IF EXISTS asts_task_type_config CASCADE;
DROP TABLE IF EXISTS asts_application CASCADE;

CREATE TABLE asts_task (
    id                      BIGINT NOT NULL,
    task_type_key           VARCHAR(64) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority                INT NOT NULL DEFAULT 0,
    -- 重试/attempt
    attempt                 INT NOT NULL DEFAULT 1,
    max_attempts            INT NOT NULL DEFAULT 1,
    next_run_at             TIMESTAMP WITH TIME ZONE,
    -- 乐观锁 (CAS)
    version                 INT NOT NULL DEFAULT 0,
    -- Execution fencing (Worker 派发态/上报态匹配)
    execution_id            BIGINT,
    execution_token         BIGINT,
    worker_id               VARCHAR(64),
    worker_ip               INET,
    lease_expire_at         TIMESTAMP WITH TIME ZONE,
    -- 取消/错误
    requested_cancel_at     TIMESTAMP WITH TIME ZONE,
    last_error_code         VARCHAR(32),
    last_error_message      TEXT,
    -- 幂等
    idempotency_key         VARCHAR(128),
    -- 既有字段
    callback_url            VARCHAR(512),
    callback_status         SMALLINT DEFAULT 0,
    progress                INT NOT NULL DEFAULT 0,
    current_step_key        VARCHAR(64),
    current_step_progress   INT DEFAULT 0,
    steps_detail            TEXT NOT NULL DEFAULT '[]',
    payload                 TEXT NOT NULL DEFAULT '{}',
    result                  TEXT NOT NULL DEFAULT '{}',
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

CREATE TABLE asts_task_default PARTITION OF asts_task DEFAULT;

CREATE INDEX idx_asts_task_poll ON asts_task (status, priority DESC, created_at ASC);
CREATE INDEX idx_asts_task_expired_at ON asts_task (expired_at);
CREATE INDEX idx_asts_task_lease_expire_at ON asts_task (lease_expire_at);
-- 幂等键唯一约束 (按 type_key 隔离; 分区表唯一索引必须含分区键 → 分区内唯一,
-- 跨月重复提交由应用层 findByIdempotencyKey 兜底)
CREATE UNIQUE INDEX uq_asts_task_idem ON asts_task (task_type_key, idempotency_key, created_at)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE asts_task_type_config (
    id                BIGINT PRIMARY KEY,
    type_key          VARCHAR(64) NOT NULL UNIQUE,
    type_name         VARCHAR(100) NOT NULL,
    name              VARCHAR(100),
    description       TEXT,
    max_concurrency   INT,
    max_queued        INT,                  -- P1-5: 队列深度上限
    exec_timeout_sec  INT,
    timeout_seconds   INT,
    max_retry_count   INT DEFAULT 3,
    is_enabled        SMALLINT DEFAULT 1,
    is_deleted        SMALLINT DEFAULT 0,
    steps_definition  TEXT,
    -- P0: 重试配置字段
    retry_initial_interval_sec INT DEFAULT 5,
    retry_multiplier            NUMERIC(4,2) DEFAULT 2.0,
    retry_max_interval_sec      INT DEFAULT 300,
    retry_jitter_ratio          NUMERIC(4,2) DEFAULT 0.2,
    default_lease_sec           INT DEFAULT 120,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO asts_task_type_config (id, type_key, type_name, name, is_enabled)
VALUES (1, 'data_export', '数据导出', 'data_export', 1);

-- P1-3 任务执行事件表 (append-only audit)
CREATE TABLE asts_task_execution_event (
    id              BIGINT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    execution_id    BIGINT,
    attempt         INT,
    event_type      VARCHAR(40) NOT NULL,
    old_status      VARCHAR(20),
    new_status      VARCHAR(20),
    worker_id       VARCHAR(64),
    trace_id        VARCHAR(64),
    operator        VARCHAR(64),
    detail          TEXT NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_atee_task_id ON asts_task_execution_event(task_id);
CREATE INDEX idx_atee_event_type ON asts_task_execution_event(event_type);
CREATE INDEX idx_atee_created_at ON asts_task_execution_event(created_at);

-- Webhook 投递 outbox (V3 对齐; 测试库平铺建表)
CREATE TABLE asts_outbox (
    id              BIGINT PRIMARY KEY,
    aggregate_type  VARCHAR(32)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    callback_url    VARCHAR(512) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempt_count   INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 8,
    next_retry_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_asts_outbox_pending ON asts_outbox (status, next_retry_at)
    WHERE status = 'PENDING';

-- 接入应用表 (client_credentials 凭据; 本期预留, 控制台走合成 ADMIN)
CREATE TABLE asts_application (
    id           BIGINT PRIMARY KEY,
    app_secret   VARCHAR(128),
    name         VARCHAR(100),
    description  TEXT,
    status       VARCHAR(20) DEFAULT 'ACTIVE',
    is_deleted   SMALLINT DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
