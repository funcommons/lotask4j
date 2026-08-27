<template>
  <div class="lotask-page lotask-worker-guide">
    <FcSectionHeader :title="t('lotask.guides.workerGuide.title')" />

    <FcSection padding="md" shadow="sm">
      <FcTabsPanel v-model="activeTab" :tabs="tabs">
        <template #tab-overview>
          <div class="markdown-body" v-html="overviewHtml" />
        </template>
        <template #tab-api>
          <div class="markdown-body" v-html="apiHtml" />
        </template>
        <template #tab-implementation>
          <div class="markdown-body" v-html="implementationHtml" />
        </template>
        <template #tab-bestPractices>
          <div class="markdown-body" v-html="bestPracticesHtml" />
        </template>
      </FcTabsPanel>
    </FcSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import 'highlight.js/styles/github.css'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcTabsPanel from '@/components/sdk/navigation/FcTabsPanel.vue'
import { renderMarkdown } from '@/utils/markdown'

defineOptions({ name: 'LotaskWorkerGuidePage' })

const { t } = useI18n()

const activeTab = ref<'overview' | 'api' | 'implementation' | 'bestPractices'>('overview')

const tabs = computed(() => [
  { value: 'overview', label: `📚 ${t('lotask.guides.workerGuideExt.tabs.overview')}` },
  { value: 'api', label: `📡 ${t('lotask.guides.workerGuideExt.tabs.api')}` },
  { value: 'implementation', label: `🛠 ${t('lotask.guides.workerGuideExt.tabs.implementation')}` },
  { value: 'bestPractices', label: `⭐ ${t('lotask.guides.workerGuideExt.tabs.bestPractices')}` },
])

// ——— Markdown 内容 (按后端实际 4 个 worker 接口重写精简版) ———

const overviewMd = `# Worker 接入概览

lotask4j 的 **Worker (执行节点)** 负责长任务的真正执行：注册到 \`asts_worker_node\`，按 taskType 拉取待办任务、上报进度、上报结果。

## 工作循环

1. \`POST /api/v1/worker/register\` — Worker 注册到调度中心
2. 循环调用 \`POST /api/v1/worker/tasks/poll\` — 按 taskType 拉一个待执行任务
3. 对拿到的 \`payload\` 做业务处理（视频转码 / 数据导出 / 图片处理 / PDF 生成…）
4. 每完成一个内部步骤，调用 \`POST /api/v1/worker/tasks/{id}/progress\` 上报 \`currentStepKey + stepProgress\`
5. 最终调用 \`POST /api/v1/worker/tasks/{id}/result\` 上报 \`SUCCESS / FAILED / CANCELLED\`，附带 \`result\` 或 \`errorMsg\`
6. 回到步骤 2 继续轮询

## 四个核心接口

| 接口 | 用途 |
|---|---|
| \`POST /api/v1/worker/register\` | Worker 注册 |
| \`POST /api/v1/worker/tasks/poll\` | 拉取待执行任务 |
| \`POST /api/v1/worker/tasks/{id}/progress\` | 上报步骤进度 |
| \`POST /api/v1/worker/tasks/{id}/result\` | 上报最终结果 |

## 鉴权

Worker 接口走 Bearer Token (与 Client 共用)，后端按 \`workerKey + taskTypeKey\` 做 worker 维度路由。
`

const apiMd = `# Worker API 列表

## 1. Worker 注册

\`\`\`http
POST /api/v1/worker/register
Content-Type: application/json
Authorization: Bearer <token>
\`\`\`

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| \`workerKey\` | String | ✅ | Worker 全局唯一 ID，建议用 IP 或 UUID |
| \`taskTypeKey\` | String | ✅ | 此 Worker 处理的 taskType，如 \`video_transcode\` |
| \`ip\` | String | ✅ | Worker IP，供 dashboard 展示 |
| \`hostname\` | String | ❌ | 可选主机名 |

响应：

\`\`\`json
{
  "code": 0,
  "data": {
    "workerKey": "wkr-xxx",
    "taskTypeKey": "video_transcode",
    "status": "ONLINE",
    "registeredAt": "2026-08-27T..."
  }
}
\`\`\`

## 2. 拉取任务

\`\`\`http
POST /api/v1/worker/tasks/poll
\`\`\`

请求体：

\`\`\`json
{
  "taskType": "video_transcode",
  "workerId": "wkr-xxx",
  "strategy": "PRIORITY"
}
\`\`\`

- \`strategy\` 可选 \`PRIORITY\` / \`FIFO\`，不传默认 PRIORITY
- 无任务时 \`data\` 为 \`null\`

响应（有任务时）：

\`\`\`json
{
  "code": 0,
  "data": {
    "id": "t-xxxxxxxxxxxx",
    "type": "video_transcode",
    "payload": { ... },
    "priority": 80,
    "executionToken": "tok-xxx",
    "version": 1,
    "attempt": 1,
    "leaseExpireAt": "2026-08-27T..."
  }
}
\`\`\`

## 3. 上报进度

\`\`\`http
POST /api/v1/worker/tasks/{id}/progress
\`\`\`

\`\`\`json
{
  "currentStepKey": "transcode",
  "stepProgress": 70,
  "executionToken": "tok-xxx",
  "version": 1
}
\`\`\`

- \`stepProgress\` 0-100，对应当前步骤完成度
- \`executionToken + version\` 用于乐观锁，错位返回 409

## 4. 上报结果

\`\`\`http
POST /api/v1/worker/tasks/{id}/result
\`\`\`

成功：

\`\`\`json
{
  "status": "SUCCESS",
  "result": { "output_url": "...", "duration": 1800 },
  "executionToken": "tok-xxx",
  "version": 1
}
\`\`\`

失败：

\`\`\`json
{
  "status": "FAILED",
  "errorMsg": "转码失败: 不支持的视频编码格式",
  "executionToken": "tok-xxx",
  "version": 1
}
\`\`\`
`

const implementationMd = `# Worker 实现指南

## 推荐架构

\`\`\`
┌─────────────────────┐
│  Poller Thread      │  ← 定时 pollTask
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  Executor Pool      │  ← 多线程并发执行
│  (按 taskType 隔离)  │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  Reporter Thread    │  ← progress / result
└─────────────────────┘
\`\`\`

## Java + Spring Boot 示例

\`\`\`java
@Component
public class VideoTranscodeWorker {
  @Scheduled(fixedDelay = 1000)
  public void poll() {
    PollTaskResponse task = pollTask(taskType, workerKey);
    if (task == null) return;
    executor.submit(() -> execute(task));
  }

  private void execute(PollTaskResponse task) {
    try {
      reportProgress(task, "init", 10);
      // 步骤1: 下载源视频
      download((String) task.payload.get("url"));
      reportProgress(task, "download", 30);
      // 步骤2: 转码
      File output = transcode(...);
      reportProgress(task, "transcode", 70);
      // 步骤3: 上传
      String url = upload(output);
      reportProgress(task, "upload", 95);
      reportResult(task, "SUCCESS", Map.of("output_url", url));
    } catch (Exception e) {
      reportResult(task, "FAILED", null, e.getMessage());
    }
  }
}
\`\`\`

## Node.js 极简示例

\`\`\`javascript
async function loop() {
  while (true) {
    const task = await poll('video_transcode', workerKey)
    if (!task) { await sleep(1000); continue }
    try {
      await stepInit(task)
      await stepDownload(task, task.payload.url)
      await stepTranscode(task)
      await stepUpload(task)
      await reportResult(task.id, {
        status: 'SUCCESS',
        result: { output_url: '...' },
        executionToken: task.executionToken,
        version: task.version
      })
    } catch (e) {
      await reportResult(task.id, {
        status: 'FAILED',
        errorMsg: e.message,
        executionToken: task.executionToken,
        version: task.version
      })
    }
  }
}
loop()
\`\`\`

## 心跳与 lease

每次 poll 都带 \`leaseExpireAt\`（通常是 +60s）。如果 Worker 在 lease 内没上报 progress/result，后端会 **reap** 任务并重新派发。建议每 30s 报一次 progress。
`

const bestPracticesMd = `# Worker 最佳实践

## 1. 幂等执行

同一任务可能被多次派发（reap / 重试）。在执行前用 \`task.id + task.version\` 做本地去重，避免重复执行产生副作用。

## 2. 优雅停机

- 收到 SIGTERM 时停止 poll
- 等待已 poll 到内存中的任务上报 result 后退出
- 不要强杀，避免任务卡在 CANCELLING

## 3. 多 worker 并发

- 同 taskType 下允许多 worker 实例（每实例一个 workerKey）
- 不同 taskType 可以独立扩容
- 通过修改 \`task_type_config.concurrencyLimit\` 控制单 worker 的并发上限

## 4. 进度粒度

\`stepProgress\` 不必每 100ms 报一次：
- 步骤级别的完成点报一次（如 "transcode" 完成报 70）
- 大步骤内部可以本地 chunk 推进，但 30s 至少报一次防止 lease 过期

## 5. 错误分类

\`errorMsg\` 应该带可读中文描述 + 错误码，方便 admin 后台检索：

\`\`\`
[task=t-xxx] 转码失败: 不支持的视频编码格式 (codec=h265_profile=main)
\`\`\`

## 6. 与 callback 配合

Worker 上报 SUCCESS 之后，后端会自动触发 Client 的 callbackUrl。Worker 不需要自己通知业务方，避免重复。
`

const overviewHtml = computed(() => renderMarkdown(overviewMd))
const apiHtml = computed(() => renderMarkdown(apiMd))
const implementationHtml = computed(() => renderMarkdown(implementationMd))
const bestPracticesHtml = computed(() => renderMarkdown(bestPracticesMd))
</script>

<style scoped lang="scss">
.lotask-worker-guide {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: var(--el-text-color-primary);

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    font-weight: 600;
    margin: 24px 0 12px;
    color: var(--el-text-color-primary);
  }

  :deep(h1) { font-size: 22px; border-bottom: 2px solid var(--el-border-color); padding-bottom: 8px; }
  :deep(h2) { font-size: 18px; border-bottom: 1px solid var(--el-border-color-lighter); padding-bottom: 6px; }
  :deep(h3) { font-size: 16px; }
  :deep(h4) { font-size: 14px; }

  :deep(p) { margin: 10px 0; }

  :deep(ul),
  :deep(ol) {
    margin: 10px 0;
    padding-left: 24px;
  }

  :deep(li) { margin: 4px 0; }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 16px 0;
    font-size: 13px;

    th, td {
      border: 1px solid var(--el-border-color);
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: var(--el-fill-color-light);
      font-weight: 600;
    }

    tr:nth-child(2n) {
      background: var(--el-fill-color-lighter);
    }
  }

  :deep(pre.hljs) {
    background: var(--el-fill-color-dark) !important;
    border: 1px solid var(--el-border-color);
    border-radius: 6px;
    padding: 12px 16px;
    overflow-x: auto;
    margin: 12px 0;
    font-size: 12px;
    font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;

    code {
      background: transparent;
      color: var(--el-text-color-primary);
    }
  }

  :deep(code) {
    background: var(--el-fill-color-light);
    color: var(--el-color-primary);
    padding: 2px 6px;
    border-radius: 3px;
    font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
    font-size: 0.92em;
  }

  :deep(pre code) {
    background: transparent;
    color: inherit;
    padding: 0;
  }

  :deep(blockquote) {
    border-left: 4px solid var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    padding: 8px 16px;
    margin: 12px 0;
    color: var(--el-text-color-regular);
  }

  :deep(hr) {
    border: 0;
    border-top: 1px solid var(--el-border-color);
    margin: 20px 0;
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>