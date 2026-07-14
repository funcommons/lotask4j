# Worker API 接口列表

## 1. Poll 任务（抢占任务）

**POST** \`/api/v1/worker/tasks/poll\`

### 说明

- ✅ Worker 主动拉取待执行的任务
- ✅ 使用 \`SELECT FOR UPDATE SKIP LOCKED\` 避免锁竞争
- ✅ **自动更新心跳**：每次 Poll 自动 UPSERT worker_node 心跳记录
- ✅ 支持按优先级（PRIORITY）或 FIFO 策略拉取

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| taskType | String | ✅ | 任务类型键，如 \`data_export\` |
| strategy | String | ❌ | 拉取策略：\`PRIORITY\`（默认）或 \`FIFO\` |
| workerIp | String | ❌ | Worker IP 地址 |

### 请求示例

\`\`\`json
{
  "taskType": "data_export",
  "strategy": "PRIORITY",
  "workerIp": "192.168.1.100"
}
\`\`\`

### 响应示例（有任务）

\`\`\`json
{
  "code": 0,
  "message": "Success",
  "data": {
    "id": "YeirYkxHuQ",
    "type": "data_export",
    "typeName": "数据导出",
    "payload": {
      "query": "SELECT * FROM users WHERE status='active'",
      "format": "xlsx"
    },
    "priority": 80,
    "stepsDetail": [
      {
        "key": "init",
        "name": "初始化",
        "status": "pending",
        "progress": 0
      },
      {
        "key": "querying",
        "name": "数据查询",
        "status": "pending",
        "progress": 0
      },
      {
        "key": "writing",
        "name": "文件写入",
        "status": "pending",
        "progress": 0
      }
    ],
    "createdAt": "2025-12-02T10:00:00Z"
  }
}
\`\`\`

### 响应示例（无任务）

\`\`\`json
{
  "code": 0,
  "message": "Success",
  "data": null
}
\`\`\`

---

## 2. 查询任务状态（检测取消信号）

**GET** \`/api/v1/worker/tasks/{id}/status\`

### 说明

- ✅ Worker 定期调用此接口检测取消信号
- ✅ 如果 \`status === 'CANCELLING'\`，立即停止业务逻辑
- ✅ 建议检测频率：每 5-10 秒一次

### 响应示例

\`\`\`json
{
  "code": 0,
  "data": {
    "id": "YeirYkxHuQ",
    "status": "CANCELLING",  // Worker 检测到此状态应停止执行
    "progress": 65
  }
}
\`\`\`

---

## 3. 上报任务进度

**POST** \`/api/v1/worker/tasks/{id}/progress\`

### 说明

- ✅ Worker 在执行过程中实时上报进度
- ✅ 支持全局进度（0-100）和步骤进度
- ✅ 建议上报频率：每个步骤变化时或每 5-10 秒

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| progress | Integer | ✅ | 全局进度 (0-100) |
| currentStep | String | ✅ | 当前步骤名称 |
| currentStepKey | String | ✅ | 当前步骤键 |
| stepProgress | Integer | ❌ | 当前步骤进度 (0-100) |
| stepsDetail | Array | ❌ | 步骤详情数组 |

### 请求示例

\`\`\`json
{
  "progress": 65,
  "currentStep": "数据查询",
  "currentStepKey": "querying",
  "stepProgress": 80,
  "stepsDetail": [
    {
      "key": "init",
      "name": "初始化",
      "status": "finished",
      "detail": "准备完成",
      "progress": 100,
      "costMs": 500
    },
    {
      "key": "querying",
      "name": "数据查询",
      "status": "processing",
      "detail": "已查询 65000/100000 条记录",
      "progress": 80,
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
  ]
}
\`\`\`

---

## 4. 上报任务结果

**POST** \`/api/v1/worker/tasks/{id}/result\`

### 说明

- ✅ Worker 执行完成后上报最终结果
- ✅ \`status\` 可以是 \`SUCCESS\`、\`FAILED\` 或 \`CANCELLED\`
- ✅ 成功时必须提供 \`result\`，失败时必须提供 \`errorMsg\`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| status | String | ✅ | 最终状态：\`SUCCESS\`、\`FAILED\`、\`CANCELLED\` |
| result | Object | ❌ | 成功时的结果数据 |
| errorMsg | String | ❌ | 失败时的错误信息 |

### 请求示例（成功）

\`\`\`json
{
  "status": "SUCCESS",
  "result": {
    "fileUrl": "https://oss.example.com/export_20250102.xlsx",
    "fileSize": 5242880,
    "rows": 100000,
    "columns": 25,
    "costMs": 45000
  },
  "errorMsg": null
}
\`\`\`

### 请求示例（失败）

\`\`\`json
{
  "status": "FAILED",
  "result": null,
  "errorMsg": "数据库连接超时: Connection timeout after 30s"
}
\`\`\`

### 请求示例（取消）

\`\`\`json
{
  "status": "CANCELLED",
  "result": null,
  "errorMsg": "用户取消任务"
}
\`\`\`
