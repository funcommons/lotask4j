<template>
  <div class="lotask-page lotask-client-guide">
    <FcSectionHeader :title="t('lotask.guides.clientGuide.title')" />

    <FcSection padding="md" shadow="sm">
      <FcTabsPanel v-model="activeTab" :tabs="tabs">
        <template #tab-overview>
          <div class="markdown-body" v-html="overviewHtml" />
        </template>
        <template #tab-api>
          <div class="markdown-body" v-html="apiHtml" />
        </template>
        <template #tab-code>
          <div class="markdown-body" v-html="codeHtml" />
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

defineOptions({ name: 'LotaskClientGuidePage' })

const { t } = useI18n()

const activeTab = ref<'overview' | 'api' | 'code' | 'bestPractices'>('overview')

const tabs = computed(() => [
  { value: 'overview', label: `📚 ${t('lotask.guides.clientGuideExt.tabs.overview')}` },
  { value: 'api', label: `📡 ${t('lotask.guides.clientGuideExt.tabs.api')}` },
  { value: 'code', label: `💻 ${t('lotask.guides.clientGuideExt.tabs.code')}` },
  { value: 'bestPractices', label: `⭐ ${t('lotask.guides.clientGuideExt.tabs.bestPractices')}` },
])

// ——— Markdown 内容 (按后端实际 4 个 client 接口重写精简版) ———

const overviewMd = `# Client 集成概览

lotask4j 的 **Client (客户端)** 面向业务系统后端，负责把耗时任务交给 Worker 节点执行。

## 工作流程

1. 业务后端通过 \`POST /api/v1/client/tasks\` 提交任务，立即返回任务 ID
2. 业务后端用 \`GET /api/v1/client/tasks/{id}\` 轮询任务详情（或者挂 callbackUrl 接收 Webhook）
3. 业务后端用 \`POST /api/v1/client/tasks/{id}/cancel\` 在 PENDING/RUNNING 状态下取消
4. 业务后端用 \`GET /api/v1/client/tasks/stats\` 拉统计概览（待处理 / 运行中 / 今日成功失败取消数）

## 四个核心接口

| 接口 | 用途 |
|---|---|
| \`POST /api/v1/client/tasks\` | 提交任务 |
| \`GET  /api/v1/client/tasks/{id}\` | 查询任务详情 |
| \`POST /api/v1/client/tasks/{id}/cancel\` | 取消任务 |
| \`GET  /api/v1/client/tasks/stats\` | 任务统计 |

## 鉴权

所有 Client 接口走 Bearer Token (client_credentials)，由 \`/api/v1/auth/token\` 颁发。
`

const apiMd = `# Client API 列表

## 1. 提交任务

\`\`\`http
POST /api/v1/client/tasks
Content-Type: application/json
Authorization: Bearer <token>
\`\`\`

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| \`type\` | String | ✅ | 任务类型键，如 \`data_export\` |
| \`payload\` | Object | ✅ | 业务参数，JSONB 列存储 |
| \`priority\` | Integer | ❌ | 0-100，默认 0 |
| \`idempotencyKey\` | String | ❌ | 幂等键，相同 key 返回首次结果 |
| \`callbackUrl\` | String | ❌ | Webhook URL，任务结束回调 |

响应（envelope）：

\`\`\`json
{
  "code": 0,
  "data": { "id": "t-xxxxxxxxxxxx" },
  "trace_id": "..."
}
\`\`\`

## 2. 查询任务详情

\`\`\`http
GET /api/v1/client/tasks/{id}
\`\`\`

- \`id\` 是后端的 OpenID 混淆字符串，**不**是数据库自增 ID
- 响应字段包含：\`status\` / \`progress\` / \`currentStep\` / \`result\` / \`errorMsg\` / \`stepsDetail\` 等

## 3. 取消任务

\`\`\`http
POST /api/v1/client/tasks/{id}/cancel
\`\`\`

- 仅 \`PENDING\` / \`RUNNING\` 状态可取消
- 取消是非阻塞的：返回成功后 worker 仍在执行中，需要 worker 主动上报 \`CANCELLED\` 结果

## 4. 任务统计

\`\`\`http
GET /api/v1/client/tasks/stats
\`\`\`

响应：

\`\`\`json
{
  "code": 0,
  "data": {
    "totalPending": 12,
    "totalRunning": 3,
    "todayStats": { "success": 145, "failed": 2, "cancelled": 1 },
    "workerCount": { "online": 5, "offline": 1 }
  }
}
\`\`\`
`

const codeMd = `# 代码示例

## Java + OkHttp

\`\`\`java
// 1. 提交任务
Map<String, Object> body = Map.of(
    "type", "data_export",
    "payload", Map.of("dateRange", "2026-08-01,2026-08-31", "format", "excel"),
    "priority", 50,
    "callbackUrl", "https://my.app/webhook/asts"
);
String json = objectMapper.writeValueAsString(body);
Request req = new Request.Builder()
    .url(baseUrl + "/api/v1/client/tasks")
    .post(RequestBody.create(json, JSON))
    .header("Authorization", "Bearer " + token)
    .build();
String id = parseId(okHttp.newCall(req).execute());

// 2. 轮询查询
Request poll = new Request.Builder()
    .url(baseUrl + "/api/v1/client/tasks/" + id)
    .header("Authorization", "Bearer " + token)
    .build();

// 3. 取消
Request cancel = new Request.Builder()
    .url(baseUrl + "/api/v1/client/tasks/" + id + "/cancel")
    .post(RequestBody.create(new byte[0]))
    .header("Authorization", "Bearer " + token)
    .build();
\`\`\`

## Node.js + axios

\`\`\`javascript
// 1. 提交
const { data: { data: { id } } } = await axios.post(
  '/api/v1/client/tasks',
  {
    type: 'image_process',
    payload: { url: 'https://oss.example.com/raw.jpg' },
    priority: 30
  },
  { headers: { Authorization: \`Bearer \${token}\` } }
)

// 2. 轮询
const { data: { data: task } } = await axios.get(
  \`/api/v1/client/tasks/\${id}\`,
  { headers: { Authorization: \`Bearer \${token}\` } }
)
if (task.status === 'SUCCESS') {
  console.log(task.result)
}

// 3. 取消
await axios.post(
  \`/api/v1/client/tasks/\${id}/cancel\`,
  null,
  { headers: { Authorization: \`Bearer \${token}\` } }
)
\`\`\`

## Python + requests

\`\`\`python
import requests

# 1. 提交
r = requests.post(
    f"{BASE}/api/v1/client/tasks",
    json={"type": "video_transcode", "payload": {"url": "...", "format": "1080p"}},
    headers={"Authorization": f"Bearer {token}"}
)
task_id = r.json()["data"]["id"]

# 2. 轮询
r = requests.get(
    f"{BASE}/api/v1/client/tasks/{task_id}",
    headers={"Authorization": f"Bearer {token}"}
)
print(r.json()["data"]["status"])

# 3. 取消
requests.post(
    f"{BASE}/api/v1/client/tasks/{task_id}/cancel",
    headers={"Authorization": f"Bearer {token}"}
)
\`\`\`
`

const bestPracticesMd = `# Client 最佳实践

## 1. 用 callbackUrl 替代轮询

提交任务时传入 \`callbackUrl\`，后端在任务进入终止态时会 POST 一份 JSON 到你的 Webhook：

\`\`\`json
{
  "id": "t-xxxxxxxxxxxx",
  "status": "SUCCESS",
  "result": { ... }
}
\`\`\`

这样既节省请求，又能在任务完成瞬间拿到结果。

## 2. 轮询策略

- 如果没有 callbackUrl，建议 5-10s 一次轮询
- 高优先级任务（priority > 80）可以 2-3s 一次轮询
- 拿到 \`status ∈ {SUCCESS, FAILED, CANCELLED}\` 立刻停止轮询

## 3. 用幂等键防重复提交

\`\`\`json
{ "type": "...", "payload": {...}, "idempotencyKey": "订单号-001" }
\`\`\`

- 后端会把同一个 \`idempotencyKey\` 在 24h 内的二次请求返回首次结果
- 适用场景：用户重复点击提交、网络重试

## 4. 取消语义

\`/cancel\` 是 **异步** 的：返回成功 ≠ worker 已停止。

- 后端把任务置为 \`CANCELLING\`
- Worker 下一轮 \`reportProgress\` 拿到 \`requestedCancelAt\` 字段后应主动终止
- Worker 上报 \`CANCELLED\` 后任务才真正关闭

## 5. 监控任务统计

定期拉 \`/stats\` 接口，把待处理 / 运行中 / 今日失败数接入业务监控。
`

const overviewHtml = computed(() => renderMarkdown(overviewMd))
const apiHtml = computed(() => renderMarkdown(apiMd))
const codeHtml = computed(() => renderMarkdown(codeMd))
const bestPracticesHtml = computed(() => renderMarkdown(bestPracticesMd))
</script>

<style scoped lang="scss">
.lotask-client-guide {
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