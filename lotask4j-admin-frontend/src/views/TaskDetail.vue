<template>
  <div class="app-page">
    <div class="task-detail" v-loading="loading">
      <TitledSection :title="t('taskDetail.title')" icon="ri-file-info-line">
        <template #actions>
          <el-button @click="goBack">
            <el-icon><i class="ri-arrow-left-line" /></el-icon>
            <span style="margin-left: 4px">{{ t('taskDetail.back') }}</span>
          </el-button>
          <el-button :icon="Refresh" @click="loadTask">{{ t('taskDetail.refresh') }}</el-button>
          <el-button
            v-if="canCancel"
            type="danger"
            @click="cancelTask"
          >
            {{ t('taskDetail.cancel') }}
          </el-button>
        </template>

        <div v-if="!task" class="empty">
          <el-empty :description="t('taskDetail.noTask')" />
        </div>

        <div v-else>
          <!-- 警告横幅 -->
          <el-alert
            v-if="task.status === 'FAILED' && task.errorMsg"
            :title="t('taskDetailExt.alert.failedTitle')"
            :description="task.errorMsg"
            type="error"
            show-icon
            :closable="false"
            style="margin-bottom: 16px"
          />
          <el-alert
            v-else-if="task.status === 'CANCELLING'"
            :title="t('taskDetailExt.alert.cancellingTitle')"
            :description="t('taskDetailExt.alert.cancellingDesc')"
            type="warning"
            show-icon
            :closable="false"
            style="margin-bottom: 16px"
          />

          <!-- 顶部 4 个 Statistic -->
          <KpiLayout :columns="4" style="margin-bottom: 16px">
            <KpiSection
              :title="t('taskDetailExt.stats.status')"
              icon="ri-flag-line"
              :value="statusLabel(task.status)"
            />
            <KpiSection
              :title="t('taskDetailExt.stats.progress')"
              icon="ri-percent-line"
              :value="task.progress"
              unit="%"
            />
            <KpiSection
              :title="t('taskDetailExt.stats.priority')"
              icon="ri-play-circle-line"
              :value="task.priority ?? 0"
            />
            <KpiSection
              :title="t('taskDetailExt.stats.retryCount')"
              icon="ri-refresh-line"
              :value="task.retryCount ?? 0"
            />
          </KpiLayout>

          <!-- 运行中：当前步骤 + 大进度条 -->
          <WorkSection v-if="task.status === 'RUNNING'" class="running-progress">
            <div class="current-step">
              <strong>{{ t('taskDetailExt.currentStep') }}:</strong>
              <span>{{ task.currentStep || '—' }}</span>
            </div>
            <el-progress
              :percentage="task.progress"
              :stroke-width="14"
              status="active"
              :color="['#007aff', '#34c759']"
            />
          </WorkSection>

          <!-- 基本信息 -->
          <WorkSection :title="t('taskDetailExt.basicInfo')" icon="ri-information-line">
            <el-descriptions :column="2" border>
              <el-descriptions-item :label="t('taskDetail.field.id')" :span="2">
                <el-text class="task-id" @click="copyText(task.id)">
                  {{ task.id }}
                  <el-icon class="copy-icon"><i class="ri-file-copy-line" /></el-icon>
                </el-text>
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetail.field.type')">
                <div class="type-cell">
                  <div>{{ task.typeName || task.type }}</div>
                  <el-text type="info" size="small">{{ task.type }}</el-text>
                </div>
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetail.field.status')">
                <el-tag :type="statusTagType(task.status)" effect="light">
                  {{ statusLabel(task.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.stats.priority')">
                {{ task.priority ?? 0 }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.stats.retryCount')">
                {{ task.retryCount ?? 0 }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetail.field.progress')">{{ task.progress }}%</el-descriptions-item>
              <el-descriptions-item :label="t('taskDetail.field.currentStep')">{{ task.currentStep || '-' }}</el-descriptions-item>
            </el-descriptions>
          </WorkSection>

          <!-- 执行信息 -->
          <WorkSection :title="t('taskDetailExt.execInfo')" icon="ri-cpu-line">
            <el-descriptions :column="2" border>
              <el-descriptions-item :label="t('taskDetail.field.workerIp')">
                <el-tag v-if="task.workerIp" type="primary" effect="plain">{{ task.workerIp }}</el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.callbackStatus')">
                <el-tag
                  v-if="task.callbackStatus !== undefined && task.callbackStatus !== null"
                  :type="callbackStatusTag(task.callbackStatus)"
                  effect="plain"
                >
                  {{ callbackStatusLabel(task.callbackStatus) }}
                </el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.callbackUrl')" :span="2">
                <el-text v-if="task.callbackUrl" class="callback-url" @click="copyText(task.callbackUrl!)">
                  {{ task.callbackUrl }}
                  <el-icon class="copy-icon"><i class="ri-file-copy-line" /></el-icon>
                </el-text>
                <span v-else>-</span>
              </el-descriptions-item>
            </el-descriptions>
          </WorkSection>

          <!-- 时间信息 -->
          <WorkSection :title="t('taskDetailExt.timeInfo')" icon="ri-time-line">
            <el-descriptions :column="2" border>
              <el-descriptions-item :label="t('taskDetail.field.createdAt')">{{ formatTime(task.createdAt) }}</el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.time.updatedAt')">{{ formatTime(task.updatedAt) }}</el-descriptions-item>
              <el-descriptions-item :label="t('taskDetail.field.startedAt')">{{ formatTime(task.startedAt) }}</el-descriptions-item>
              <el-descriptions-item :label="t('taskDetail.field.finishedAt')">{{ formatTime(task.finishedAt) }}</el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.time.expiredAt')" :span="2">
                <template v-if="task.expiredAt">
                  <span>{{ formatTime(task.expiredAt) }}</span>
                  <el-tag
                    v-if="expiredInfo(task.expiredAt).isExpired"
                    type="danger" size="small" effect="plain" style="margin-left: 8px"
                  >
                    {{ t('taskListExt.column.expired') }}
                  </el-tag>
                  <el-tag
                    v-else-if="expiredInfo(task.expiredAt).isExpiring"
                    type="warning" size="small" effect="plain" style="margin-left: 8px"
                  >
                    {{ t('taskListExt.column.remaining') }} {{ expiredInfo(task.expiredAt).text }}
                  </el-tag>
                  <el-text v-else type="info" size="small" style="margin-left: 8px">
                    {{ t('taskListExt.column.remaining') }} {{ expiredInfo(task.expiredAt).text }}
                  </el-text>
                </template>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.time.waitingDuration')">{{ waitingDuration }}</el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.time.execDuration')">{{ execDuration }}</el-descriptions-item>
              <el-descriptions-item :label="t('taskDetailExt.time.totalDuration')" :span="2">
                <strong>{{ totalDuration }}</strong>
              </el-descriptions-item>
            </el-descriptions>
          </WorkSection>

          <!-- 步骤详情 -->
          <WorkSection :title="t('taskDetail.steps.title')" icon="ri-list-check-3">
            <el-empty
              v-if="!task.stepsDetail || task.stepsDetail.length === 0"
              :description="t('taskDetail.steps.empty')"
              :image-size="60"
            />
            <el-timeline v-else>
              <el-timeline-item
                v-for="(step, idx) in task.stepsDetail"
                :key="step.key || idx"
                :type="stepTimelineType(step.status)"
                :timestamp="stepTime(step)"
                :hollow="step.status !== 'finished' && step.status !== 'failed'"
              >
                <div class="step-title">
                  <span class="step-name">{{ idx + 1 }}. {{ step.name || step.key }}</span>
                  <el-tag size="small" :type="stepTagType(step.status)" effect="plain">
                    {{ stepStatusLabel(step.status) }}
                  </el-tag>
                </div>
                <div v-if="step.detail" class="step-detail">{{ step.detail }}</div>
                <el-progress
                  v-if="step.progress !== undefined"
                  :percentage="step.progress"
                  :stroke-width="6"
                  :status="step.status === 'processing' ? undefined : (step.status === 'finished' ? 'success' : step.status === 'failed' ? 'exception' : undefined)"
                  style="margin-top: 6px; max-width: 400px;"
                />
                <div class="step-meta">
                  <span v-if="step.end_time || step.endTime">
                    {{ t('taskDetailExt.steps.endTime') }}: {{ formatTime(step.end_time || step.endTime) }}
                  </span>
                  <span v-if="step.cost_ms !== undefined">
                    {{ t('taskDetailExt.steps.costMs') }}: {{ step.cost_ms }}ms
                  </span>
                </div>
              </el-timeline-item>
            </el-timeline>
          </WorkSection>

          <!-- 任务入参 -->
          <WorkSection v-if="task.payload" :title="t('taskDetail.payload')" icon="ri-file-code-line">
            <pre class="json-block">{{ formatJson(task.payload) }}</pre>
          </WorkSection>

          <!-- 执行结果 -->
          <WorkSection v-if="task.result" :title="t('taskDetail.result')" icon="ri-file-shield-2-line">
            <pre class="json-block">{{ formatJson(task.result) }}</pre>
          </WorkSection>

          <!-- 错误信息 -->
          <WorkSection
            v-if="task.errorMsg && task.status !== 'FAILED'"
            :title="t('taskDetail.error')"
            icon="ri-error-warning-line"
            class="error-section"
          >
            <el-alert :title="task.errorMsg" type="error" :closable="false" show-icon />
          </WorkSection>
        </div>
      </TitledSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getTaskDetail, cancelTask as cancelTaskApi, type Task } from '@/api/client'
import { formatTime, parseTime, formatDuration } from '@/utils/time'
import { formatExpiredTime } from '@/utils/taskTime'
import { usePolling } from '@/composables/usePolling'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import KpiLayout from '@/components/sdk/common/KpiLayout.vue'
import KpiSection from '@/components/sdk/common/KpiSection.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const task = ref<Task | null>(null)
usePolling(loadTask, {
  interval: 2000,
  predicate: () =>
    task.value?.status === 'RUNNING' || task.value?.status === 'PENDING',
})

const canCancel = computed(() => {
  const s = task.value?.status
  return s === 'PENDING' || s === 'RUNNING'
})

async function loadTask() {
  const taskId = route.params.id as string
  if (!taskId) {
    task.value = null
    return
  }
  loading.value = true
  try {
    const res = await getTaskDetail(taskId)
    task.value = res.data as any
  } catch (err: any) {
    ElMessage.error(err.message || t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/tasks')
}

async function cancelTask() {
  if (!task.value) return
  try {
    await ElMessageBox.confirm(
      t('taskList.action.confirming'),
      t('taskDetail.confirmCancel'),
      { type: 'warning' }
    )
  } catch { return }
  try {
    await cancelTaskApi(task.value.id)
    ElMessage.success(t('taskList.cancelSuccess'))
    loadTask()
  } catch (err: any) {
    ElMessage.error(err.message || t('common.cancelFailed'))
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('taskDetailExt.copySuccess'))
  } catch {
    ElMessage.warning(text)
  }
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'RUNNING': return 'warning'
    case 'FAILED': return 'danger'
    case 'CANCELLED': return 'info'
    default: return 'info'
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: t('taskList.filter.pending'),
    RUNNING: t('taskList.filter.running'),
    SUCCESS: t('taskList.filter.success'),
    FAILED: t('taskList.filter.failed'),
    CANCELLED: t('taskList.filter.cancelled'),
    CANCELLING: t('taskDetail.cancelling')
  }
  return map[status] || status
}

function callbackStatusLabel(status: number): string {
  const key = `taskDetailExt.callbackStatusMap.${status}` as const
  return t(key)
}

function callbackStatusTag(status: number): 'success' | 'danger' | 'info' {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

function progressStatus(status: string): '' | 'success' | 'exception' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'exception'
  return ''
}

function stepTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'finished': return 'success'
    case 'processing': return 'warning'
    case 'failed': return 'danger'
    default: return 'info'
  }
}

function stepTimelineType(status: string): 'success' | 'warning' | 'danger' | 'primary' {
  switch (status) {
    case 'finished': return 'success'
    case 'processing': return 'primary'
    case 'failed': return 'danger'
    default: return 'warning'
  }
}

function stepStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: t('taskDetailExt.stepStatus.pending'),
    processing: t('taskDetailExt.stepStatus.processing'),
    finished: t('taskDetailExt.stepStatus.finished'),
    failed: t('taskDetailExt.stepStatus.failed'),
  }
  return map[status] || status
}

function stepTime(step: any): string {
  return formatTime(step.start_time || step.startTime)
}

function expiredInfo(expiredAt: string) {
  return formatExpiredTime(expiredAt)
}

const waitingDuration = computed(() => {
  if (!task.value?.createdAt) return '-'
  const created = parseTime(task.value.createdAt)
  if (!created) return '-'
  if (task.value.startedAt) {
    const started = parseTime(task.value.startedAt)
    if (started) return formatDuration(started.getTime() - created.getTime())
  }
  if (task.value.status === 'PENDING') return formatDuration(Date.now() - created.getTime())
  return '-'
})

const execDuration = computed(() => {
  if (!task.value?.startedAt) return '-'
  const started = parseTime(task.value.startedAt)
  if (!started) return '-'
  if (task.value.status === 'RUNNING') return formatDuration(Date.now() - started.getTime())
  if (task.value.finishedAt) {
    const finished = parseTime(task.value.finishedAt)
    if (finished) return formatDuration(finished.getTime() - started.getTime())
  }
  return '-'
})

const totalDuration = computed(() => {
  if (!task.value?.createdAt) return '-'
  const created = parseTime(task.value.createdAt)
  if (!created) return '-'
  if (task.value.finishedAt) {
    const finished = parseTime(task.value.finishedAt)
    if (finished) return formatDuration(finished.getTime() - created.getTime())
  }
  return formatDuration(Date.now() - created.getTime())
})

function formatJson(obj: any) {
  if (!obj) return ''
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

watch(() => route.params.id, loadTask)
onMounted(() => {
  loadTask()
})
</script>

<style scoped lang="scss">
.task-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty {
  padding: 40px 0;
}

.running-progress {
  margin-bottom: 16px;

  .current-step {
    margin-bottom: 8px;
    font-size: 14px;

    strong { margin-right: 4px; }
  }
}

.task-id {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;

  .copy-icon {
    margin-left: 4px;
    font-size: 12px;
    opacity: 0.6;
  }
}

.type-cell {
  display: flex;
  flex-direction: column;
  font-size: 13px;
}

.callback-url {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  word-break: break-all;

  .copy-icon {
    margin-left: 4px;
    font-size: 12px;
    opacity: 0.6;
  }
}

.step-title {
  display: flex;
  align-items: center;
  gap: 8px;

  .step-name {
    font-weight: 600;
    font-size: 14px;
  }
}

.step-detail {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}

.step-meta {
  display: flex;
  gap: 16px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.json-block {
  padding: 12px 0;
  font-size: 12px;
  font-family: 'SF Mono', Monaco, monospace;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 400px;
  overflow-y: auto;
}

.error-section {
  border-left: 3px solid var(--el-color-danger);
}
</style>
