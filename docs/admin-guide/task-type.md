# 任务类型配置

任务类型是任务的业务分类，也是**容量与行为的控制面**：并发、超时、重试、步骤定义都挂在类型上。

## 配置项

| 字段 | 说明 | 示例 |
|------|------|------|
| `typeKey` | 类型标识（提交时 `type` 的值），租户内唯一 | `data_export` |
| `name` | 展示名 | 数据导出 |
| `concurrencyLimit` | 该类型同时 `RUNNING` 的任务上限（背压水位） | 5 |
| `maxQueued` | 该类型 `PENDING` 排队上限（背压水位） | 100 |
| `timeoutSeconds` | 任务有效期（秒）；未配置默认 7 天 | 3600 |
| `maxRetries` | 失败后自动重试次数（总尝试 = maxRetries + 1） | 2 |
| `isEnabled` | 停用后该类型不可提交（`20102`） | true |
| `stepsConfig` | 步骤定义（JSON 数组）：`key`/`name`/`weight`，用于全局进度折算 | 见下 |

## 步骤定义示例

```json
[
  {"key": "fetch",      "name": "拉取数据", "weight": 40},
  {"key": "transcode",  "name": "转码处理", "weight": 40},
  {"key": "upload",     "name": "上传结果", "weight": 20}
]
```

- Worker 上报 `currentStepKey` + `stepProgress` 后，服务端按权重折算全局进度：
  `全局进度 = (已完成权重 + 当前步权重 × 步进度%) / 总权重`
- 未定义步骤时全局进度 = Worker 上报的步骤进度本身。
- 权重全 0 时同样退化为直通模式。

## 创建/更新（upsert）

```bash
curl -X POST $BASE/api/v1/admin/types \
  -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" \
  -d '{"typeKey":"data_export","name":"数据导出","concurrencyLimit":5,
       "maxQueued":100,"timeoutSeconds":3600,"maxRetries":2,"isEnabled":true,
       "stepsConfig":[{"key":"fetch","name":"拉取","weight":40},
                      {"key":"upload","name":"上传","weight":60}]}'
```

同一 `typeKey` 重复提交即**整体更新**；`DELETE /types/{typeKey}` 为逻辑删除（已提交任务不受影响）。

## 参数调优速查

| 症状 | 调整 |
|------|------|
| 任务大面积 `20006` 队列已满 | 调大 `maxQueued` 或扩 Worker 后调大 `concurrencyLimit` |
| 任务频繁 `PO_TIMEOUT` 判死 | 调大 `timeoutSeconds`，或检查 Worker 是否卡死 |
| 失败任务人工重跑压力大 | 适当调大 `maxRetries`（注意幂等消费） |
| 前端进度跳变 | 检查步骤 `weight` 与实际耗时是否匹配 |

## 相关文档

- [背压与限流](../best-practice/backpressure.md)
- [进度上报与取消](../user-guide/progress-cancel.md)
- [Admin API](../dev-guide/admin-api.md)
