<template>
  <div class="app-page">
    <TitledSection :title="t('demoSimulatorExt.title')" icon="ri-flask-line">
      <WorkSection>
        <el-alert
          :title="t('demoSimulatorExt.envWarning')"
          :description="t('demoSimulatorExt.envWarningDesc')"
          type="warning"
          show-icon
          :closable="true"
          style="margin-bottom: 16px"
        />

        <el-row :gutter="16">
          <!-- Client 模拟器 -->
          <el-col :span="8">
            <div class="control-card">
              <div class="control-title">{{ t('demoSimulatorExt.client.title') }}</div>
              <el-form label-position="top">
                <el-form-item :label="t('demoSimulatorExt.client.taskType')">
                  <el-select v-model="taskType" :disabled="isClientRunning" style="width: 100%">
                    <el-option
                      v-for="(label, key) in taskTypeOptions"
                      :key="key"
                      :label="label"
                      :value="key"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item :label="t('demoSimulatorExt.client.submitInterval')">
                  <el-input-number v-model="clientInterval" :disabled="isClientRunning" :min="1000" :step="1000" style="width: 100%" />
                </el-form-item>
                <el-button
                  v-if="!isClientRunning"
                  type="primary"
                  :icon="VideoPlay"
                  @click="startClient"
                >
                  {{ t('demoSimulatorExt.client.start') }}
                </el-button>
                <el-button
                  v-else
                  type="danger"
                  :icon="VideoPause"
                  @click="stopClient"
                >
                  {{ t('demoSimulatorExt.client.stop') }}
                </el-button>
              </el-form>
            </div>
          </el-col>

          <!-- Worker 模拟器 -->
          <el-col :span="8">
            <div class="control-card">
              <div class="control-title">{{ t('demoSimulatorExt.worker.title') }}</div>
              <el-form label-position="top">
                <el-form-item :label="t('demoSimulatorExt.worker.pollInterval')">
                  <el-input-number v-model="workerInterval" :disabled="isWorkerRunning" :min="1000" :step="1000" style="width: 100%" />
                </el-form-item>
                <div class="worker-spacer"></div>
                <el-button
                  v-if="!isWorkerRunning"
                  type="primary"
                  :icon="VideoPlay"
                  @click="startWorker"
                >
                  {{ t('demoSimulatorExt.worker.start') }}
                </el-button>
                <el-button
                  v-else
                  type="danger"
                  :icon="VideoPause"
                  @click="stopWorker"
                >
                  {{ t('demoSimulatorExt.worker.stop') }}
                </el-button>
              </el-form>
            </div>
          </el-col>

          <!-- 统计 -->
          <el-col :span="8">
            <div class="control-card">
              <div class="control-title">{{ t('demoSimulatorExt.stats.title') }}</div>
              <KpiLayout :columns="3">
                <KpiSection
                  :title="t('demoSimulatorExt.stats.submitted')"
                  :value="taskStats.submitted"
                />
                <KpiSection
                  :title="t('demoSimulatorExt.stats.completed')"
                  :value="taskStats.completed"
                />
                <KpiSection
                  :title="t('demoSimulatorExt.stats.failed')"
                  :value="taskStats.failed"
                />
              </KpiLayout>
              <el-button
                :icon="Delete"
                @click="clearLogs"
                style="margin-top: 16px; width: 100%"
              >
                {{ t('demoSimulatorExt.stats.clearLogs') }}
              </el-button>
            </div>
          </el-col>
        </el-row>
      </WorkSection>

      <!-- 活跃任务 -->
      <WorkSection :title="t('demoSimulatorExt.activeTasks')" icon="ri-list-check" style="margin-top: 16px">
        <div v-if="activeTasks.length === 0" class="empty-tasks">
          {{ t('demoSimulatorExt.noActiveTasks') }}
        </div>
        <el-row v-else :gutter="[12, 12]">
          <el-col
            v-for="task in activeTasks"
            :key="task.id"
            :xs="12" :sm="8" :md="6"
          >
            <div class="active-task-card" :class="task.status.toLowerCase()">
              <div class="task-header">
                <div>
                  <div class="task-name">{{ task.typeName || task.type }}</div>
                  <div class="task-id">{{ task.id.substring(0, 12) }}...</div>
                </div>
                <el-tag :type="taskTagType(task.status)" effect="light" size="small">
                  {{ task.status }}
                </el-tag>
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
          </el-col>
        </el-row>
      </WorkSection>

      <!-- 日志 -->
      <WorkSection style="margin-top: 16px">
        <template #title>
          <span>{{ t('demoSimulatorExt.logs') }}</span>
          <el-tag type="success" effect="plain" size="small" style="margin-left: 8px">
            <el-icon><i class="ri-flashlight-line" /></el-icon>
            {{ t('demoSimulatorExt.logsRealtime') }}
          </el-tag>
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
      </WorkSection>
    </TitledSection>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { VideoPlay, VideoPause, Delete } from '@element-plus/icons-vue'
import axios from 'axios'
import { formatTime, toGMT8ISO, nowGMT8 } from '@/utils/time'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import KpiLayout from '@/components/sdk/common/KpiLayout.vue'
import KpiSection from '@/components/sdk/common/KpiSection.vue'

const { t } = useI18n()

interface SimLog {
  time: string
  type: 'client' | 'worker' | 'system'
  message: string
}

interface ActiveTask {
  id: string
  type: string
  typeName?: string
  status: string
  progress: number
  currentStep?: string
  expiredAt?: string
  payload?: any
  result?: any
}

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

const taskTypeOptions = {
  video_transcode: t('demoSimulatorExt.taskTypeName.video_transcode'),
  data_export: t('demoSimulatorExt.taskTypeName.data_export'),
  image_process: t('demoSimulatorExt.taskTypeName.image_process'),
  pdf_generate: t('demoSimulatorExt.taskTypeName.pdf_generate')
}

function addLog(type: 'client' | 'worker' | 'system', message: string) {
  const time = formatTime(nowGMT8()).split(' ')[1]
  logs.value = [...logs.value.slice(-49), { time, type, message }]
}

function logIcon(type: string): string {
  return type === 'client' ? '📤' : type === 'worker' ? '⚙️' : '💻'
}

function logTimelineType(type: string): 'success' | 'warning' | 'primary' {
  return type === 'client' ? 'primary' : type === 'worker' ? 'success' : 'warning'
}

function generatePayload(type: string): any {
  const r = Math.floor(Math.random() * 1000)
  switch (type) {
    case 'video_transcode':
      return {
        url: `http://oss.example.com/videos/input_${r}.mp4`,
        format: ['720p', '1080p', '4K'][Math.floor(Math.random() * 3)],
        bitrate: ['1500k', '2000k', '3000k', '5000k'][Math.floor(Math.random() * 4)],
        codec: ['h264', 'h265', 'av1'][Math.floor(Math.random() * 3)],
        watermark: Math.random() > 0.5 ? `wm_${r}.png` : null,
        duration: Math.floor(Math.random() * 3600) + 60
      }
    case 'data_export':
      return {
        exportType: ['excel', 'csv', 'pdf'][Math.floor(Math.random() * 3)],
        dateRange: `2024-0${Math.floor(Math.random() * 9) + 1}-01,2024-0${Math.floor(Math.random() * 9) + 1}-30`,
        filters: {
          status: ['active', 'pending', 'completed'][Math.floor(Math.random() * 3)],
          category: `cat_${Math.floor(Math.random() * 10)}`,
          minAmount: Math.floor(Math.random() * 1000)
        },
        columns: ['id', 'name', 'amount', 'status'],
        compress: Math.random() > 0.5
      }
    case 'image_process':
      return {
        url: `http://oss.example.com/images/raw_${r}.jpg`,
        operations: [
          { type: 'resize', width: 800, height: 600 },
          { type: 'watermark', text: `© ${new Date().getFullYear()}` },
          { type: 'format', target: 'webp' }
        ],
        quality: Math.floor(Math.random() * 30) + 70,
        outputFormat: ['jpg', 'png', 'webp'][Math.floor(Math.random() * 3)]
      }
    case 'pdf_generate':
      return {
        templateId: `tpl_${Math.floor(Math.random() * 100)}`,
        data: {
          title: `Report_${r}`,
          author: t(`demoSimulatorExt.author${Math.floor(Math.random() * 3) + 1}`)
        },
        format: ['A4', 'A3', 'Letter'][Math.floor(Math.random() * 3)],
        orientation: ['portrait', 'landscape'][Math.floor(Math.random() * 2)],
        includeWatermark: Math.random() > 0.5
      }
    default:
      return { id: `task_${Date.now()}_${r}` }
  }
}

function generateResult(type: string): any {
  const ts = toGMT8ISO(nowGMT8()) || ''
  switch (type) {
    case 'video_transcode':
      return {
        output_url: `http://oss.example.com/videos/output_${Date.now()}.mp4`,
        duration: Math.floor(Math.random() * 3600) + 60,
        size: Math.floor(Math.random() * 50000000) + 5000000,
        codec: ['h264', 'h265', 'av1'][Math.floor(Math.random() * 3)],
        resolution: ['720p', '1080p', '4K'][Math.floor(Math.random() * 3)],
        processed_at: ts
      }
    case 'data_export':
      return {
        download_url: `http://cdn.example.com/exports/export_${Date.now()}.xlsx`,
        total_records: Math.floor(Math.random() * 100000) + 1000,
        file_size: Math.floor(Math.random() * 10000000) + 100000,
        columns: Math.floor(Math.random() * 20) + 5,
        processed_at: ts
      }
    case 'image_process':
      return {
        output_url: `http://cdn.example.com/images/processed_${Date.now()}.webp`,
        original_size: Math.floor(Math.random() * 5000000) + 500000,
        output_size: Math.floor(Math.random() * 1000000) + 100000,
        width: 800,
        height: 600,
        processed_at: ts
      }
    case 'pdf_generate':
      return {
        download_url: `http://cdn.example.com/pdfs/report_${Date.now()}.pdf`,
        page_count: Math.floor(Math.random() * 50) + 10,
        file_size: Math.floor(Math.random() * 5000000) + 500000,
        processed_at: ts
      }
    default:
      return { status: 'completed', timestamp: ts }
  }
}

const errorKeys = ['transcode', 'download', 'memory', 'format', 'permission', 'quota', 'busy'] as const
function randomError(): string {
  const key = errorKeys[Math.floor(Math.random() * errorKeys.length)]
  return t(`demoSimulatorExt.errors.${key}`)
}

async function submitOneTask() {
  try {
    const payload = generatePayload(taskType.value)
    const priority = Math.floor(Math.random() * 100)
    const hasCallback = Math.random() > 0.7
    const data: any = { type: taskType.value, payload, priority }
    if (hasCallback) data.callbackUrl = `https://webhook.example.com/cb/${Math.floor(Math.random() * 1000)}`
    const res = await axios.post('/api/v1/client/tasks', data)
    const id = res.data?.data?.id
    if (!id) return
    addLog('client', `✅ ${t('demoSimulatorExt.submitSuccess')}: ${id.substring(0, 8)}... [${t('demoSimulatorExt.client.taskType')}:${taskType.value}, ${t('taskDetailExt.stats.priority')}:${priority}${hasCallback ? ', callback' : ''}]`)
    taskStats.submitted++
    try {
      const detailRes = await axios.get(`/api/v1/client/tasks/${id}`)
      const detail = detailRes.data?.data
      activeTasks.value.push({
        id,
        type: taskType.value,
        typeName: taskTypeOptions[taskType.value as keyof typeof taskTypeOptions],
        status: 'PENDING',
        progress: 0,
        expiredAt: detail?.expiredAt,
        payload: detail?.payload
      })
    } catch {
      activeTasks.value.push({
        id,
        type: taskType.value,
        typeName: taskTypeOptions[taskType.value as keyof typeof taskTypeOptions],
        status: 'PENDING',
        progress: 0
      })
    }
  } catch (err: any) {
    addLog('client', `❌ ${t('demoSimulatorExt.submitFailed')}: ${err.message}`)
  }
}

async function pollAndExecute() {
  try {
    const pollRes = await axios.post('/api/v1/worker/tasks/poll', {
      taskType: taskType.value,
      strategy: 'PRIORITY'
    })
    const task = pollRes.data?.data
    if (!task) {
      addLog('worker', `⏳ ${t('demoSimulatorExt.worker.noTask')}`)
      return
    }
    const id = task.id
    addLog('worker', `🔨 ${t('demoSimulatorExt.worker.begin')}: ${id.substring(0, 8)}...`)

    let detail: any = null
    try {
      const dr = await axios.get(`/api/v1/client/tasks/${id}`)
      detail = dr.data?.data
    } catch {}

    const idx = activeTasks.value.findIndex(x => x.id === id)
    const typeName = detail?.typeName || taskTypeOptions[taskType.value as keyof typeof taskTypeOptions]
    const updateTask = (patch: Partial<ActiveTask>) => {
      const i = activeTasks.value.findIndex(x => x.id === id)
      if (i >= 0) {
        activeTasks.value[i] = { ...activeTasks.value[i], ...patch }
      } else {
        activeTasks.value.push({
          id,
          type: detail?.type || taskType.value,
          typeName,
          status: 'RUNNING',
          progress: 0,
          ...patch
        } as ActiveTask)
      }
    }
    if (idx >= 0) {
      updateTask({ status: 'RUNNING', progress: 0, payload: detail?.payload, expiredAt: detail?.expiredAt, typeName })
    } else {
      updateTask({ status: 'RUNNING', progress: 0, payload: detail?.payload, expiredAt: detail?.expiredAt, typeName })
    }

    const steps = [
      { key: 'init', name: t('demoSimulatorExt.step.init'), progress: 10 },
      { key: 'download', name: t('demoSimulatorExt.step.download'), progress: 30 },
      { key: 'transcode', name: t('demoSimulatorExt.step.transcode'), progress: 70 },
      { key: 'upload', name: t('demoSimulatorExt.step.upload'), progress: 95 }
    ]
    for (const step of steps) {
      await new Promise(r => setTimeout(r, 1000))
      await axios.post(`/api/v1/worker/tasks/${id}/progress`, {
        currentStepKey: step.key,
        stepProgress: step.progress
      })
      addLog('worker', `📊 ${t('demoSimulatorExt.logStep', { name: step.name, progress: step.progress })}`)
      updateTask({ progress: step.progress, currentStep: step.name })
    }

    const isSuccess = Math.random() > 0.15
    if (isSuccess) {
      const result = generateResult(task.type || taskType.value)
      await axios.post(`/api/v1/worker/tasks/${id}/result`, { status: 'SUCCESS', result })
      addLog('worker', `✅ ${t('demoSimulatorExt.execSuccess')}: ${id.substring(0, 8)}...`)
      taskStats.completed++
      updateTask({ status: 'SUCCESS', progress: 100, result })
    } else {
      const errorMsg = randomError()
      await axios.post(`/api/v1/worker/tasks/${id}/result`, { status: 'FAILED', errorMsg })
      addLog('worker', `❌ ${t('demoSimulatorExt.execFailed')}: ${id.substring(0, 8)}... (${errorMsg})`)
      taskStats.failed++
      updateTask({ status: 'FAILED', progress: 0 })
    }

    setTimeout(() => {
      activeTasks.value = activeTasks.value.filter(x => x.id !== id)
    }, 3000)
  } catch (err: any) {
    if (err.response?.status === 404 || err.response?.data?.code === 20101) return
    addLog('worker', `⚠️ ${t('demoSimulatorExt.workerError')}: ${err.message}`)
  }
}

function startClient() {
  isClientRunning.value = true
  addLog('system', `🚀 ${t('demoSimulatorExt.client.started')}`)
  submitOneTask()
  clientTimer = window.setInterval(submitOneTask, clientInterval.value)
}

function stopClient() {
  if (clientTimer) { clearInterval(clientTimer); clientTimer = null }
  isClientRunning.value = false
  addLog('system', `⏹️ ${t('demoSimulatorExt.client.stopped')}`)
}

function startWorker() {
  isWorkerRunning.value = true
  addLog('system', `🚀 ${t('demoSimulatorExt.worker.started')}`)
  pollAndExecute()
  workerTimer = window.setInterval(pollAndExecute, workerInterval.value)
}

function stopWorker() {
  if (workerTimer) { clearInterval(workerTimer); workerTimer = null }
  isWorkerRunning.value = false
  addLog('system', `⏹️ ${t('demoSimulatorExt.worker.stopped')}`)
}

function clearLogs() {
  logs.value = []
  taskStats.submitted = 0
  taskStats.completed = 0
  taskStats.failed = 0
  addLog('system', `🧹 ${t('demoSimulatorExt.stats.logsCleared')}`)
}

function taskTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'RUNNING': return 'warning'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

onUnmounted(() => {
  if (clientTimer) clearInterval(clientTimer)
  if (workerTimer) clearInterval(workerTimer)
})
</script>

<style scoped lang="scss">
.control-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
  height: 100%;
}

.control-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
}

.worker-spacer {
  height: 32px;
}

.empty-tasks {
  text-align: center;
  padding: 32px;
  color: var(--el-text-color-secondary);
}

.active-task-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;
  height: 100%;

  &.running { border-left: 3px solid var(--el-color-primary); }
  &.success { border-left: 3px solid var(--el-color-success); }
  &.failed { border-left: 3px solid var(--el-color-danger); }
  &.pending { border-left: 3px solid var(--el-border-color); }
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;

  .task-name {
    font-size: 13px;
    font-weight: 600;
  }

  .task-id {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    font-family: 'SF Mono', Monaco, monospace;
  }
}

.task-step {
  font-size: 11px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
}

.log-panel {
  max-height: 400px;
  overflow-y: auto;
  padding: 12px 0;
}

.log-icon {
  margin-right: 6px;

  &.client { color: var(--el-color-primary); }
  &.worker { color: var(--el-color-success); }
  &.system { color: var(--el-color-warning); }
}
</style>
