-- PostgreSQL 集成测试 schema (真实 PostgreSQL 类型)
-- 用于 Testcontainers + pollAndLockTask 并发抢占测试

DROP TABLE IF EXISTS asts_task CASCADE;
DROP TABLE IF EXISTS asts_task_type_config CASCADE;

CREATE TABLE asts_task (
    id                      BIGINT PRIMARY KEY,
    task_type_key           VARCHAR(64) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority                INT NOT NULL DEFAULT 0,
    retry_count             INT NOT NULL DEFAULT 0,
    worker_ip               INET,
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
    is_archived             SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_asts_task_poll ON asts_task (status, priority DESC, created_at ASC);
CREATE INDEX idx_asts_task_expired_at ON asts_task (expired_at);

CREATE TABLE asts_task_type_config (
    id                BIGINT PRIMARY KEY,
    type_key          VARCHAR(64) NOT NULL UNIQUE,
    type_name         VARCHAR(100) NOT NULL,
    name              VARCHAR(100),
    description       TEXT,
    max_concurrency   INT,
    exec_timeout_sec  INT,
    timeout_seconds   INT,
    max_retry_count   INT DEFAULT 3,
    is_enabled        SMALLINT DEFAULT 1,
    is_deleted        SMALLINT DEFAULT 0,
    steps_definition  TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO asts_task_type_config (id, type_key, type_name, name, is_enabled)
VALUES (1, 'data_export', '数据导出', 'data_export', 1);
