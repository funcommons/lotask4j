<template>
  <div class="lotask-page lotask-detail">
    <FcSectionHeader
      :title="t('lotask.tasks.detail.title')"
      :back="true"
      @back="goBack"
    >
      <template #actions>
        <FcButton variant="secondary" :loading="loading" @click="loadTask">
          {{ t('lotask.tasks.detail.refresh') }}
        </FcButton>
        <FcButton
          v-if="canCancel"
          variant="danger"
          @click="cancelTask"
        >
          {{ t('lotask.tasks.detail.cancel') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <FcSection v-if="!task" padding="md" shadow="sm">
      <FcEmpty
        v-if="!loading"
        type="empty"
        :description="t('lotask.tasks.detail.noTask')"
      />
    </FcSection>

    <template v-else>
      <!-- 警告横幅 -->
      <FcSection padding="md" shadow="sm">
        <el-alert
          v-if="task.status === 'FAILED' && task.errorMsg"
          :title="t('lotask.tasks.detailExt.alert.failedTitle')"
          :description="task.errorMsg"
          type="error"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else-if="task.status === 'CANCELLING'"
          :title="t('lotask.tasks.detailExt.alert.cancellingTitle')"
          :description="t('lotask.tasks.detailExt.alert.cancellingDesc')"
          type="warning"
          show-icon
          :closable="false"
        />
      </FcSection>

      <!-- 顶部 4 KPI -->
      <FcSection padding="md" shadow="sm">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="kpi-tile">
              <i class="ri-flag-line kpi-tile__icon" />
              <div class="kpi-tile__body">
                <div class="kpi-tile__title">{{ t('lotask.tasks.detailExt.stats.status') }}</div>
                <div class="kpi-tile__value">{{ statusLabel(task.status) }}</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="kpi-tile">
              <i class="ri-percent-line kpi-tile__icon" />
              <div class="kpi-tile__body">
                <div class="kpi-tile__title">{{ t('lotask.tasks.detailExt.stats.progress') }}</div>
                <div class="kpi-tile__value">{{ task.progress }}%</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="kpi-tile">
              <i class="ri-play-circle-line kpi-tile__icon" />
              <div class="kpi-tile__body">
                <div class="kpi-tile__title">{{ t('lotask.tasks.detailExt.stats.priority') }}</div>
                <div class="kpi-tile__value">{{ task.priority ?? 0 }}</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="kpi-tile">
              <i class="ri-refresh-line kpi-tile__icon" />
              <div class="kpi-tile__body">
                <div class="kpi-tile__title">{{ t('lotask.tasks.detailExt.stats.retryCount') }}</div>
                <div class="kpi-tile__value">{{ task.attempt ?? 0 }} / {{ task.maxAttempts ?? 0 }}</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </FcSection>

      <!-- RUNNING: 当前步骤 + 大进度条 -->
      <FcSection v-if="task.status === 'RUNNING'" padding="md" shadow="sm">
        <div class="running-progress">
          <div class="current-step">
            <strong>{{ t('lotask.tasks.detailExt.currentStep') }}:</strong>
            <span>{{ task.currentStep || '—' }}</span>
          </div>
          <el-progress
            :percentage="task.progress"
            :stroke-width="14"
            status="active"
            :color="['#007aff', '#34c759']"
          />
        </div>
      </FcSection>

      <!-- 基本信息 -->
      <FcSection padding="md" shadow="sm">
        <template #header>
          <div class="section-subhead">
            <i class="ri-information-line" />
            <span>{{ t('lotask.tasks.detailExt.basicInfo') }}</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.id')" :span="2">
            <span class="task-id" @click="copyText(task.id)">
              {{ task.id }}
              <i class="ri-file-copy-line copy-icon" />
            </span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.type')">
            <div class="type-cell">
              <div>{{ task.typeName || task.type }}</div>
              <span class="type-key">{{ task.type }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.status')">
            <FcTag :color="statusColor(task.status)" size="sm">
              {{ statusLabel(task.status) }}
            </FcTag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.stats.priority')">
            {{ task.priority ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.stats.retryCount')">
            {{ task.attempt ?? 0 }} / {{ task.maxAttempts ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.progress')">
            {{ task.progress }}%
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.currentStep')">
            {{ task.currentStep || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </FcSection>

      <!-- 执行信息 -->
      <FcSection padding="md" shadow="sm">
        <template #header>
          <div class="section-subhead">
            <i class="ri-cpu-line" />
            <span>{{ t('lotask.tasks.detailExt.execInfo') }}</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.workerIp')">
            <FcTag v-if="task.workerIp" color="brand" size="sm">
              {{ task.workerIp }}
            </FcTag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.callbackStatus')">
            <template v-if="task.callbackStatus !== undefined && task.callbackStatus !== null">
              <FcTag :color="callbackStatusColor(task.callbackStatus)" size="sm">
                {{ callbackStatusLabel(task.callbackStatus) }}
              </FcTag>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.callbackUrl')" :span="2">
            <span v-if="task.callbackUrl" class="callback-url" @click="copyText(task.callbackUrl!)">
              {{ task.callbackUrl }}
              <i class="ri-file-copy-line copy-icon" />
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
      </FcSection>

      <!-- 时间信息 -->
      <FcSection padding="md" shadow="sm">
        <template #header>
          <div class="section-subhead">
            <i class="ri-time-line" />
            <span>{{ t('lotask.tasks.detailExt.timeInfo') }}</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.createdAt')">
            {{ formatDateTime(task.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.time.updatedAt')">
            {{ formatDateTime(task.updatedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.startedAt')">
            {{ formatDateTime(task.startedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detail.field.finishedAt')">
            {{ formatDateTime(task.finishedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.time.expiredAt')" :span="2">
            <template v-if="task.expiredAt">
              <span>{{ formatDateTime(task.expiredAt) }}</span>
              <template v-if="remainingLabel(task.expiredAt).expired">
                <FcTag color="danger" size="sm" class="expired-tag">
                  {{ t('lotask.tasks.listExt.column.expired') }}
                </FcTag>
              </template>
              <template v-else-if="remainingLabel(task.expiredAt).urgent">
                <FcTag color="warning" size="sm" class="expired-tag">
                  {{ t('lotask.tasks.listExt.column.remaining') }} {{ remainingLabel(task.expiredAt).text }}
                </FcTag>
              </template>
              <span v-else class="expired-text expired-tag">
                {{ t('lotask.tasks.listExt.column.remaining') }} {{ remainingLabel(task.expiredAt).text }}
              </span>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.time.waitingDuration')">
            {{ waitingDuration }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.time.execDuration')">
            {{ execDuration }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.detailExt.time.totalDuration')" :span="2">
            <strong>{{ totalDuration }}</strong>
          </el-descriptions-item>
        </el-descriptions>
      </FcSection>

      <!-- 步骤详情 -->
      <FcSection padding="md" shadow="sm">
        <template #header>
          <div class="section-subhead">
            <i class="ri-list-check-3" />
            <span>{{ t('lotask.tasks.detail.steps.title') }}</span>
          </div>
        </template>
        <FcEmpty
          v-if="!task.stepsDetail || task.stepsDetail.length === 0"
          type="empty"
          :description="t('lotask.tasks.detail.steps.empty')"
        />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(step, idx) in task.stepsDetail"
            :key="step.key || idx"
            :type="stepTimelineType(step.status)"
            :timestamp="formatDateTime(step.start_time)"
            :hollow="step.status !== 'finished' && step.status !== 'failed'"
          >
            <div class="step-title">
              <span class="step-name">{{ idx + 1 }}. {{ step.name || step.key }}</span>
              <FcTag :color="stepColor(step.status)" size="sm">
                {{ stepStatusLabel(step.status) }}
              </FcTag>
            </div>
            <div v-if="step.detail" class="step-detail">{{ step.detail }}</div>
            <el-progress
              v-if="step.progress !== undefined"
              :percentage="step.progress"
              :stroke-width="6"
              :status="stepProgressStatus(step.status)"
              class="step-progress"
            />
            <div class="step-meta">
              <span v-if="step.end_time">
                {{ t('lotask.tasks.detailExt.steps.endTime') }}: {{ formatDateTime(step.end_time) }}
              </span>
              <span v-if="step.cost_ms !== undefined">
                {{ t('lotask.tasks.detailExt.steps.costMs') }}: {{ step.cost_ms }}ms
              </span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </FcSection>

      <!-- 任务入参 -->
      <FcSection v-if="task.payload" padding="md" shadow="sm">
        <template #header>
          <div class="section-subhead">
            <i class="ri-file-code-line" />
            <span>{{ t('lotask.tasks.detail.payload') }}</span>
          </div>
        </template>
        <pre class="json-block">{{ formatJson(task.payload) }}</pre>
      </FcSection>

      <!-- 执行结果 -->
      <FcSection v-if="task.result" padding="md" shadow="sm">
        <template #header>
          <div class="section-subhead">
            <i class="ri-file-shield-2-line" />
            <span>{{ t('lotask.tasks.detail.result') }}</span>
          </div>
        </template>
        <pre class="json-block">{{ formatJson(task.result) }}</pre>
      </FcSection>

      <!-- 错误信息 -->
      <FcSection
        v-if="task.errorMsg && task.status !== 'FAILED'"
        padding="md"
        shadow="sm"
        class="error-section"
      >
        <template #header>
          <div class="section-subhead section-subhead--error">
            <i class="ri-error-warning-line" />
            <span>{{ t('lotask.tasks.detail.error') }}</span>
          </div>
        </template>
        <el-alert :title="task.errorMsg" type="error" :closable="false" show-icon />
      </FcSection>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { getTaskDetail, cancelTask as cancelTaskApi } from '@/api/client'
import type { TaskDetail, StepStatus, TaskStatus } from '@/api/types'
import {
  formatDateTime,
  formatDuration,
  remainingLabel,
  TASK_STATUS_TAG_TYPE,
  STEP_STATUS_TAG_TYPE
} from '@/utils/taskStatus'
import { usePolling, useClipboard } from '@/composables'
import { toast } from '@/components/sdk'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcEmpty from '@/components/sdk/display/FcEmpty.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskTaskDetailPage' })

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { copy } = useClipboard()

const loading = ref(false)
const task = ref<TaskDetail | null>(null)

// 动态秒表: 每秒刷新当前执行时长
const tick = ref(0)
const tickTimer = window.setInterval(() => { tick.value++ }, 1000)

usePolling(loadTask, {
  interval: 2000,
  predicate: () =>
    task.value?.status === 'RUNNING' || task.value?.status === 'PENDING'
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
    task.value = res as TaskDetail
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    toast.error(msg || t('lotask.tasks.detail.noTask'))
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
    await toast.confirm({
      title: t('lotask.tasks.detail.confirmCancel'),
      message: t('lotask.tasks.list.action.confirming'),
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await cancelTaskApi(task.value.id)
    toast.success(t('lotask.tasks.list.cancelSuccess'))
    loadTask()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    toast.error(msg || t('lotask.tasks.list.action.cancel'))
  }
}

async function copyText(text: string) {
  await copy(text)
}

function statusLabel(status: TaskStatus): string {
  const map: Record<TaskStatus, string> = {
    PENDING: t('lotask.tasks.list.filter.pending'),
    RUNNING: t('lotask.tasks.list.filter.running'),
    SUCCESS: t('lotask.tasks.list.filter.success'),
    FAILED: t('lotask.tasks.list.filter.failed'),
    CANCELLED: t('lotask.tasks.list.filter.cancelled'),
    CANCELLING: t('lotask.tasks.detail.cancelling')
  }
  return map[status] || status
}

function callbackStatusLabel(status: number): string {
  if (status === 1) return t('lotask.tasks.detailExt.callbackStatusMap.1')
  if (status === 2) return t('lotask.tasks.detailExt.callbackStatusMap.2')
  return t('lotask.tasks.detailExt.callbackStatusMap.0')
}

function callbackStatusColor(status: number) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'gray'
}

function statusColor(status: TaskStatus) {
  // FcTag 仅支持 6 色 brand palette, 把 el-tag 的 primary/info 收敛
  const c = TASK_STATUS_TAG_TYPE[status]
  if (c === 'primary') return 'brand'
  if (c === 'info')    return 'gray'
  return c
}

function stepColor(status: StepStatus) {
  const c = STEP_STATUS_TAG_TYPE[status]
  if (c === 'info') return 'gray'
  return c
}

function stepTimelineType(status: StepStatus): 'success' | 'warning' | 'danger' | 'primary' {
  switch (status) {
    case 'finished':   return 'success'
    case 'processing': return 'primary'
    case 'failed':     return 'danger'
    default:           return 'warning'
  }
}

function stepStatusLabel(status: StepStatus): string {
  const map: Record<StepStatus, string> = {
    pending:    t('lotask.tasks.detailExt.stepStatus.pending'),
    processing: t('lotask.tasks.detailExt.stepStatus.processing'),
    finished:   t('lotask.tasks.detailExt.stepStatus.finished'),
    failed:     t('lotask.tasks.detailExt.stepStatus.failed')
  }
  return map[status] || status
}

function stepProgressStatus(status: StepStatus): '' | 'success' | 'exception' {
  if (status === 'finished') return 'success'
  if (status === 'failed')   return 'exception'
  return ''
}

// 等待时长 (created→started) / 执行时长 (started→finished|now) / 总耗时
const waitingDuration = computed(() => {
  if (!task.value?.createdAt) return '-'
  const created = new Date(task.value.createdAt).getTime()
  if (!created) return '-'
  if (task.value.startedAt) {
    const started = new Date(task.value.startedAt).getTime()
    if (started) return formatDuration(started - created)
  }
  if (task.value.status === 'PENDING') return formatDuration(Date.now() - created)
  return '-'
})

const execDuration = computed(() => {
  if (!task.value?.startedAt) return '-'
  const started = new Date(task.value.startedAt).getTime()
  if (!started) return '-'
  if (task.value.status === 'RUNNING') {
    // consume tick to make it reactive each second
    void tick.value
    return formatDuration(Date.now() - started)
  }
  if (task.value.finishedAt) {
    const finished = new Date(task.value.finishedAt).getTime()
    if (finished) return formatDuration(finished - started)
  }
  return '-'
})

const totalDuration = computed(() => {
  if (!task.value?.createdAt) return '-'
  const created = new Date(task.value.createdAt).getTime()
  if (!created) return '-'
  if (task.value.finishedAt) {
    const finished = new Date(task.value.finishedAt).getTime()
    if (finished) return formatDuration(finished - created)
  }
  void tick.value
  return formatDuration(Date.now() - created)
})

function formatJson(obj: unknown): string {
  if (obj === undefined || obj === null) return ''
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

watch(() => route.params.id, () => {
  loadTask()
})

onMounted(() => {
  loadTask()
})

onBeforeUnmount(() => {
  clearInterval(tickTimer)
})
</script>

<style scoped lang="scss">
.lotask-detail {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.kpi-tile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  height: 100%;

  &__icon { font-size: 26px; color: var(--el-color-primary); flex-shrink: 0; }
  &__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  &__title { font-size: 12px; color: var(--el-text-color-secondary); }
  &__value { font-size: 20px; font-weight: 600; color: var(--el-text-color-primary); line-height: 1.2; }
}

.section-subhead {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);

  i {
    font-size: 16px;
    color: var(--el-color-primary);
  }

  &--error i { color: var(--el-color-danger); }
}

.running-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .current-step {
    font-size: 14px;
    color: var(--el-text-color-regular);

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

  .type-key {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
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

.expired-tag { margin-left: 8px; }
.expired-text { font-size: 12px; color: var(--el-text-color-secondary); }

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

.step-progress {
  margin-top: 6px;
  max-width: 400px;
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