# Admin API

平台运营管理接口。前缀 `/api/v1/admin`，需**平台身份**（`client_id=PLATFORM`）Bearer Token；租户 Token 访问本域一律 403（域守卫）。

## 租户管理

| 接口 | 说明 |
|------|------|
| `POST /api/v1/admin/tenants` | 创建租户；响应含**一次性明文** `tenantSecret` |
| `GET /api/v1/admin/tenants` | 分页列表（`keyword`/`status`/`page`/`pageSize`），不含 secret |
| `GET /api/v1/admin/tenants/{id}` | 租户详情 |
| `POST /api/v1/admin/tenants/{id}/reset-secret` | 重置密钥：返回新明文，旧钥 24h 宽限，**立即撤销全部会话** |
| `POST /api/v1/admin/tenants/{id}/status` | 启停：`{"status":"ACTIVE"|"INACTIVE"}`（INACTIVE 内部映射 SUSPEND） |
| `DELETE /api/v1/admin/tenants/{id}` | 逻辑删除 |

详细流程与安全语义见[租户管理](../admin-guide/tenant.md)。

## 任务类型配置

| 接口 | 说明 |
|------|------|
| `POST /api/v1/admin/types` | 新增/更新类型配置（按 `typeKey` upsert） |
| `GET /api/v1/admin/types` | 全量类型列表 |
| `GET /api/v1/admin/types/{typeKey}` | 类型详情 |
| `DELETE /api/v1/admin/types/{typeKey}` | 逻辑删除 |

配置字段见[任务类型配置](../admin-guide/task-type.md)。

## 任务管理

| 接口 | 说明 |
|------|------|
| `GET /api/v1/admin/tasks` | 任务列表（`id`/`status`/`type`/`page`/`pageSize`，数据库分页） |
| `POST /api/v1/admin/tasks/submit` | 平台手动提交任务（补单/调试）；缺省优先级 100 |
| `GET /api/v1/admin/tasks/{id}/events` | 任务执行事件时间线（`limit` 1-1000，默认 100） |

> **说明** `/tasks/{id}/events` 的 `id` 为 OpenID 路径参数，格式非法返回 `10106`。

## Worker 监控

| 接口 | 说明 |
|------|------|
| `GET /api/v1/admin/workers` | 在线 Worker 列表（最近 30 秒有心跳的节点） |

## 统计与系统

| 接口 | 说明 |
|------|------|
| `GET /api/v1/admin/stats/overview` | 统计概览（各状态任务数等） |
| `GET /api/v1/admin/system/config` | 系统配置信息（JVM/数据库/Redis 等） |

## 嵌入配置管理

前缀 `/api/v1/admin/embed-config`：

| 接口 | 说明 |
|------|------|
| `GET /configs` | 分页列表（`keyword`/`isEnabled`） |
| `GET /configs/{id}` | 详情（含自动生成的相对嵌入 URL） |
| `POST /configs` | 创建（`configKey` 租户内唯一；`componentType` 必填三选一） |
| `PUT /configs/{id}` | 更新 |
| `DELETE /configs/{id}` | 逻辑删除 |
| `POST /configs/{id}/toggle?isEnabled=` | 启停（停用即 accessKey 失效） |
| `GET /configs/{id}/preview-url` | 生成嵌入预览 URL（`componentType`/`taskId` 可覆盖） |

## 调用示例

```bash
# 平台身份换 Token
PLATFORM_TOKEN=$(curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=PLATFORM&client_secret=<平台凭据>' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["access_token"])')

# 创建租户
curl -X POST $BASE/api/v1/admin/tenants \
  -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"order-service","description":"订单中心"}'

# 新增任务类型
curl -X POST $BASE/api/v1/admin/types \
  -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" \
  -d '{"typeKey":"data_export","name":"数据导出","concurrencyLimit":5,
       "timeoutSeconds":3600,"maxRetries":2,"isEnabled":true}'
```

## 相关文档

- [租户管理](../admin-guide/tenant.md)
- [任务类型配置](../admin-guide/task-type.md)
- [监控与运维](../admin-guide/monitoring.md)
