<template>
  <div class="lotask-page lotask-active">
    <FcSectionHeader
      :title="t('router.active-tasks')"
      :back="true"
      @back="router.back()"
    >
      <template #actions>
        <FcButton variant="secondary" :loading="loading" @click="loadTasks">
          {{ t('lotask.tasks.list.action.refresh') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <!-- 顶部 3 KPI -->
    <FcSection padding="md" shadow="sm">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <div class="kpi-tile kpi-tile--warning">
            <i class="ri-loader-4-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.active.stats.running') }}</div>
              <div class="kpi-tile__value">{{ stats.running }}</div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="8">
          <div class="kpi-tile kpi-tile--info">
            <i class="ri-time-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.active.stats.pending') }}</div>
              <div class="kpi-tile__value">{{ stats.pending }}</div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="8">
          <div class="kpi-tile kpi-tile--primary">
            <i class="ri-flashlight-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.active.stats.total') }}</div>
              <div class="kpi-tile__value">
                {{ tasks.length }}
                <span class="kpi-tile__suf">/ 50</span>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </FcSection>

    <FcSection padding="md" shadow="sm">
      <FcEmpty
        v-if="tasks.length === 0 && !loading"
        type="empty"
        :description="t('lotask.tasks.active.empty')"
      />

      <el-row v-else :gutter="16" class="tasks-grid">
        <el-col
          v-for="task in tasks"
          :key="task.id"
          :xs="24"
          :sm="12"
          :md="8"
        >
          <div
            class="task-card"
            :class="{
              running: task.status === 'RUNNING',
              'high-priority': (task.priority ?? 0) > 80
            }"
            @click="viewTask(task)"
          >
            <div class="card-header">
              <div class="card-type">
                <div class="type-name">{{ task.typeName || task.type }}</div>
                <div class="type-key">{{ task.type }}</div>
              </div>
              <FcTag
                :color="task.status === 'RUNNING' ? 'warning' : 'gray'"
                size="sm"
              >
                {{ task.status }}
              </FcTag>
            </div>

            <div class="card-id">
              <span>ID: {{ task.id.substring(0, 14) }}...</span>
              <i
                class="ri-file-copy-line copy-icon"
                @click.stop="copyId(task.id)"
              />
            </div>

            <div class="card-row">
              <span class="priority">
                <i class="ri-play-circle-line" />
                {{ t('lotask.tasks.active.card.priority') }}:
                <strong>{{ task.priority ?? 0 }}</strong>
              </span>
              <FcTag
                v-if="task.workerIp"
                color="success"
                size="sm"
              >
                {{ workerTail(task.workerIp) }}
              </FcTag>
            </div>

            <template v-if="task.status === 'RUNNING'">
              <el-progress
                :percentage="task.progress"
                :stroke-width="6"
                status="active"
                :color="['#007aff', '#34c759']"
              />
              <div v-if="task.currentStep" class="current-step">
                {{ t('lotask.tasks.detailExt.currentStep') }}: {{ task.currentStep }}
              </div>
            </template>

            <div class="card-time">
              <i class="ri-time-line" />
              <template v-if="task.status === 'RUNNING'">
                {{ t('lotask.tasks.active.card.runningFor') }}
                <strong class="time-running">{{ runningFor(task) }}</strong>
              </template>
              <template v-else>
                {{ t('lotask.tasks.active.card.waitingFor') }}
                <strong class="time-waiting">{{ waitingFor(task) }}</strong>
              </template>
            </div>

            <div v-if="task.expiredAt" class="card-expired">
              <i :class="['ri-timer-line', expiredClass(task.expiredAt)]" />
              <span :class="expiredClass(task.expiredAt)">{{ expiredText(task.expiredAt) }}</span>
            </div>

            <FcButton
              variant="text"
              size="sm"
              class="card-view"
              @click.stop="viewTask(task)"
            >
              {{ t('lotask.tasks.active.card.viewDetail') }}
            </FcButton>
          </div>
        </el-col>
      </el-row>

      <div v-if="tasks.length > 0" class="bottom-tip">
        <i class="ri-flashlight-line" />
        <span class="bottom-tip__text">{{ t('lotask.tasks.active.tip') }}</span>
      </div>
    </FcSection>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getTaskList } from '@/api/client'
import type { TaskListItem } from '@/api/types'
import { formatDuration, remainingLabel } from '@/utils/taskStatus'
import { usePolling, useClipboard } from '@/composables'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcEmpty from '@/components/sdk/display/FcEmpty.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskActiveTasksPage' })

const { t } = useI18n()
const router = useRouter()
const { copy } = useClipboard()

const loading = ref(false)
const tasks = ref<TaskListItem[]>([])
const stats = reactive({ running: 0, pending: 0 })

usePolling(loadTasks, { interval: 2000 })

async function loadTasks() {
  loading.value = true
  try {
    const [pendingRes, runningRes] = await Promise.all([
      getTaskList({ status: 'PENDING', page: 1, pageSize: 25 }),
      getTaskList({ status: 'RUNNING', page: 1, pageSize: 25 })
    ])
    const pending = pendingRes.list || []
    const running = runningRes.list || []
    const merged = [...running, ...pending]
      .sort((a, b) => {
        if (a.status === 'RUNNING' && b.status !== 'RUNNING') return -1
        if (a.status !== 'RUNNING' && b.status === 'RUNNING') return 1
        const pa = a.priority ?? 0
        const pb = b.priority ?? 0
        if (pb !== pa) return pb - pa
        const ta = new Date(a.createdAt).getTime() || 0
        const tb = new Date(b.createdAt).getTime() || 0
        return ta - tb
      })
      .slice(0, 50)
    tasks.value = merged
    stats.running = running.length
    stats.pending = pending.length
  } catch (err) {
    console.error('加载活跃任务失败:', err)
  } finally {
    loading.value = false
  }
}

function viewTask(row: TaskListItem) {
  router.push(`/tasks/${row.id}`)
}

async function copyId(id: string) {
  await copy(id)
}

function workerTail(workerIp: string): string {
  return workerIp.split('.').slice(-2).join('.')
}

function runningFor(task: TaskListItem): string {
  if (!task.startedAt) return '-'
  const start = new Date(task.startedAt).getTime()
  if (!start) return '-'
  return formatDuration(Date.now() - start)
}

function waitingFor(task: TaskListItem): string {
  if (!task.createdAt) return '-'
  const start = new Date(task.createdAt).getTime()
  if (!start) return '-'
  return formatDuration(Date.now() - start)
}

function expiredText(expiredAt: string): string {
  const info = remainingLabel(expiredAt)
  if (info.expired) return t('lotask.tasks.active.card.expired')
  return `${t('lotask.tasks.active.card.expireIn')} ${info.text}${t('lotask.tasks.active.card.after')}`
}

function expiredClass(expiredAt: string): string {
  const info = remainingLabel(expiredAt)
  if (info.expired) return 'expired'
  if (info.urgent) return 'expiring'
  return 'normal'
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped lang="scss">
.lotask-active {
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

  &__icon { font-size: 28px; flex-shrink: 0; }
  &__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  &__title { font-size: 12px; color: var(--el-text-color-secondary); }
  &__value {
    font-size: 24px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    line-height: 1.2;
  }
  &__suf { font-size: 14px; font-weight: 400; color: var(--el-text-color-secondary); }

  &--warning .kpi-tile__icon { color: var(--el-color-warning); }
  &--info    .kpi-tile__icon { color: var(--el-color-info); }
  &--primary .kpi-tile__icon { color: var(--el-color-primary); }
}

.tasks-grid {
  row-gap: 16px;
}

.task-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 4px solid var(--el-border-color);
  border-radius: 8px;
  padding: 14px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    transform: translateY(-1px);
  }

  &.running {
    border-left-color: var(--el-color-primary);
  }

  &.high-priority {
    background: var(--el-color-danger-light-9);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.card-type {
  display: flex;
  flex-direction: column;

  .type-name {
    font-size: 14px;
    font-weight: 600;
  }

  .type-key {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}

.card-id {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-family: 'SF Mono', Monaco, monospace;

  .copy-icon {
    cursor: pointer;
    opacity: 0.6;
    &:hover { opacity: 1; }
  }
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .priority {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;

    strong { color: var(--el-color-primary); }
  }
}

.current-step {
  font-size: 11px;
  color: var(--el-text-color-regular);
  margin: 2px 0;
}

.card-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);

  .time-running { color: var(--el-color-success); }
  .time-waiting { color: var(--el-color-warning); }
}

.card-expired {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;

  .expired  { color: var(--el-color-danger); }
  .expiring { color: var(--el-color-warning); }
  .normal   { color: var(--el-text-color-secondary); }
}

.card-view {
  align-self: flex-start;
}

.bottom-tip {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  gap: 8px;

  &__text {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>