<template>
  <div class="lotask-page lotask-demo-simulator">
    <FcSectionHeader :title="t('lotask.guides.demoSimulatorExt.title')" />

    <el-alert
      :title="t('lotask.guides.demoSimulatorExt.envWarning')"
      :description="t('lotask.guides.demoSimulatorExt.envWarningDesc')"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 3 列控制面板 -->
    <FcSection padding="md" shadow="sm">
      <div class="control-grid">
        <!-- Client -->
        <div class="control-card">
          <div class="control-title">
            <i class="ri-user-line" />
            {{ t('lotask.guides.demoSimulatorExt.client.title') }}
          </div>
          <div class="control-body">
            <label class="control-label">
              {{ t('lotask.guides.demoSimulatorExt.client.taskType') }}
              <FcSelect
                v-model="taskType"
                :disabled="isClientRunning"
                :options="taskTypeOptions"
                style="margin-top: 6px"
              />
            </label>
            <label class="control-label">
              {{ t('lotask.guides.demoSimulatorExt.client.submitInterval') }}
              <el-input-number
                v-model="clientInterval"
                :disabled="isClientRunning"
                :min="1000"
                :step="1000"
                class="fc-input"
                style="margin-top: 6px; width: 100%"
              />
            </label>
            <FcButton
              v-if="!isClientRunning"
              type="primary"
              :icon="VideoPlayIcon"
              block
              @click="startClient"
            >
              {{ t('lotask.guides.demoSimulatorExt.client.start') }}
            </FcButton>
            <FcButton
              v-else
              type="danger"
              :icon="VideoPauseIcon"
              block
              @click="stopClient"
            >
              {{ t('lotask.guides.demoSimulatorExt.client.stop') }}
            </FcButton>
          </div>
        </div>

        <!-- Worker -->
        <div class="control-card">
          <div class="control-title">
            <i class="ri-server-line" />
            {{ t('lotask.guides.demoSimulatorExt.worker.title') }}
          </div>
          <div class="control-body">
            <label class="control-label">
              {{ t('lotask.guides.demoSimulatorExt.worker.pollInterval') }}
              <el-input-number
                v-model="workerInterval"
                :disabled="isWorkerRunning"
                :min="1000"
                :step="1000"
                class="fc-input"
                style="margin-top: 6px; width: 100%"
              />
            </label>
            <div class="control-spacer" />
            <FcButton
              v-if="!isWorkerRunning"
              type="primary"
              :icon="VideoPlayIcon"
              block
              @click="startWorker"
            >
              {{ t('lotask.guides.demoSimulatorExt.worker.start') }}
            </FcButton>
            <FcButton
              v-else
              type="danger"
              :icon="VideoPauseIcon"
              block
              @click="stopWorker"
            >
              {{ t('lotask.guides.demoSimulatorExt.worker.stop') }}
            </FcButton>
          </div>
        </div>

        <!-- 统计 -->
        <div class="control-card">
          <div class="control-title">
            <i class="ri-bar-chart-line" />
            {{ t('lotask.guides.demoSimulatorExt.stats.title') }}
          </div>
          <div class="stats-grid">
            <div class="stat-tile stat-tile--info">
              <div class="stat-value">{{ taskStats.submitted }}</div>
              <div class="stat-label">{{ t('lotask.guides.demoSimulatorExt.stats.submitted') }}</div>
            </div>
            <div class="stat-tile stat-tile--success">
              <div class="stat-value">{{ taskStats.completed }}</div>
              <div class="stat-label">{{ t('lotask.guides.demoSimulatorExt.stats.completed') }}</div>
            </div>
            <div class="stat-tile stat-tile--danger">
              <div class="stat-value">{{ taskStats.failed }}</div>
              <div class="stat-label">{{ t('lotask.guides.demoSimulatorExt.stats.failed') }}</div>
            </div>
          </div>
          <FcButton :icon="DeleteIcon" block @click="clearLogs" style="margin-top: 12px">
            {{ t('lotask.guides.demoSimulatorExt.stats.clearLogs') }}
          </FcButton>
        </div>
      </div>
    </FcSection>

    <!-- 活跃任务 -->
    <FcSection padding="md" shadow="sm" style="margin-top: 16px">
      <template #header>
        <div class="section-title">
          <i class="ri-list-check" />
          <span>{{ t('lotask.guides.demoSimulatorExt.activeTasks') }}</span>
        </div>
      </template>
      <div v-if="activeTasks.length === 0" class="empty-tasks">
        {{ t('lotask.guides.demoSimulatorExt.noActiveTasks') }}
      </div>
      <div v-else class="tasks-grid">
        <div
          v-for="task in activeTasks"
          :key="task.id"
          class="active-task-card"
          :class="task.status.toLowerCase()"
        >
          <div class="task-header">
            <div>
              <div class="task-name">{{ task.typeName || task.type }}</div>
              <div class="task-id">{{ task.id.substring(0, 12) }}...</div>
            </div>
            <FcTag
              :color="taskTagColor(task.status)"
              size="sm"
            >
              {{ task.status }}
            </FcTag>
          </div>
          <template v-if="task.status === 'RUNNING'">
            <el-progress
              :percentage="task.progress"
              :stroke-width="6"
              status="active"
              :color="['#007aff', '#34c759']"
            />
            <div v-if="task.currentStep" class="task-step">{{ task.currentStep }}</div>
          </template>
          <el-progress
            v-else-if="task.status === 'SUCCESS'"
            :percentage="100"
            :stroke-width="6"
            status="success"
          />
          <el-progress
            v-else
            :percentage="task.progress"
            :stroke-width="6"
          />
        </div>
      </div>
    </FcSection>

    <!-- 日志 -->
    <FcSection padding="md" shadow="sm" style="margin-top: 16px">
      <template #header>
        <div class="section-title">
          <span>{{ t('lotask.guides.demoSimulatorExt.logs') }}</span>
          <FcTag color="success" size="sm" style="margin-left: 8px">
            <i class="ri-flashlight-line" style="margin-right: 2px" />
            {{ t('lotask.guides.demoSimulatorExt.logsRealtime') }}
          </FcTag>
        </div>
      </template>
      <div class="log-panel">
        <el-timeline>
          <el-timeline-item
            v-for="(log, idx) in logs"
            :key="idx"
            :type="logTimelineType(log.type)"
            :timestamp="log.time"
          >
            <span :class="['log-icon', log.type]">{{ logIcon(log.type) }}</span>
            <span>{{ log.message }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
    </FcSection>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { VideoPlay, VideoPause, Delete } from '@element-plus/icons-vue'
import { submitTask, getTaskDetail } from '@/api/client'
import {
  registerWorker,
  pollTask,
  reportProgress,
  reportResult,
  type ReportProgressRequest,
  type ReportResultRequest,
} from '@/api/worker'
import { formatTimeString } from '@/utils/date'
import { toast } from '@/components/sdk'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'
import FcSelect from '@/components/sdk/form/FcSelect.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'

defineOptions({ name: 'LotaskDemoSimulatorPage' })

const { t } = useI18n()

interface SimLog {
  time: string
  type: 'client' | 'worker' | 'system'
  message: string
}

interface ActiveTask {
  id: string
  type: string
  typeName: string
  status: string
  progress: number
  currentStep?: string
  payload?: Record<string, unknown>
}

const VideoPlayIcon = VideoPlay
const VideoPauseIcon = VideoPause
const DeleteIcon = Delete

const logs = ref<SimLog[]>([])
const isClientRunning = ref(false)
const isWorkerRunning = ref(false)
const taskType = ref('video_transcode')
const clientInterval = ref(5000)
const workerInterval = ref(3000)
const taskStats = reactive({ submitted: 0, completed: 0, failed: 0 })
const activeTasks = ref<ActiveTask[]>([])

let clientTimer: number | null = null
let workerTimer: number | null = null

const taskTypeOptions: Array<{ label: string; value: string }> = [
  { label: t('lotask.guides.demoSimulatorExt.taskTypeName.video_transcode'), value: 'video_transcode' },
  { label: t('lotask.guides.demoSimulatorExt.taskTypeName.data_export'), value: 'data_export' },
  { label: t('lotask.guides.demoSimulatorExt.taskTypeName.image_process'), value: 'image_process' },
  { label: t('lotask.guides.demoSimulatorExt.taskTypeName.pdf_generate'), value: 'pdf_generate' },
]

function addLog(type: 'client' | 'worker' | 'system', message: string) {
  const time = formatTimeString(Date.now())
  logs.value = [...logs.value.slice(-49), { time, type, message }]
}

function logIcon(type: string): string {
  if (type === 'client') return '📤'
  if (type === 'worker') return '⚙️'
  return '💻'
}

function logTimelineType(type: string): 'success' | 'warning' | 'primary' {
  if (type === 'client') return 'primary'
  if (type === 'worker') return 'success'
  return 'warning'
}

function taskTagColor(status: string): 'success' | 'warning' | 'danger' | 'primary' | 'gray' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLING') return 'warning'
  return 'gray'
}

function generatePayload(type: string): Record<string, unknown> {
  const r = Math.floor(Math.random() * 1000)
  switch (type) {
    case 'video_transcode':
      return {
        url: `http://oss.example.com/videos/input_${r}.mp4`,
        format: ['720p', '1080p', '4K'][Math.floor(Math.random() * 3)],
        bitrate: ['1500k', '2000k', '3000k', '5000k'][Math.floor(Math.random() * 4)],
        codec: ['h264', 'h265', 'av1'][Math.floor(Math.random() * 3)],
        watermark: Math.random() > 0.5 ? `wm_${r}.png` : null,
        duration: Math.floor(Math.random() * 3600) + 60,
      }
    case 'data_export':
      return {
        exportType: ['excel', 'csv', 'pdf'][Math.floor(Math.random() * 3)],
        dateRange: `2026-0${Math.floor(Math.random() * 9) + 1}-01,2026-0${Math.floor(Math.random() * 9) + 1}-30`,
        filters: {
          status: ['active', 'pending', 'completed'][Math.floor(Math.random() * 3)],
          category: `cat_${Math.floor(Math.random() * 10)}`,
          minAmount: Math.floor(Math.random() * 1000),
        },
        columns: ['id', 'name', 'amount', 'status'],
        compress: Math.random() > 0.5,
      }
    case 'image_process':
      return {
        url: `http://oss.example.com/images/raw_${r}.jpg`,
        operations: [
          { type: 'resize', width: 800, height: 600 },
          { type: 'watermark', text: `© ${new Date().getFullYear()}` },
          { type: 'format', target: 'webp' },
        ],
        quality: Math.floor(Math.random() * 30) + 70,
        outputFormat: ['jpg', 'png', 'webp'][Math.floor(Math.random() * 3)],
      }
    case 'pdf_generate':
      return {
        templateId: `tpl_${Math.floor(Math.random() * 100)}`,
        data: {
          title: `Report_${r}`,
          author: t(`lotask.guides.demoSimulatorExt.author${(Math.floor(Math.random() * 3) + 1)}`),
        },
        format: ['A4', 'A3', 'Letter'][Math.floor(Math.random() * 3)],
        orientation: ['portrait', 'landscape'][Math.floor(Math.random() * 2)],
        includeWatermark: Math.random() > 0.5,
      }
    default:
      return { id: `task_${Date.now()}_${r}` }
  }
}

function generateResult(type: string): Record<string, unknown> {
  const ts = new Date().toISOString()
  switch (type) {
    case 'video_transcode':
      return {
        output_url: `http://oss.example.com/videos/output_${Date.now()}.mp4`,
        duration: Math.floor(Math.random() * 3600) + 60,
        size: Math.floor(Math.random() * 50000000) + 5000000,
        codec: ['h264', 'h265', 'av1'][Math.floor(Math.random() * 3)],
        resolution: ['720p', '1080p', '4K'][Math.floor(Math.random() * 3)],
        processed_at: ts,
      }
    case 'data_export':
      return {
        download_url: `http://cdn.example.com/exports/export_${Date.now()}.xlsx`,
        total_records: Math.floor(Math.random() * 100000) + 1000,
        file_size: Math.floor(Math.random() * 10000000) + 100000,
        columns: Math.floor(Math.random() * 20) + 5,
        processed_at: ts,
      }
    case 'image_process':
      return {
        output_url: `http://cdn.example.com/images/processed_${Date.now()}.webp`,
        original_size: Math.floor(Math.random() * 5000000) + 500000,
        output_size: Math.floor(Math.random() * 1000000) + 100000,
        width: 800,
        height: 600,
        processed_at: ts,
      }
    case 'pdf_generate':
      return {
        download_url: `http://cdn.example.com/pdfs/report_${Date.now()}.pdf`,
        page_count: Math.floor(Math.random() * 50) + 10,
        file_size: Math.floor(Math.random() * 5000000) + 500000,
        processed_at: ts,
      }
    default:
      return { status: 'completed', timestamp: ts }
  }
}

const errorKeys = ['transcode', 'download', 'memory', 'format', 'permission', 'quota', 'busy'] as const
function randomError(): string {
  const key = errorKeys[Math.floor(Math.random() * errorKeys.length)]
  return t(`lotask.guides.demoSimulatorExt.errors.${key}`)
}

const workerKey = `wkr-${Math.random().toString(36).slice(2, 10)}`

async function ensureWorkerRegistered() {
  try {
    await registerWorker({
      workerKey,
      taskTypeKey: taskType.value,
      ip: '127.0.0.1',
      hostname: 'demo-simulator',
    })
  } catch {
    // Worker 注册失败不致命 (开发环境可能没注册接口), 但仍让 poll 走默认流程
  }
}

async function submitOneTask() {
  try {
    const payload = generatePayload(taskType.value)
    const priority = Math.floor(Math.random() * 100)
    const hasCallback = Math.random() > 0.7
    const data: Record<string, unknown> = {
      type: taskType.value,
      payload,
      priority,
    }
    if (hasCallback) data.callbackUrl = `https://webhook.example.com/cb/${Math.floor(Math.random() * 1000)}`
    const res = await submitTask(data as never)
    const id = (res as { id?: string }).id
    if (!id) return
    addLog('client', `✅ ${t('lotask.guides.demoSimulatorExt.submitSuccess')}: ${id.substring(0, 8)}... [${taskType.value}, priority=${priority}${hasCallback ? ', callback' : ''}]`)
    taskStats.submitted++
    const typeName = taskTypeOptions.find(o => o.value === taskType.value)?.label || taskType.value
    try {
      const detail = await getTaskDetail(id)
      activeTasks.value.push({
        id,
        type: taskType.value,
        typeName,
        status: 'PENDING',
        progress: 0,
        payload: detail?.payload,
      })
    } catch {
      activeTasks.value.push({
        id,
        type: taskType.value,
        typeName,
        status: 'PENDING',
        progress: 0,
      })
    }
  } catch (err) {
    addLog('client', `❌ ${t('lotask.guides.demoSimulatorExt.submitFailed')}: ${(err as Error).message}`)
  }
}

async function pollAndExecute() {
  try {
    const task = await pollTask({
      taskType: taskType.value,
      workerId: workerKey,
      strategy: 'PRIORITY',
    })
    if (!task) {
      addLog('worker', `⏳ ${t('lotask.guides.demoSimulatorExt.worker.noTask')}`)
      return
    }
    const id = task.id
    addLog('worker', `🔨 ${t('lotask.guides.demoSimulatorExt.worker.begin')}: ${id.substring(0, 8)}...`)

    let detail: { payload?: Record<string, unknown>; typeName?: string; type?: string } | null = null
    try {
      detail = await getTaskDetail(id)
    } catch { /* noop */ }

    const typeName = detail?.typeName || taskTypeOptions.find(o => o.value === taskType.value)?.label || taskType.value
    const updateTask = (patch: Partial<ActiveTask>) => {
      const i = activeTasks.value.findIndex(x => x.id === id)
      if (i >= 0) {
        const merged: ActiveTask = { ...activeTasks.value[i]!, ...patch } as ActiveTask
        activeTasks.value[i] = merged
      } else {
        const base: ActiveTask = {
          id,
          type: detail?.type || task.type,
          typeName,
          status: 'RUNNING',
          progress: 0,
          ...patch,
        }
        activeTasks.value.push(base)
      }
    }
    updateTask({ status: 'RUNNING', progress: 0, payload: detail?.payload, typeName })

    const steps: Array<{ key: string; name: string; progress: number }> = [
      { key: 'init', name: t('lotask.guides.demoSimulatorExt.step.init'), progress: 10 },
      { key: 'download', name: t('lotask.guides.demoSimulatorExt.step.download'), progress: 30 },
      { key: 'transcode', name: t('lotask.guides.demoSimulatorExt.step.transcode'), progress: 70 },
      { key: 'upload', name: t('lotask.guides.demoSimulatorExt.step.upload'), progress: 95 },
    ]
    for (const step of steps) {
      await new Promise(r => setTimeout(r, 1000))
      const progressReq: ReportProgressRequest = {
        currentStepKey: step.key,
        stepProgress: step.progress,
        executionToken: task.executionToken,
        version: task.version,
      }
      await reportProgress(id, progressReq)
      addLog('worker', `📊 ${t('lotask.guides.demoSimulatorExt.logStep', { name: step.name, progress: step.progress })}`)
      updateTask({ progress: step.progress, currentStep: step.name })
    }

    const isSuccess = Math.random() > 0.15
    if (isSuccess) {
      const result = generateResult(task.type || taskType.value)
      const resultReq: ReportResultRequest = {
        status: 'SUCCESS',
        result,
        executionToken: task.executionToken,
        version: task.version,
      }
      await reportResult(id, resultReq)
      addLog('worker', `✅ ${t('lotask.guides.demoSimulatorExt.execSuccess')}: ${id.substring(0, 8)}...`)
      taskStats.completed++
      updateTask({ status: 'SUCCESS', progress: 100, payload: { ...activeTasks.value.find(x => x.id === id)?.payload, result } as Record<string, unknown> })
    } else {
      const errorMsg = randomError()
      const resultReq: ReportResultRequest = {
        status: 'FAILED',
        errorMsg,
        executionToken: task.executionToken,
        version: task.version,
      }
      await reportResult(id, resultReq)
      addLog('worker', `❌ ${t('lotask.guides.demoSimulatorExt.execFailed')}: ${id.substring(0, 8)}... (${errorMsg})`)
      taskStats.failed++
      updateTask({ status: 'FAILED', progress: 0 })
    }

    setTimeout(() => {
      activeTasks.value = activeTasks.value.filter(x => x.id !== id)
    }, 3000)
  } catch (err) {
    addLog('worker', `⚠️ ${t('lotask.guides.demoSimulatorExt.workerError')}: ${(err as Error).message}`)
  }
}

function startClient() {
  isClientRunning.value = true
  addLog('system', `🚀 ${t('lotask.guides.demoSimulatorExt.client.started')}`)
  submitOneTask()
  clientTimer = window.setInterval(submitOneTask, clientInterval.value)
}

function stopClient() {
  if (clientTimer) {
    window.clearInterval(clientTimer)
    clientTimer = null
  }
  isClientRunning.value = false
  addLog('system', `⏹️ ${t('lotask.guides.demoSimulatorExt.client.stopped')}`)
}

function startWorker() {
  isWorkerRunning.value = true
  addLog('system', `🚀 ${t('lotask.guides.demoSimulatorExt.worker.started')}`)
  ensureWorkerRegistered()
  pollAndExecute()
  workerTimer = window.setInterval(pollAndExecute, workerInterval.value)
}

function stopWorker() {
  if (workerTimer) {
    window.clearInterval(workerTimer)
    workerTimer = null
  }
  isWorkerRunning.value = false
  addLog('system', `⏹️ ${t('lotask.guides.demoSimulatorExt.worker.stopped')}`)
}

function clearLogs() {
  logs.value = []
  taskStats.submitted = 0
  taskStats.completed = 0
  taskStats.failed = 0
  addLog('system', `🧹 ${t('lotask.guides.demoSimulatorExt.stats.logsCleared')}`)
  toast.success(t('lotask.guides.demoSimulatorExt.stats.logsCleared'))
}

onUnmounted(() => {
  if (clientTimer) window.clearInterval(clientTimer)
  if (workerTimer) window.clearInterval(workerTimer)
})
</script>

<style scoped lang="scss">
.lotask-demo-simulator {
  display: flex;
  flex-direction: column;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.control-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.control-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);

  i {
    font-size: 18px;
    color: var(--el-color-primary);
  }
}

.control-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.control-label {
  display: flex;
  flex-direction: column;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  gap: 4px;
}

.control-spacer {
  flex: 1;
  min-height: 76px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.stat-tile {
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  text-align: center;

  &--info    { background: var(--el-color-primary-light-9); }
  &--success { background: var(--el-color-success-light-9); }
  &--danger  { background: var(--el-color-danger-light-9); }

  .stat-value {
    font-size: 22px;
    font-weight: 600;
    line-height: 1.2;
  }

  .stat-label {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-top: 2px;
  }

  &--info    .stat-value { color: var(--el-color-primary); }
  &--success .stat-value { color: var(--el-color-success); }
  &--danger  .stat-value { color: var(--el-color-danger); }
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;

  i {
    font-size: 18px;
    color: var(--el-color-primary);
  }
}

.empty-tasks {
  text-align: center;
  padding: 32px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.tasks-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.active-task-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &.running { border-left-color: var(--el-color-primary); }
  &.success { border-left-color: var(--el-color-success); }
  &.failed  { border-left-color: var(--el-color-danger); }
  &.pending { border-left-color: var(--el-border-color); }
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 6px;
}

.task-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-id {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-family: 'SF Mono', Monaco, monospace;
}

.task-step {
  font-size: 11px;
  color: var(--el-text-color-regular);
  margin-top: 2px;
}

.log-panel {
  max-height: 400px;
  overflow-y: auto;
  padding: 4px 0;
}

.log-icon {
  margin-right: 6px;

  &.client { color: var(--el-color-primary); }
  &.worker { color: var(--el-color-success); }
  &.system { color: var(--el-color-warning); }
}

@media (max-width: 768px) {
  .control-grid {
    grid-template-columns: 1fr;
  }
}
</style>