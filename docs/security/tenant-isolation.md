# 多租户隔离

ASTS 基于 framework4j-tenant 实现租户级隔离：**身份（Token claim）是唯一事实来源**，数据面全链路按租户收口。

## 三域守卫

接口按受众划分三个域，域与身份互斥：

| 域 | 接口前缀 | 准入身份 | 守卫 |
|----|----------|----------|------|
| 平台管理域 | `/api/v1/admin/**` | 仅平台身份（`tenant_id=0`） | `@PlatformDomain` |
| 租户业务域 | `/api/v1/client/**`、`/api/v1/worker/**` | 仅真实租户（`tenant_id>0`） | `@TenantDomain` |
| 开放嵌入域 | `/web-embed/**` | accessKey / 短期 token | 组件入口自校验 |

- 租户 Token 打管理域 → **403**；
- 平台 Token 打业务域 → **403**；
- 未携带 Token 访问受保护接口 → **401**。

## 数据面隔离

```text
Token claim (tenant_id)
      │ 只从 claim 取, 请求体同名字段一律忽略
      ▼
Service 层 (TenantIdentity.currentTenantId)
      ▼
Mapper/XML: 每条 SQL 显式 AND tenant_id = ?   (动态注入, 平台/后台任务传 null 跨租户)
      ▼
PostgreSQL RLS POLICY 兜底 (asts_task 等五张业务表)
```

| 防线 | 覆盖 |
|------|------|
| 提交 | 任务归属写 claim 租户 |
| 查询/取消 | 跨租户 ID 一律 `TASK_NOT_FOUND`（**不泄露存在性**） |
| Worker 轮询 | SQL 级租户过滤：A 租户 Worker 永远抢不到 B 租户任务 |
| Worker 上报 | 进度/结果 UPDATE 带租户条件，防跨租户篡改 |
| 幂等键 | （租户， 类型， 幂等键）命名空间，租户间互不可见 |
| RLS 兜底 | 应用层意外漏过滤时，数据库侧仍有最后一道闸 |

## Webhook / 嵌入的租户边界

- **Webhook 签名密钥** = 任务归属租户的密钥：租户只能用自己的密钥验出属于自己的回调；
- **嵌入组件** = accessKey 归属租户 → 短期 token 的 claim 即该租户，组件只能看到该租户数据。

## 平台身份的特殊性

`PLATFORM`（`tenant_id=0`）用于跨租户治理：任务列表/统计可全量查询（支持 `?tenant_id` 可选收窄）、替租户建任务类型、管理租户生命周期。平台凭据只应存在于运维通道（KMS/env），严禁下发业务方。

## 相关文档

- [认证与凭据](../dev-guide/auth.md)
- [密钥管理与轮换](credential-rotation.md)
- [Admin API](../dev-guide/admin-api.md)
