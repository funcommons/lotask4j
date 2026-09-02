# Client API

面向业务系统的任务接口。前缀 `/api/v1/client/tasks`，需租户身份 Bearer Token。所有数据按 Token 的 `tenant_id` 隔离——跨租户访问一律 `TASK_NOT_FOUND`，不泄露存在性。

## 提交任务

```http
POST /api/v1/client/tasks/submit
```

请求体与字段说明见[任务提交与幂等](../user-guide/submit-idempotency.md)。

**响应** `code=0`：

```json
{"code": 0, "data": {"id": "Yk3xR9pQmZ2w"}}
```

**常见错误**：`20001` 提交失败 / `20006` 队列已满 / `20101` 类型未配置 / `20102` 类型已禁用。

## 查询任务详情

```http
GET /api/v1/client/tasks/{id}
```

**响应核心字段**：

```json
{
  "code": 0,
  "data": {
    "id": "Yk3xR9pQmZ2w",
    "type": "data_export",
    "typeName": "数据导出",
    "status": "RUNNING",
    "priority": 10,
    "progress": 60,
    "stepsDetail": [
      {"key": "fetch", "status": "done", "progress": 100},
      {"key": "write", "status": "processing", "progress": 20}
    ],
    "payload": {"query": "SELECT * FROM orders"},
    "result": null,
    "errorMsg": null,
    "lastErrorCode": null,
    "attempt": 1,
    "maxAttempts": 3,
    "callbackStatus": 0,
    "createdAt": "2026-09-02T10:00:00+08:00",
    "startedAt": "2026-09-02T10:00:03+08:00",
    "expiredAt": "2026-09-09T10:00:00+08:00"
  }
}
```

> **说明** `id` 为 OpenID 格式，请原样回传。`callbackStatus`：0 未回调 / 1 已成功 / 2 投递终态失败。

## 取消任务

```http
POST /api/v1/client/tasks/{id}/cancel
```

仅 `PENDING`/`RUNNING` 可取消；成功后任务进入 `CANCELLING`，待 Worker 上报 `CANCELLED` 确认。违反状态机报 `20401`。

## 任务列表

```http
GET /api/v1/client/tasks
```

| 参数 | 必填 | 说明 |
|------|------|------|
| `id` | 否 | 精确匹配（数字 ID） |
| `status` | 否 | 状态过滤 |
| `taskType` | 否 | 类型过滤 |
| `isArchived` | 否 | `true` 查归档（只读），缺省查当前任务 |
| `createdAtStart` / `createdAtEnd` | 否 | 创建时间范围（ISO-8601） |
| `page` / `pageSize` | 否 | 分页，默认 1 / 20 |

**响应**：`data` 为分页结构（`list/total/page/pageSize/totalPages`）。

## 任务统计

```http
GET /api/v1/client/tasks/stats
```

返回本租户当前任务的状态分布等汇总信息，适合做业务侧大盘。

## 调用示例（Java，摘自 lotask4j-demo）

```java
AstsClient client = new AstsClient("http://asts-host:9080",
        "order-service", "<tenantSecret>");   // 内部自动换/缓存 Token

SubmitTaskResponse resp = client.submit(SubmitTaskRequest.builder()
        .type("data_export")
        .payload(Map.of("query", "SELECT * FROM orders"))
        .priority(10)
        .idempotencyKey("export-20260902-001")
        .callbackUrl("https://biz.example.com/asts/callback")
        .build());

TaskDetailResponse detail = client.getTask(resp.getId());
```

## 相关文档

- [任务提交与幂等](../user-guide/submit-idempotency.md)
- [任务生命周期](../user-guide/lifecycle.md)
- [错误码](error-codes.md)
