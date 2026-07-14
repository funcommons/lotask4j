# ASTS Worker 工作端使用指南

**目标用户**: Worker 节点开发人员
**角色定位**: 任务执行端（Worker）
**接口前缀**: \`/api/v1/worker\`

---

## 🔧 什么是 Worker？

**Worker（工作端）** 是分布式任务执行节点，负责：

✅ **Poll 任务**: 通过 Poll 机制从 ASTS 拉取待执行任务
✅ **执行业务逻辑**: 根据任务类型执行具体的业务逻辑
✅ **上报进度**: 实时上报任务执行进度和步骤详情
✅ **检测取消信号**: 定期检查任务是否被取消
✅ **上报结果**: 任务完成后上报最终结果（成功/失败）
✅ **心跳保活**: 通过 Poll 自动更新心跳（无需单独心跳接口）

---

## 🎯 Worker 架构特点

### 1. **无状态设计**

- ✅ Worker 不直接访问数据库
- ✅ 所有操作通过 HTTP API 与 ASTS 交互
- ✅ 易于水平扩展，支持多实例部署

### 2. **Poll 机制**

- ✅ Worker 主动拉取任务（Pull 模式）
- ✅ 使用 \`SELECT FOR UPDATE SKIP LOCKED\` 避免任务竞争
- ✅ Poll 即心跳，无需单独维护心跳定时器

### 3. **协作式取消**

- ✅ Worker 定期检测取消信号
- ✅ 检测到 \`CANCELLING\` 状态后停止业务逻辑
- ✅ 清理资源后上报 \`CANCELLED\` 状态

---

## 🔄 Worker 执行流程

\`\`\`mermaid
sequenceDiagram
    participant Worker as Worker 节点
    participant API as ASTS API
    participant DB as PostgreSQL

    loop 每 N 秒 Poll
        Worker->>API: POST /worker/tasks/poll
        API->>DB: SELECT FOR UPDATE SKIP LOCKED + UPSERT worker_node

        alt 有可用任务
            DB-->>API: 返回任务 status=RUNNING
            API-->>Worker: 返回任务详情

            Note over Worker: 开始执行业务逻辑

            loop 执行过程中
                Worker->>Worker: 执行业务逻辑
                Worker->>API: POST /tasks/progress 上报进度
                Worker->>API: GET /tasks/status 检测取消信号
            end

            Worker->>API: POST /tasks/result SUCCESS
            API->>DB: UPDATE status=SUCCESS

        else 无可用任务
            DB-->>API: 返回空
            API-->>Worker: data=null
        end
    end
\`\`\`

---

## 🚀 快速开始

### 1. Poll 任务

\`\`\`bash
curl -X POST http://localhost:8080/api/v1/worker/tasks/poll \\
  -H "Content-Type: application/json" \\
  -d '{
    "taskType": "data_export",
    "strategy": "PRIORITY",
    "workerIp": "192.168.1.100"
  }'
\`\`\`

**响应**（有任务）:
\`\`\`json
{
  "code": 0,
  "data": {
    "id": "YeirYkxHuQ",
    "type": "data_export",
    "payload": {
      "query": "SELECT * FROM users",
      "format": "xlsx"
    },
    "stepsDetail": [ /* 步骤定义 */ ]
  }
}
\`\`\`

> **注意**: \`id\` 是 OpenID 格式的字符串（Base62 编码），用于对外隐藏真实的数据库 ID。

**响应**（无任务）:
\`\`\`json
{
  "code": 0,
  "data": null
}
\`\`\`

### 2. 上报进度

\`\`\`bash
curl -X POST http://localhost:8080/api/v1/worker/tasks/YeirYkxHuQ/progress \\
  -H "Content-Type: application/json" \\
  -d '{
    "progress": 65,
    "currentStep": "querying",
    "currentStepKey": "querying",
    "stepProgress": 80,
    "stepsDetail": [
      {
        "key": "querying",
        "name": "数据查询",
        "status": "processing",
        "detail": "已查询 65000/100000 条记录",
        "progress": 80
      }
    ]
  }'
\`\`\`

### 3. 上报结果

\`\`\`bash
curl -X POST http://localhost:8080/api/v1/worker/tasks/abc123/result \\
  -H "Content-Type: application/json" \\
  -d '{
    "status": "SUCCESS",
    "result": {
      "fileUrl": "https://oss.example.com/export_20250102.xlsx",
      "fileSize": 5242880,
      "rows": 100000
    },
    "errorMsg": null
  }'
\`\`\`
