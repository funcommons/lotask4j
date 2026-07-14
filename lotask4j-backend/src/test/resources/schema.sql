-- H2 测试数据库初始化脚本
-- 创建 asts_task 表结构 (简化版，仅用于测试)
-- H2 PostgreSQL 模式会自动将 JSONB 类型映射为 JSON，TIMESTAMP 映射为 TIMESTAMP WITH TIME ZONE

DROP TABLE IF EXISTS asts_task CASCADE;

CREATE TABLE asts_task (
    id BIGINT PRIMARY KEY,
    task_type_key VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    worker_ip VARCHAR(50),
    callback_url VARCHAR(255),
    callback_status INT DEFAULT 0,
    progress INT DEFAULT 0,
    current_step_key VARCHAR(50),
    current_step_progress INT DEFAULT 0,
    steps_detail TEXT,
    payload TEXT,  -- H2中使用TEXT存储JSON
    result TEXT,   -- H2中使用TEXT存储JSON
    error_msg TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    expired_at TIMESTAMP WITH TIME ZONE,
    is_deleted INT DEFAULT 0,
    is_archived INT DEFAULT 0
);

-- 创建任务类型配置表
DROP TABLE IF EXISTS asts_task_type_config CASCADE;

CREATE TABLE asts_task_type_config (
    id BIGINT PRIMARY KEY,
    type_key VARCHAR(50) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL,
    name VARCHAR(100),
    description TEXT,
    max_concurrency INT,
    exec_timeout_sec INT,
    timeout_seconds INT,
    max_retry_count INT DEFAULT 3,
    is_enabled INT DEFAULT 1,
    is_deleted INT DEFAULT 0,
    steps_definition TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_task_status ON asts_task(status);
CREATE INDEX IF NOT EXISTS idx_task_type_key ON asts_task(task_type_key);
CREATE INDEX IF NOT EXISTS idx_task_is_deleted ON asts_task(is_deleted);
CREATE INDEX IF NOT EXISTS idx_task_is_archived ON asts_task(is_archived);
CREATE INDEX IF NOT EXISTS idx_task_created_at ON asts_task(created_at);

-- 插入测试数据
INSERT INTO asts_task_type_config (id, type_key, type_name, name, description, max_concurrency, exec_timeout_sec, timeout_seconds, max_retry_count, is_enabled, is_deleted, steps_definition, created_at, updated_at)
VALUES
    (1, 'data_export', '数据导出', 'data_export', '导出大量数据为Excel/CSV文件', 5, 3600, 3600, 3, 1, 0, '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'video_transcode', '视频转码', 'video_transcode', '将视频转换为不同格式', 3, 7200, 7200, 2, 1, 0, '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'report_generation', '报表生成', 'report_generation', '生成复杂的业务报表', 10, 1800, 1800, 3, 1, 0, '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
