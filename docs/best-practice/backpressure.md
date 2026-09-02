# 背压与限流

ASTS 的过载保护分两层：**提交准入（背压）** 保护队列不被打爆，**接口限流**保护服务自身。

## 任务级背压（提交准入）

每个任务类型可配置两个水位（管理台类型配置）：

| 配置 | 含义 | 超限行为 |
|------|------|----------|
| `concurrencyLimit`（max_concurrency） | 该类型同时 `RUNNING` 的任务上限 | 新任务拒绝提交（`20006` 队列已满/并发已满） |
| `maxQueued`（max_queued） | 该类型 `PENDING` 排队上限 | 同上 |

```text
提交请求 ──► TaskSubmitGuard.checkOrThrow(type)
                │
                ├─ RUNNING 数 ≥ concurrencyLimit ──► 拒绝 (ApiException)
                ├─ PENDING 数 ≥ maxQueued ─────────► 拒绝 (ApiException)
                └─ 通过 ──► 入队
```

### 容量规划建议

```text
maxQueued ≥ 峰值提交速率 × 可接受排队时长
concurrencyLimit = Worker 实例数 × 单实例并发 × 0.8   (留 20% 余量应对重试风暴)
```

- 业务侧收到 `20006` 应**降速重试**（指数退避），而不是同步等待；
- 长期打满说明容量不足，优先扩 Worker 而不是调大水位。

## 接口限流（滑动窗口）

| 项 | 说明 |
|----|------|
| 维度 | 带 Token 按租户（claim `tenant_id`）；无 Token 按来源 IP |
| 算法 | Redis Lua 滑动窗口，规则由接口 `@RateLimit` 注解声明 |
| 白名单 | 环回地址（127.0.0.1/::1）默认放行 |
| 触发返回 | `code=10500`，message 含"请求过于频繁" |

### 客户端配合

1. 所有调用方实现统一的**429 风格退避**：收到 `10500` 后指数退避 + 抖动；
2. 批量任务提交走队列化发送（令牌桶），避免瞬时打满；
3. 监控各业务方的限流命中率，作为容量与配额谈判依据。

## 任务有效期兜底

即使背压失效（如堆积后 Worker 全体下线），任务还有最后一条保险：有效期（默认 7 天 / 类型 `timeoutSeconds`）。过期任务由 TaskReaper 判 `FAILED`（`PO_TIMEOUT`）或重试，队列不会无限膨胀。

## 相关文档

- [任务类型配置](../admin-guide/task-type.md)
- [任务提交与幂等](../user-guide/submit-idempotency.md)
