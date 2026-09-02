# 实现第一个 Worker

Worker 是真正执行任务的进程：**注册心跳 → 轮询抢占 → 执行并上报进度 → 上报终态**。完整可运行示例见仓库 `lotask4j-demo` 模块（`SimpleWorkerExample`）。

## 前提条件

- 已完成[准备工作](prepare.md)，持有租户凭据。
- 已规划好要消费的任务类型（如 `data_export`）。

## Worker 主循环（伪代码）

```text
每 3~5 秒循环：
  1. POST /api/v1/worker/tasks/poll        → 抢占一个 PENDING 任务
  2. 抢到任务：
       a. 记录 executionToken 与 version（后续上报的凭据）
       b. 执行业务逻辑，每完成一步 → POST /tasks/{id}/progress
       c. 执行结束 → POST /tasks/{id}/result（终态）
  3. 没抢到：休眠后重试
```

## 第一步：轮询抢占

```bash
curl -X POST $BASE/api/v1/worker/tasks/poll \
  -H "Authorization: Bearer $WORKER_TOKEN" -H "Content-Type: application/json" \
  -d '{"taskType": "data_export", "strategy": "PRIORITY", "workerId": "wkr-node-001"}'
```

- `strategy`：`PRIORITY`（默认，优先级高者先出队）或 `FIFO`。
- 抢占成功返回任务详情与**执行令牌 `executionToken`**；队列为空返回空。
- 抢占即加租约（lease），Worker 需在租约内持续上报（每次上报自动续约）。

> **注意**
> 抢到的任务请立即持久化 `executionToken` 和 `version`——丢失后无法上报，任务会在租约过期后被系统回收重派。

## 第二步：上报进度

```bash
curl -X POST $BASE/api/v1/worker/tasks/<id>/progress \
  -H "Authorization: Bearer $WORKER_TOKEN" -H "Content-Type: application/json" \
  -d '{"currentStepKey": "write", "stepProgress": 60,
       "executionToken": 123456789, "version": 3}'
```

- `currentStepKey` + `stepProgress`：当前步骤标识与该步进度（0-100）。
- 全局进度由任务类型的步骤权重自动折算；未定义步骤时全局进度即步骤进度。
- 上报采用 `version` 乐观锁 + `executionToken` Fencing 双校验，并发冲突会被拒绝。

## 第三步：上报终态

```bash
# 成功
curl -X POST $BASE/api/v1/worker/tasks/<id>/result \
  -H "Authorization: Bearer $WORKER_TOKEN" -H "Content-Type: application/json" \
  -d '{"status": "SUCCESS", "result": {"fileUrl": "oss://bucket/orders.csv"},
       "executionToken": 123456789, "version": 4}'

# 失败（携带错误码便于告警归因）
curl -X POST $BASE/api/v1/worker/tasks/<id>/result \
  -H "Authorization: Bearer $WORKER_TOKEN" -H "Content-Type: application/json" \
  -d '{"status": "FAILED", "errorMsg": "OSS 写入超时",
       "lastErrorCode": "OSS_TIMEOUT", "lastErrorMessage": "putObject timeout 30s",
       "executionToken": 123456789, "version": 4}'
```

`status` 仅接受终态：`SUCCESS` / `FAILED` / `CANCELLED`。终态上报成功后任务即出队，若配置了 `callbackUrl`，服务端随后投递 Webhook。

## 心跳与保活

Worker 进程应随轮询自动维持心跳（服务端按租户注册 Worker 节点表）；停止轮询超过阈值后，管理台显示离线，节点被 `WorkerCleaner` 清理。Worker 宿主机重启后，未完成的任务会在租约过期后被 TaskReaper 回收重派。

## Java 参考

仓库 `lotask4j-demo/src/main/java/.../SimpleWorkerExample.java` 是一个完整的最小实现（Spring Boot + 定时轮询），可直接拷贝改造。

## 相关文档

- [Worker API](../dev-guide/worker-api.md)
- [Worker 开发规范](../best-practice/worker.md)
- [进度上报与取消](../user-guide/progress-cancel.md)
