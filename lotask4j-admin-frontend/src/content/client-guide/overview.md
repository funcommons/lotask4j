# ASTS 客户端使用指南

**目标用户**: 业务系统后端开发人员
**角色定位**: 任务发布端（Client）
**接口前缀**: \`/api/v1/client\`

---

## 📋 什么是客户端？

**客户端（Client）** 是指业务系统的后端服务，负责：

✅ **提交任务**: 将长时间运行的任务提交到 ASTS
✅ **查询状态**: 轮询任务执行状态和进度
✅ **取消任务**: 发送取消信号，请求 Worker 停止执行
✅ **接收回调**: 通过 Webhook 接收任务完成通知（可选）

---

## 🎯 典型使用场景

| 场景 | 描述 | 适用任务类型 |
|-----|------|-------------|
| **数据导出** | 用户请求导出大量数据到 Excel 文件 | \`data_export\` |
| **视频转码** | 用户上传视频，需要转码为多种格式 | \`video_transcode\` |
| **批量处理** | 批量发送邮件、生成报表 | \`batch_email\` |
| **AI 模型推理** | 图像识别、文本分析等耗时推理任务 | \`ai_inference\` |

---

## 🔄 任务生命周期

\`\`\`mermaid
graph LR
    A[客户端提交] --> B[PENDING 待处理]
    B --> C[RUNNING 执行中]
    C --> D{执行结果}
    D -->|成功| E[SUCCESS 完成]
    D -->|失败| F[FAILED 失败]
    D -->|取消| G[CANCELLED 已取消]

    style A fill:#4CAF50,color:#fff
    style E fill:#2196F3,color:#fff
    style F fill:#FF5722,color:#fff
    style G fill:#FF9800,color:#fff
\`\`\`

---

## 📡 统一响应格式

所有 Client API 返回以下格式：

\`\`\`json
{
  "code": 0,                    // 0=成功, 非0=失败
  "message": "Success",         // 响应消息
  "data": { /* 业务数据 */ },   // 响应数据
  "timestamp": 1701234567890,   // 响应时间戳
  "traceId": "abc123..."        // 链路追踪ID
}
\`\`\`

---

## 🚀 快速开始

### 1. 提交任务

\`\`\`bash
curl -X POST http://localhost:8080/api/v1/client/tasks \\
  -H "Content-Type: application/json" \\
  -d '{
    "type": "data_export",
    "payload": {
      "query": "SELECT * FROM users",
      "format": "xlsx"
    },
    "priority": 50,
    "callbackUrl": "https://your-app.com/webhook"
  }'
\`\`\`

**响应**:
\`\`\`json
{
  "code": 0,
  "message": "Success",
  "data": {
    "id": "YeirYkxHuQ"
  }
}
\`\`\`

> **注意**: \`id\` 是 OpenID 格式的字符串（Base62 编码），用于对外隐藏真实的数据库 ID。

### 2. 查询任务状态

\`\`\`bash
curl http://localhost:8080/api/v1/client/tasks/YeirYkxHuQ
\`\`\`

### 3. 轮询等待完成

使用指数退避策略轮询：

\`\`\`typescript
async function waitForTask(id: string) {
  let delay = 1000 // 初始 1 秒
  const maxDelay = 30000 // 最大 30 秒

  while (true) {
    const task = await getTaskStatus(id)

    // 检查终态
    if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(task.status)) {
      return task
    }

    // 等待后重试
    await sleep(delay)
    delay = Math.min(delay * 1.5, maxDelay)
  }
}
\`\`\`
