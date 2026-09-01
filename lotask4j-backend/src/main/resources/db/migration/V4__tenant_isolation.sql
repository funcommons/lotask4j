-- V4__tenant_isolation.sql — 多租户隔离 (benefit4j 同款, framework4j-tenant 接入)
--
-- 1) asts_application → asts_tenant: 演进为 TenantEntity 契约表 (租户即接入方, 凭据无缝保留)
-- 2) 五张业务表加 tenant_id (本版 nullable; V5 在应用层收口完成后收紧 NOT NULL)
-- 3) 索引重建: tenant_id 打头 (tenant-tck T2 结构断言)
-- 4) PG RLS POLICY 就位 (ENABLE 不 FORCE, benefit4j 同款; owner 不受限, 应用层过滤是生命线)
--
-- asts_outbox 不加 tenant_id (跨租户事件总线, benefit4j UbmaOutbox 同款)。

-- ==================== 1) 租户表演进 ====================
ALTER TABLE asts_application RENAME TO asts_tenant;
ALTER TABLE asts_tenant RENAME COLUMN app_secret TO tenant_secret;

-- 契约列补齐 (TenantDdlGenerator PG 定义; 已有列保持现有宽度超集, tck 只断言列存在性)
ALTER TABLE asts_tenant ALTER COLUMN tenant_secret TYPE VARCHAR(256);
UPDATE asts_tenant SET tenant_secret = '' WHERE tenant_secret IS NULL;
ALTER TABLE asts_tenant ALTER COLUMN tenant_secret SET NOT NULL;
UPDATE asts_tenant SET name = COALESCE(name, ''), status = COALESCE(status, 'ACTIVE'),
                       is_deleted = COALESCE(is_deleted, 0);
ALTER TABLE asts_tenant ALTER COLUMN name SET NOT NULL;
ALTER TABLE asts_tenant ALTER COLUMN status SET NOT NULL;
ALTER TABLE asts_tenant ALTER COLUMN is_deleted SET NOT NULL;

ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS email                 VARCHAR(128);
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS channel               VARCHAR(16) NOT NULL DEFAULT 'OPS';
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS tenant_secret_prev    VARCHAR(256);
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS tenant_secret_prev_at TIMESTAMPTZ;
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS privileges            JSONB NOT NULL DEFAULT '{}';
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS config                JSONB NOT NULL DEFAULT '{}';
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS oem                   JSONB NOT NULL DEFAULT '{}';
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS ext                   JSONB NOT NULL DEFAULT '{}';
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS create_by             VARCHAR(64);
ALTER TABLE asts_tenant ADD COLUMN IF NOT EXISTS update_by             VARCHAR(64);

-- email 部分唯一索引 (tenant-tck T3)
CREATE UNIQUE INDEX IF NOT EXISTS uk_asts_tenant_email ON asts_tenant (email)
    WHERE is_deleted = 0 AND email IS NOT NULL;

-- 默认租户 (存量数据回填归属; secret 为占位明文, 上线后请经管理端 reset-secret 重置)
-- id=1 手工固定值, 与雪花 id 空间 (时间戳高位) 天然不冲突
INSERT INTO asts_tenant (id, name, channel, status, tenant_secret, created_at, updated_at)
SELECT 1, 'default', 'OPS', 'ACTIVE', 'lotask4j-default-tenant-placeholder', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM asts_tenant WHERE id = 1);

-- ==================== 2) 业务表加 tenant_id (nullable, V5 收紧) ====================
-- 分区父表加列自动传播到所有分区
ALTER TABLE asts_task ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE asts_task_execution_event ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE asts_task_type_config ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE asts_web_embed_config ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE asts_worker_node ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- 存量行回填默认租户
UPDATE asts_task SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE asts_task_execution_event SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE asts_task_type_config SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE asts_web_embed_config SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE asts_worker_node SET tenant_id = 1 WHERE tenant_id IS NULL;

-- ==================== 3) 索引重建 (tenant_id 打头) ====================
-- asts_task: poll 索引加租户前缀 (租户级 worker: WHERE tenant_id=? AND status='PENDING')
DROP INDEX IF EXISTS idx_asts_task_poll;
CREATE INDEX idx_asts_task_poll ON asts_task (tenant_id, status, priority DESC, created_at ASC);
-- 幂等唯一: 租户隔离命名空间 (跨租户允许同号) + 分区键保留
DROP INDEX IF EXISTS uq_asts_task_idem;
CREATE UNIQUE INDEX uq_asts_task_idem ON asts_task (tenant_id, task_type_key, idempotency_key, created_at)
    WHERE idempotency_key IS NOT NULL;

-- asts_task_execution_event
CREATE INDEX IF NOT EXISTS idx_atee_tenant ON asts_task_execution_event (tenant_id, created_at);

-- asts_task_type_config: type_key 全局唯一 → 租户内唯一 (各租户可定义同名类型)
ALTER TABLE asts_task_type_config DROP CONSTRAINT IF EXISTS asts_task_type_config_type_key_key;
DROP INDEX IF EXISTS uq_asts_type_config;
CREATE UNIQUE INDEX uq_asts_type_config ON asts_task_type_config (tenant_id, type_key);

-- asts_web_embed_config: config_key 保持全局唯一 (accessKey 是免登录入口, 解析时未知租户),
-- 另建租户打头普通索引满足租户过滤
CREATE INDEX IF NOT EXISTS idx_asts_weconfig_tenant ON asts_web_embed_config (tenant_id, config_key);

-- asts_worker_node: worker_id 是租户内标识 → 唯一性按租户
-- (防御性 DROP 旧名变体: 生产可能存在 worker_ip 维度的唯一索引, 与租户唯一键并存会误伤)
DROP INDEX IF EXISTS uq_asts_worker_node;
DROP INDEX IF EXISTS uq_asts_worker_node_ip_type;
DROP INDEX IF EXISTS asts_worker_node_worker_ip_task_type_key_key;
CREATE UNIQUE INDEX uq_asts_worker_node ON asts_worker_node (tenant_id, worker_id, task_type_key)
    WHERE is_deleted = 0;

-- ==================== 4) RLS POLICY (ENABLE 不 FORCE) ====================
-- 连接层未 SET app.tenant_id 时 current_setting(...,true) 返回 NULL → policy 不可达;
-- 表 owner 本身不受 RLS 限制 (benefit4j V1.4.1 同款: 结构就位, 应用层过滤是生命线)
ALTER TABLE asts_task ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON asts_task;
CREATE POLICY tenant_isolation ON asts_task
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);

ALTER TABLE asts_task_execution_event ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON asts_task_execution_event;
CREATE POLICY tenant_isolation ON asts_task_execution_event
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);

ALTER TABLE asts_task_type_config ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON asts_task_type_config;
CREATE POLICY tenant_isolation ON asts_task_type_config
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);

ALTER TABLE asts_web_embed_config ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON asts_web_embed_config;
CREATE POLICY tenant_isolation ON asts_web_embed_config
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);

ALTER TABLE asts_worker_node ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON asts_worker_node;
CREATE POLICY tenant_isolation ON asts_worker_node
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
