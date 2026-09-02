# 租户管理

租户是 ASTS 的接入方身份：一份凭据、一个数据隔离边界、一组任务类型与 Worker。

## 创建租户

管理台"租户管理"→ 新建，或调用 API：

```bash
curl -X POST $BASE/api/v1/admin/tenants \
  -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"order-service","description":"订单中心","email":"owner@biz.com"}'
```

- `name` 全局唯一（也是登录 `client_id`）；
- 响应中的 `tenantSecret` **明文仅此一次**，请通过安全渠道（密码管家/加密消息）交付业务方；
- 落库为 AES-256-GCM 密文，任何列表/详情接口都不返回。

## 生命周期

```text
创建 (ACTIVE) ──► 停用 (INACTIVE→SUSPEND) ──► 启用 (ACTIVE) ──► 删除 (逻辑删)
```

| 操作 | 效果 |
|------|------|
| 停用 | 立即不可换新 Token；存量会话仍有效至过期，如需立即失效请先 reset-secret |
| 启用 | 恢复换 Token 能力 |
| 删除 | 逻辑删除；租户下任务数据保留（隔离语义不变） |

> **注意**
> 状态字段接受 `ACTIVE` / `INACTIVE`（旧值兼容，内部映射为框架契约的 `SUSPEND`），其他值报 `20107`。

## 密钥轮换

```bash
curl -X POST $BASE/api/v1/admin/tenants/{id}/reset-secret \
  -H "Authorization: Bearer $PLATFORM_TOKEN"
```

一次 reset 的完整语义（框架 `TenantSecretService` 委托）：

1. 生成新明文密钥（**响应一次性返回**）；
2. 旧密钥进入 **24 小时宽限期**（双版本并行，期间新旧密钥均可换 Token）；
3. **立即撤销该租户全部存量会话**（所有在途 Token 失效，需重新登录）；
4. 宽限期过后旧密钥彻底失效。

**轮换操作手册**：通知业务方 → reset → 业务方更新配置 → 验证新钥可换 Token → 宽限期内观察旧钥调用是否归零。

## 日常运维

| 任务 | 操作 |
|------|------|
| 查询租户 | 列表支持 `keyword`（名称模糊）/`status` 过滤 |
| 密钥疑似泄露 | 立即 reset-secret（撤会话 + 换钥一步完成） |
| 业务方下线 | 停用 → 观察无调用 → 删除 |
| 审计 | 换 Token 失败记录受防爆破保护；敏感操作走平台身份 |

## 相关文档

- [密钥管理与轮换](../security/credential-rotation.md)
- [Admin API](../dev-guide/admin-api.md)
