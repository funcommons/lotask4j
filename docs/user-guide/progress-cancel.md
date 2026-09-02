# 进度上报与取消

## 分步进度模型

Worker 通过 `POST /api/v1/worker/tasks/{id}/progress` 上报当前步骤：

```json
{
  "currentStepKey": "write",
  "stepProgress": 60,
  "executionToken": 123456789,
  "version": 3
}
```

### 全局进度如何计算

- **任务类型定义了步骤权重**（`stepsConfig`）时：

```text
全局进度 = (已完成步骤权重和 + 当前步骤权重 × 当前步进度%) / 总权重 × 100%
```

例：步骤 `fetch(40) → transcode(40) → upload(20)`，当前 `transcode` 步进度 50%：

```text
全局进度 = (40 + 40 × 50%) / 100 × 100% = 60%
```

- **未定义步骤**：全局进度 = 步骤进度，`currentStepKey` 仅作展示。
- 计算结果钳制在 0-100。

### 上报语义

| 项 | 说明 |
|----|------|
| 步骤更新 | `currentStepKey` 匹配已有步骤则更新该步；否则**追加**为新步骤 |
| 状态前置校验 | 仅 `RUNNING` / `CANCELLING` 可上报；否则报 `20409` |
| 并发安全 | `version` 乐观锁 + `executionToken` Fencing，任一不匹配即拒 |
| 租约续约 | 每次上报自动延长租约，Worker 无需单独心跳接口 |

> **注意**
> `CANCELLING` 状态仍可上报进度（便于前端展示），但 Worker 应尽快收尾并上报 `CANCELLED` 终态——业务逻辑在每个协作点（分片循环、批间）检查取消标记。

## 取消任务

### 发起取消（业务方）

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" $BASE/api/v1/client/tasks/<id>/cancel
```

- 仅 `PENDING` / `RUNNING` 可取消（终态与 `CANCELLING` 报 `20401`）。
- 成功后任务进入 `CANCELLING`：这只是**取消请求标记**，不会立刻打断执行。

### 响应取消（Worker）

Worker 在协作点收到 `CANCELLING`（上报进度时会被拒绝并提示状态）后，停止业务动作并上报终态：

```bash
curl -X POST $BASE/api/v1/worker/tasks/<id>/result \
  -H "Authorization: Bearer $WORKER_TOKEN" -H "Content-Type: application/json" \
  -d '{"status": "CANCELLED", "executionToken": 123456789, "version": 5}'
```

任务随即进入 `CANCELLED`，触发 Webhook（`status: CANCELLED`）。

### 取消的兜底

| 情形 | 结果 |
|------|------|
| Worker 正常确认 | `CANCELLED` |
| Worker 消失 / 长期不确认 | 租约过期 → TaskReaper 回收 → 按重试语义回 `PENDING` 或判 `FAILED` |
| `PENDING` 中取消 | 无 Worker 持有，轮询侧 CAS 保证不再被抢占 |

## 相关文档

- [Worker API](../dev-guide/worker-api.md)
- [任务生命周期](lifecycle.md)
