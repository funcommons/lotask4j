-- V5: 收紧业务表 tenant_id NOT NULL (V4 引入时 nullable 观察期结束)
--
-- 前置事实 (代码层保证):
--   * asts_task           唯一写入点 submitTask, tenantId 取自 Token claim (client>0 / admin=0)
--   * asts_worker_node    写入点 register/heartbeat, claim 必在
--   * asts_web_embed_config 管理台创建已要求 tenantId 必填 (embed token 的租户 claim 来源)
--   * asts_task_execution_event 保持 nullable: TaskReaper/平台路径记录事件时无租户 claim
--     (事件租户随任务, 审计语义由 task_id 关联补齐), 不在本轮收紧范围

-- 1. 存量回填: NULL 一律归入默认租户 (V4 已建 id=1)
UPDATE asts_task               SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE asts_task_type_config   SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE asts_web_embed_config   SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE asts_worker_node        SET tenant_id = 1 WHERE tenant_id IS NULL;

-- 2. 收紧 NOT NULL (分区父表 ALTER 自动传播到各分区)
ALTER TABLE asts_task             ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE asts_task_type_config ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE asts_web_embed_config ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE asts_worker_node      ALTER COLUMN tenant_id SET NOT NULL;
