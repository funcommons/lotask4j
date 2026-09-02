# 嵌入组件

ASTS 提供三种开箱即用的前端组件，业务系统用一行 iframe 即可获得任务列表/详情/进度卡片能力，无需自建任务 UI。

## 组件类型

| 组件 | 路径 | 能力 |
|------|------|------|
| 任务列表 | `/web-embed/task-list` | 展示本租户任务列表，支持分页、筛选、取消入口 |
| 任务详情 | `/web-embed/task-detail` | 单任务详情：步骤进度、结果、事件时间线 |
| 任务卡片 | `/web-embed/task-card` | 轻量卡片：单任务进度与状态，适合嵌在业务详情页 |

## 快速嵌入

### 第一步：创建嵌入配置（平台运营）

在管理台"嵌入配置"页（或 `POST /api/v1/admin/embed-config/configs`）创建配置，关键属性：

| 属性 | 说明 |
|------|------|
| `configKey` | 访问密钥（accessKey），即嵌入 URL 的免密凭证 |
| `componentType` | 限定组件：`task-list` / `task-detail` / `task-card`（必填，不可 all） |
| `tenantId` | 租户归属——决定组件能看哪个租户的数据 |
| `isEnabled` | 停用后 accessKey 立即失效 |

### 第二步：iframe 引入

```html
<iframe src="https://asts.example.com/web-embed/task-list?accessKey=ek-xxxxxxxx&taskId=可选"></iframe>
```

| 参数 | 必填 | 说明 |
|------|------|------|
| `accessKey` | 否 | 缺省为开放模式（只读、无租户 token，功能受限） |
| `taskId` | 否 | `task-detail` / `task-card` 指定展示的任务 |

## 认证机制：短期 Token

带合法 `accessKey` 访问时，服务端自动完成：

1. 校验 accessKey（存在、启用、组件类型匹配）；
2. 按**配置归属租户**签发短期 TENANT 型 JWT；
3. 种入 `ASTS_EMBED_TOKEN` Cookie（非 httpOnly，随组件页生效）并重定向到组件页。

组件前端读取该 Cookie，以 `Bearer` 调用 Client 只读接口——**业务系统全程无需传递租户密钥**。

> **说明**
> - `accessKey` 与组件类型绑定：`task-list` 的 key 不能用于 `task-card`（返回 `10106`）。
> - `accessKey` 相当于只读凭证，请仅嵌入到可信页面；泄露时在管理台停用配置即可立即失效。

## 前端自建方案

若不用 iframe，业务前端也可基于管理台同款组件源码（`frontend/` 仓 `--mode embed` 构建）集成，登录态走相同的短期 token Cookie 约定。

## 相关文档

- [Admin API：嵌入配置管理](../dev-guide/admin-api.md)
- [多租户隔离](../security/tenant-isolation.md)
