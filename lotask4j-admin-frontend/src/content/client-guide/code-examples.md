# 代码集成示例

## Java 集成示例

### 1. 添加依赖

\`\`\`xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
\`\`\`

### 2. ASTS 客户端封装

\`\`\`java
package com.example.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Map;
import java.util.HashMap;

@Service
public class AstsClient {

    private final RestTemplate restTemplate;
    private final String astsBaseUrl = "http://localhost:8080/api/v1/client";

    public AstsClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 提交任务
     */
    public String submitTask(String taskType, Map<String, Object> payload,
                             Integer priority, String callbackUrl) {
        String url = astsBaseUrl + "/tasks";

        Map<String, Object> request = new HashMap<>();
        request.put("type", taskType);
        request.put("payload", payload);
        request.put("priority", priority);
        request.put("callbackUrl", callbackUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null && (Integer) body.get("code") == 0) {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return (String) data.get("id");
        }

        throw new RuntimeException("提交任务失败: " + body.get("message"));
    }

    /**
     * 查询任务状态
     */
    public Map<String, Object> getTaskStatus(String id) {
        String url = astsBaseUrl + "/tasks/" + id;

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null && (Integer) body.get("code") == 0) {
            return (Map<String, Object>) body.get("data");
        }

        throw new RuntimeException("查询任务失败: " + body.get("message"));
    }

    /**
     * 取消任务
     */
    public void cancelTask(String id) {
        String url = astsBaseUrl + "/tasks/" + id + "/cancel";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        restTemplate.postForEntity(url, entity, Map.class);
    }

    /**
     * 轮询等待任务完成
     */
    public Map<String, Object> waitForTask(String id, long timeoutMs)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long delay = 1000; // 初始 1 秒
        long maxDelay = 30000; // 最大 30 秒

        while (true) {
            // 检查超时
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw new RuntimeException("等待任务超时");
            }

            // 查询任务状态
            Map<String, Object> task = getTaskStatus(id);
            String status = (String) task.get("status");

            // 检查终态
            if ("SUCCESS".equals(status) || "FAILED".equals(status)
                || "CANCELLED".equals(status)) {
                return task;
            }

            // 等待后重试
            Thread.sleep(delay);
            delay = Math.min((long) (delay * 1.5), maxDelay);
        }
    }
}
\`\`\`

### 3. 业务服务使用示例

\`\`\`java
package com.example.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;

@Service
public class DataExportService {

    private final AstsClient astsClient;

    public DataExportService(AstsClient astsClient) {
        this.astsClient = astsClient;
    }

    /**
     * 导出用户数据
     */
    public String exportUsers(String query, String format) {
        // 构造任务入参
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("format", format);

        // 提交任务
        String id = astsClient.submitTask(
            "data_export",           // 任务类型
            payload,                 // 入参
            80,                      // 优先级
            "https://your-app.com/webhook/export-completed"  // 回调 URL
        );

        System.out.println("任务已提交，任务ID: " + id);
        return id;
    }

    /**
     * 同步等待导出完成
     */
    public Map<String, Object> exportUsersSync(String query, String format)
            throws InterruptedException {
        // 提交任务
        String id = exportUsers(query, format);

        // 轮询等待完成（最多等待 10 分钟）
        Map<String, Object> task = astsClient.waitForTask(id, 600000);

        String status = (String) task.get("status");
        if ("SUCCESS".equals(status)) {
            Map<String, Object> result = (Map<String, Object>) task.get("result");
            System.out.println("导出成功，文件URL: " + result.get("fileUrl"));
            return result;
        } else {
            String errorMsg = (String) task.get("errorMsg");
            throw new RuntimeException("导出失败: " + errorMsg);
        }
    }
}
\`\`\`

---

## TypeScript 集成示例

### 1. 类型定义

\`\`\`typescript
// types/asts.ts

export interface SubmitTaskRequest {
  type: string
  payload: Record<string, any>
  priority?: number
  callbackUrl?: string
}

export interface SubmitTaskResponse {
  id: string
}

export interface TaskDetail {
  id: string
  type: string
  typeName: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLING' | 'CANCELLED'
  progress: number
  currentStep: string
  stepsDetail: TaskStep[]
  payload: Record<string, any>
  result: Record<string, any> | null
  errorMsg: string | null
  workerIp: string | null
  createdAt: string
  updatedAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface TaskStep {
  key: string
  name: string
  status: 'pending' | 'processing' | 'finished' | 'failed' | 'skipped'
  detail: string | null
  progress: number
  costMs: number | null
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId: string
}
\`\`\`

### 2. ASTS 客户端封装

\`\`\`typescript
// services/astsClient.ts

import axios, { AxiosInstance } from 'axios'
import type {
  SubmitTaskRequest,
  SubmitTaskResponse,
  TaskDetail,
  ApiResponse
} from '@/types/asts'

export class AstsClient {
  private client: AxiosInstance

  constructor(baseURL: string = 'http://localhost:8080/api/v1/client') {
    this.client = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json'
      }
    })
  }

  /**
   * 提交任务
   */
  async submitTask(request: SubmitTaskRequest): Promise<string> {
    const response = await this.client.post<ApiResponse<SubmitTaskResponse>>(
      '/tasks',
      request
    )

    if (response.data.code === 0) {
      return response.data.data.id
    }

    throw new Error(\`提交任务失败: \${response.data.message}\`)
  }

  /**
   * 查询任务状态
   */
  async getTaskStatus(id: string): Promise<TaskDetail> {
    const response = await this.client.get<ApiResponse<TaskDetail>>(
      \`/tasks/\${id}\`
    )

    if (response.data.code === 0) {
      return response.data.data
    }

    throw new Error(\`查询任务失败: \${response.data.message}\`)
  }

  /**
   * 取消任务
   */
  async cancelTask(id: string): Promise<void> {
    await this.client.post(\`/tasks/\${id}/cancel\`)
  }

  /**
   * 轮询等待任务完成
   */
  async waitForTask(
    id: string,
    timeoutMs: number = 600000
  ): Promise<TaskDetail> {
    const startTime = Date.now()
    let delay = 1000 // 初始 1 秒
    const maxDelay = 30000 // 最大 30 秒

    while (true) {
      // 检查超时
      if (Date.now() - startTime > timeoutMs) {
        throw new Error('等待任务超时')
      }

      // 查询任务状态
      const task = await this.getTaskStatus(id)

      // 检查终态
      if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(task.status)) {
        return task
      }

      // 等待后重试
      await this.sleep(delay)
      delay = Math.min(delay * 1.5, maxDelay)
    }
  }

  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
  }
}
\`\`\`

### 3. 业务使用示例

\`\`\`typescript
// services/dataExportService.ts

import { AstsClient } from './astsClient'

export class DataExportService {
  private astsClient: AstsClient

  constructor() {
    this.astsClient = new AstsClient()
  }

  /**
   * 导出用户数据（异步）
   */
  async exportUsers(query: string, format: string): Promise<string> {
    const id = await this.astsClient.submitTask({
      type: 'data_export',
      payload: {
        query,
        format
      },
      priority: 80,
      callbackUrl: 'https://your-app.com/webhook/export-completed'
    })

    console.log('任务已提交，任务ID:', id)
    return id
  }

  /**
   * 导出用户数据（同步等待）
   */
  async exportUsersSync(query: string, format: string): Promise<any> {
    // 提交任务
    const id = await this.exportUsers(query, format)

    // 轮询等待完成
    const task = await this.astsClient.waitForTask(id)

    if (task.status === 'SUCCESS') {
      console.log('导出成功，文件URL:', task.result?.fileUrl)
      return task.result
    } else {
      throw new Error(\`导出失败: \${task.errorMsg}\`)
    }
  }
}
\`\`\`

### 4. React 组件示例

\`\`\`typescript
import { useState } from 'react'
import { Button, Progress, message } from 'antd'
import { DataExportService } from '@/services/dataExportService'

export default function ExportButton() {
  const [loading, setLoading] = useState(false)
  const [progress, setProgress] = useState(0)
  const exportService = new DataExportService()

  const handleExport = async () => {
    setLoading(true)
    try {
      // 提交任务
      const id = await exportService.exportUsers(
        'SELECT * FROM users',
        'xlsx'
      )

      // 轮询进度
      const interval = setInterval(async () => {
        const task = await exportService.astsClient.getTaskStatus(id)
        setProgress(task.progress)

        if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(task.status)) {
          clearInterval(interval)
          setLoading(false)

          if (task.status === 'SUCCESS') {
            message.success('导出成功！')
            // 下载文件
            window.open(task.result?.fileUrl)
          } else {
            message.error(\`导出失败: \${task.errorMsg}\`)
          }
        }
      }, 2000)

    } catch (error) {
      setLoading(false)
      message.error('导出失败: ' + error.message)
    }
  }

  return (
    <div>
      <Button
        type="primary"
        onClick={handleExport}
        loading={loading}
      >
        导出数据
      </Button>
      {loading && <Progress percent={progress} />}
    </div>
  )
}
\`\`\`
