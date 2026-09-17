-- V1__baseline.sql — 存量 schema 基线 (2026-09-01)
-- 与 src/test/resources/schema-postgres.sql 对齐, 并补齐此前只靠手工建的
-- asts_worker_node / asts_web_embed_config (根治 dev 库缺表漂移)。
-- 存量库走 baseline-on-migrate 只打标记不执行; 空库全量执行。

-- ==================== 任务主表 ====================
CREATE TABLE asts_task (
    id                      BIGINT PRIMARY KEY,
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
    is_archived             SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_asts_task_poll ON asts_task (status, priority DESC, created_at ASC);
CREATE INDEX idx_asts_task_expired_at ON asts_task (expired_at);
CREATE INDEX idx_asts_task_lease_expire_at ON asts_task (lease_expire_at);
-- 幂等键唯一约束 (按 type_key 隔离, 避免跨任务类型 key 冲突)
CREATE UNIQUE INDEX uq_asts_task_idem ON asts_task (task_type_key, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ==================== 任务类型配置 ====================
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

-- ==================== 任务执行事件 (append-only audit) ====================
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

-- ==================== Worker 节点注册表 (本期补齐, 此前靠手工建表) ====================
CREATE TABLE asts_worker_node (
    id                    BIGINT PRIMARY KEY,
    worker_id             VARCHAR(64) NOT NULL,
    task_type_key         VARCHAR(64) NOT NULL,
    worker_ip             INET,
    worker_port           INT,
    hostname              VARCHAR(128),
    supported_task_types  VARCHAR(512),
    max_task_count        INT DEFAULT 10,
    current_task_count    INT DEFAULT 0,
    status                VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',   -- ONLINE/OFFLINE/BUSY
    last_heartbeat_at     TIMESTAMP WITH TIME ZONE,
    total_tasks_done      BIGINT DEFAULT 0,
    total_tasks_failed    BIGINT DEFAULT 0,
    registered_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted            SMALLINT NOT NULL DEFAULT 0
);

-- 联合唯一: 同一 worker 同一类型只一条注册记录 (逻辑删除排除)
CREATE UNIQUE INDEX uq_asts_worker_node ON asts_worker_node (worker_id, task_type_key)
    WHERE is_deleted = 0;

-- ==================== Web Embed 组件配置 (本期补齐, 此前靠手工建表) ====================
-- config 列为 JSON 字符串直存 (JacksonTypeHandler), 用 TEXT 而非 jsonb
CREATE TABLE asts_web_embed_config (
    id               BIGINT PRIMARY KEY,
    config_key       VARCHAR(64) NOT NULL UNIQUE,
    config_name      VARCHAR(100) NOT NULL,
    user_id          VARCHAR(64),
    is_open          SMALLINT NOT NULL DEFAULT 0,       -- 0=鉴权模式 1=开放模式
    callback_url     VARCHAR(512),
    config           TEXT NOT NULL DEFAULT '{}',
    component_type   VARCHAR(32) NOT NULL,               -- task-list / task-detail / task-card
    allowed_domains  VARCHAR(1024),
    is_enabled       SMALLINT NOT NULL DEFAULT 1,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       SMALLINT NOT NULL DEFAULT 0
);

-- ==================== 接入应用表 (client_credentials 凭据) ====================
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
