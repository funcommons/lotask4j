# Client API 接口列表

## 1. 提交任务

**POST** \`/api/v1/client/tasks\`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| type | String | ✅ | 任务类型键，如 \`data_export\` |
| payload | Object | ✅ | 任务入参 JSON 对象 |
| priority | Integer | ❌ | 优先级 (0-100)，默认 50 |
| callbackUrl | String | ❌ | Webhook 回调 URL |

### 请求示例

\`\`\`json
{
  "type": "data_export",
  "payload": {
    "query": "SELECT * FROM users WHERE created_at > '2025-01-01'",
    "format": "xlsx",
    "filters": {
      "status": "active"
    }
  },
  "priority": 80,
  "callbackUrl": "https://your-app.com/api/webhook/task-completed"
}
\`\`\`

### 响应示例

\`\`\`json
{
  "code": 0,
  "message": "Success",
  "data": {
    "id": "YeirYkxHuQ"
  },
  "timestamp": 1701234567890,
  "traceId": "trace-001"
}
\`\`\`

> **注意**: \`id\` 是 OpenID 格式的字符串（Base62 编码），用于对外隐藏真实的数据库 ID。

### 错误码

| 错误码 | 说明 |
|-------|------|
| 40001 | 参数校验失败 |
| 40002 | 任务类型不存在 |
| 40003 | 任务类型已禁用 |
| 50001 | 系统内部错误 |

---

## 2. 查询任务详情

**GET** \`/api/v1/client/tasks/{id}\`

### 路径参数

| 参数 | 类型 | 说明 |
|-----|------|------|
| id | String | 任务唯一标识 (OpenID 格式) |

### 响应示例

\`\`\`json
{
  "code": 0,
  "message": "Success",
  "data": {
    "id": "YeirYkxHuQ",
    "type": "data_export",
    "typeName": "数据导出",
    "status": "RUNNING",
    "progress": 65,
    "currentStep": "querying",
    "stepsDetail": [
      {
        "key": "init",
        "name": "初始化",
        "status": "finished",
        "detail": "准备导出环境",
        "progress": 100,
        "costMs": 500
      },
      {
        "key": "querying",
        "name": "数据查询",
        "status": "processing",
        "detail": "已查询 65000/100000 条记录",
        "progress": 65,
        "costMs": 12000
      },
      {
        "key": "writing",
        "name": "文件写入",
        "status": "pending",
        "detail": null,
        "progress": 0,
        "costMs": null
      }
    ],
    "payload": {
      "query": "SELECT * FROM users",
      "format": "xlsx"
    },
    "result": null,
    "errorMsg": null,
    "workerIp": "192.168.1.100",
    "createdAt": "2025-12-02T10:00:00Z",
    "updatedAt": "2025-12-02T10:02:15Z",
    "startedAt": "2025-12-02T10:00:01Z",
    "finishedAt": null
  }
}
\`\`\`

---

## 3. 取消任务

**POST** \`/api/v1/client/tasks/{id}/cancel\`

### 说明

- ⚠️ 取消是**协作式**的，需要 Worker 配合检测取消信号
- ✅ 取消请求**立即返回**，任务状态变为 \`CANCELLING\`
- ✅ Worker 检测到信号后停止执行，最终状态变为 \`CANCELLED\`
- ❌ 已完成的任务（SUCCESS/FAILED/CANCELLED）不允许取消

### 响应示例

\`\`\`json
{
  "code": 0,
  "message": "任务取消请求已发送",
  "data": null
}
\`\`\`

### 取消流程

\`\`\`
1. Client 发起取消 → status 变为 CANCELLING
2. Worker 定期检测 status
3. Worker 检测到 CANCELLING → 停止业务逻辑
4. Worker 上报结果 → status 变为 CANCELLED
\`\`\`

---

## 4. 查询任务列表

**GET** \`/api/v1/client/tasks\`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| id | String | ❌ | 任务 ID 筛选（模糊匹配） |
| status | String | ❌ | 任务状态筛选 |
| taskType | String | ❌ | 任务类型筛选 |
| isArchived | Boolean | ❌ | 是否查询归档任务 |
| page | Integer | ❌ | 页码，默认 1 |
| pageSize | Integer | ❌ | 每页数量，默认 20，最大 100 |

### 响应示例

\`\`\`json
{
  "code": 0,
  "message": "Success",
  "data": {
    "total": 150,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "id": "YeirYkxHuQ",
        "type": "data_export",
        "typeName": "数据导出",
        "status": "SUCCESS",
        "progress": 100,
        "createdAt": "2025-12-02T10:00:00Z",
        "finishedAt": "2025-12-02T10:05:30Z"
      }
    ]
  }
}
\`\`\`

---

## 5. 查询任务统计

**GET** \`/api/v1/client/tasks/stats\`

### 响应示例

\`\`\`json
{
  "code": 0,
  "message": "Success",
  "data": {
    "pending": 15,
    "running": 8,
    "success": 1250,
    "failed": 23,
    "cancelled": 5
  }
}
\`\`\`
