# 监控与运维

## 管理台视图

| 页面 | 内容 |
|------|------|
| 统计概览 | 各状态任务数、总量趋势（`GET /api/v1/admin/stats/overview`） |
| 任务管理 | 当前/归档双视图，按状态/类型/时间筛选，事件时间线排障 |
| Worker 监控 | 在线节点（30 秒心跳窗口）、承载类型、租约数 |
| 租户管理 | 生命周期与密钥轮换 |

## 内置指标（Micrometer）

命名空间 `lotask4j.*`，可直接接入 Prometheus：

| 指标 | 类型 | 说明 |
|------|------|------|
| `lotask4j.tasks.submitted.total{type}` | Counter | 提交数 |
| `lotask4j.tasks.succeeded.total{type}` | Counter | 成功数 |
| `lotask4j.tasks.failed.total{type,error_code}` | Counter | 失败数（按错误码分桶，告警归因利器） |
| `lotask4j.tasks.canceled.total{type}` | Counter | 取消数 |
| `lotask4j.tasks.timeout.total{type}` | Counter | 超时数 |
| `lotask4j.tasks.retry.total{type}` | Counter | 重试数 |
| `lotask4j.task.queue_delay_seconds{type}` | Timer | 排队时延（started_at - created_at），P99 告警首选 |
| `lotask4j.task.exec_seconds{type}` | Timer | 执行时长 |
| `lotask4j.task.e2e_seconds{type}` | Timer | 端到端时长 |
| `lotask4j.workers.active` | Gauge | 活跃 Worker（持有租约数） |

**推荐告警规则（示例）**：

- `rate(lotask4j.tasks.failed.total[5m]) / rate(lotask4j.tasks.submitted.total[5m]) > 0.2` — 失败率突增
- `lotask4j_task_queue_delay_seconds{quantile=0.99} > 300` — 排队 P99 超 5 分钟（容量不足）
- `lotask4j.workers.active < 预期` — Worker 掉线

**开箱即用看板**：`deploy/grafana-dashboard.json` — Grafana → Dashboards → Import 导入，支持按任务类型筛选，含状态总览/失败归因/排队时延/Worker 活跃度 7 个面板。

## 健康与端点

| 端点 | 用途 |
|------|------|
| `/actuator/health` | 存活/就绪探针 |
| `/druid/index.html` | SQL 统计、慢查询（内网） |
| `/swagger-ui.html` | 接口自测 |

## 后台任务一览

| 任务 | 周期 | 职责 |
|------|------|------|
| `OutboxPublisher` | 5 秒 | Webhook 投递 + 指数退避重试 |
| `WorkerCleaner` | 1 分钟 | 清理离线 Worker 节点 |
| `TaskReaper` | 周期 | 回收租约过期任务（重试或判死 `PO_TIMEOUT`） |
| `TaskArchiver` | 每日 02:00 | 归档 7 天前终态任务 + 预建当/下月分区 |

## 排障手册

| 症状 | 检查路径 |
|------|----------|
| 任务卡 `PENDING` 不动 | 有无在线 Worker → 类型是否 `concurrencyLimit=0`/禁用 → 队列是否背压打满 |
| 任务卡 `RUNNING` 很久 | 事件时间线最后一条是否停更 → Worker 是否离线 → 等租约到期自动重派 |
| Webhook 收不到 | 任务详情 `callbackStatus`：0=未投/重试中，2=终态失败 → 查接收端可达性与验签 |
| 认证突然 401 | 租户是否被停用/reset → 密钥宽限期是否已过 |

## 本地联调与压测环境

仓库自带 docker compose 真实联调栈（PG 16 + Redis 7 + backend，Flyway 全量迁移）：

```bash
mvn -pl lotask4j-backend -am package -DskipTests   # 打包
docker compose up -d --build                        # 起环境 (backend :19080)
bash scripts/smoke.sh                               # 全链路冒烟 (24 断言)
python3 scripts/poll_bench.py --tasks 20 --workers 8  # poll 并发压测
docker compose down -v                              # 清理
```

- `scripts/smoke.sh`：Flyway 迁移、双身份认证、HMAC 签名提交、幂等、租户隔离、消费全链、Webhook 真实投递+验签、reset-secret 宽限、embed token、防爆破。
- `scripts/poll_bench.py`：并发抢占正确性（每任务恰好消费一次）+ 吞吐/时延基线。
- 发版前建议全量跑一遍 smoke；压测吞吐随 submit 限流（30/min/租户）配速。

## 相关文档

- [任务归档](../user-guide/archive.md)
- [错误码](../dev-guide/error-codes.md)
