-- PostgreSQL 集成测试 schema (真实 PostgreSQL 类型)
-- 用于 Testcontainers + pollAndLockTask 并发抢占测试
-- P0 增强：乐观锁 (version)、execution_token、lease、attempt、idempotency_key
-- 分区: 与 Flyway V2 后生产结构对齐 (PARTITION BY RANGE created_at + default 兜底;
--       月分区由 TaskArchiver 运维逻辑建, 测试只保证 default 兜底即可写入)
-- tenant_id: V5 已在 Flyway 路径收紧 NOT NULL; 本测试 schema 有意保持 nullable,
--            以兼容未携带租户 claim 的既有 mapper 层用例 (生产由 claim + V5 双保险)

DROP TABLE IF EXISTS asts_task_execution_event CASCADE;
DROP TABLE IF EXISTS asts_outbox CASCADE;
DROP TABLE IF EXISTS asts_task CASCADE;
DROP TABLE IF EXISTS asts_task_type_config CASCADE;
DROP TABLE IF EXISTS asts_tenant CASCADE;
DROP TABLE IF EXISTS asts_worker_node CASCADE;
DROP TABLE IF EXISTS asts_web_embed_config CASCADE;

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
    tenant_id               BIGINT,                -- 租户归属 (V4 nullable, V5 收紧 NOT NULL)
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE asts_task_default PARTITION OF asts_task DEFAULT;

CREATE INDEX idx_asts_task_poll ON asts_task (tenant_id, status, priority DESC, created_at ASC);
CREATE INDEX idx_asts_task_expired_at ON asts_task (expired_at);
CREATE INDEX idx_asts_task_lease_expire_at ON asts_task (lease_expire_at);
-- 幂等键唯一约束 (租户隔离命名空间 + 分区键; 分区内唯一,
-- 跨月重复提交由应用层 findByIdempotencyKey 兜底)
CREATE UNIQUE INDEX uq_asts_task_idem ON asts_task (tenant_id, task_type_key, idempotency_key, created_at)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE asts_task_type_config (
    id                BIGINT PRIMARY KEY,
    type_key          VARCHAR(64) NOT NULL,        -- 租户内唯一 (见 uq_asts_type_config)
    tenant_id         BIGINT,                      -- V4 nullable, V5 收紧
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

CREATE UNIQUE INDEX uq_asts_type_config ON asts_task_type_config (tenant_id, type_key);

INSERT INTO asts_task_type_config (id, type_key, tenant_id, type_name, name, is_enabled)
VALUES (1, 'data_export', 1, '数据导出', 'data_export', 1);

-- P1-3 任务执行事件表 (append-only audit)
CREATE TABLE asts_task_execution_event (
    id              BIGINT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    tenant_id       BIGINT,                      -- V4 nullable, V5 收紧
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
CREATE INDEX idx_atee_tenant ON asts_task_execution_event(tenant_id, created_at);

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

-- 租户表 (framework4j-tenant TenantEntity 契约, V4 由 asts_application 演进)
CREATE TABLE asts_tenant (
    id                    BIGINT PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    description           TEXT,
    email                 VARCHAR(128),
    channel               VARCHAR(16) NOT NULL DEFAULT 'OPS',
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    tenant_secret         VARCHAR(256) NOT NULL,
    tenant_secret_prev    VARCHAR(256),
    tenant_secret_prev_at TIMESTAMPTZ,
    privileges            JSONB NOT NULL DEFAULT '{}',
    config                JSONB NOT NULL DEFAULT '{}',
    oem                   JSONB NOT NULL DEFAULT '{}',
    ext                   JSONB NOT NULL DEFAULT '{}',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by             VARCHAR(64),
    update_by             VARCHAR(64),
    is_deleted            SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_asts_tenant_email ON asts_tenant (email)
    WHERE is_deleted = 0 AND email IS NOT NULL;

-- 默认租户 (id=1; secret 为测试已知明文, C 阶段认证测试换 token 用)
INSERT INTO asts_tenant (id, name, channel, status, tenant_secret, created_at, updated_at)
VALUES (1, 'default', 'OPS', 'ACTIVE', 'test-default-tenant-secret', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ==================== Worker 节点注册表 (V1+V4 对齐) ====================
CREATE TABLE asts_worker_node (
    id                    BIGINT PRIMARY KEY,
    tenant_id             BIGINT,
    worker_id             VARCHAR(64) NOT NULL,
    task_type_key         VARCHAR(64) NOT NULL,
    worker_ip             INET,
    worker_port           INT,
    hostname              VARCHAR(128),
    supported_task_types  VARCHAR(512),
    max_task_count        INT DEFAULT 10,
    current_task_count    INT DEFAULT 0,
    status                VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    last_heartbeat_at     TIMESTAMP WITH TIME ZONE,
    total_tasks_done      BIGINT DEFAULT 0,
    total_tasks_failed    BIGINT DEFAULT 0,
    registered_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted            SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_asts_worker_node ON asts_worker_node (tenant_id, worker_id, task_type_key)
    WHERE is_deleted = 0;

-- ==================== Web Embed 组件配置 (V1+V4 对齐) ====================
CREATE TABLE asts_web_embed_config (
    id               BIGINT PRIMARY KEY,
    tenant_id        BIGINT,
    config_key       VARCHAR(64) NOT NULL UNIQUE,
    config_name      VARCHAR(100) NOT NULL,
    user_id          VARCHAR(64),
    is_open          SMALLINT NOT NULL DEFAULT 0,
    callback_url     VARCHAR(512),
    config           TEXT NOT NULL DEFAULT '{}',
    component_type   VARCHAR(32) NOT NULL,
    allowed_domains  VARCHAR(1024),
    is_enabled       SMALLINT NOT NULL DEFAULT 1,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_asts_weconfig_tenant ON asts_web_embed_config (tenant_id, config_key);
