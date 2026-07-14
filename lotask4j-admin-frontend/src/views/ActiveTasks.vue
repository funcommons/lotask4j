<template>
  <div class="app-page">
    <TitledSection :title="t('activeTasks.title')" icon="ri-flashlight-line">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadTasks">
          {{ t('taskList.action.refresh') }}
        </el-button>
      </template>

      <!-- 顶部 3 KPI -->
      <KpiLayout :columns="3" style="margin-bottom: 16px">
        <KpiSection
          :title="t('activeTasksExt.stats.running')"
          icon="ri-loader-4-line"
          :value="stats.running"
        />
        <KpiSection
          :title="t('activeTasksExt.stats.pending')"
          icon="ri-time-line"
          :value="stats.pending"
        />
        <KpiSection
          :title="t('activeTasksExt.stats.total')"
          icon="ri-flashlight-line"
          :value="tasks.length"
          :description="`/ 50`"
        />
      </KpiLayout>

      <WorkSection>
        <el-empty v-if="tasks.length === 0 && !loading" :description="t('activeTasksExt.empty')" />

        <el-row v-else :gutter="16" class="tasks-grid">
          <el-col
            v-for="task in tasks"
            :key="task.id"
            :xs="24" :sm="12" :md="8"
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
                <el-tag
                  :type="task.status === 'RUNNING' ? 'warning' : 'info'"
                  effect="light"
                  size="small"
                >
                  {{ task.status }}
                </el-tag>
              </div>

              <div class="card-id">
                <span>ID: {{ task.id.substring(0, 14) }}...</span>
                <el-icon class="copy-icon" @click.stop="copyId(task.id)"><i class="ri-file-copy-line" /></el-icon>
              </div>

              <div class="card-row">
                <span class="priority">
                  <el-icon><i class="ri-play-circle-line" /></el-icon>
                  {{ t('activeTasksExt.card.priority') }}: <strong>{{ task.priority ?? 0 }}</strong>
                </span>
                <el-tag
                  v-if="task.workerIp"
                  type="success"
                  size="small"
                  effect="plain"
                >
                  {{ task.workerIp.split('.').slice(-2).join('.') }}
                </el-tag>
              </div>

              <template v-if="task.status === 'RUNNING'">
                <el-progress
                  :percentage="task.progress"
                  :stroke-width="6"
                  status="active"
                  :color="['#007aff', '#34c759']"
                />
                <div v-if="task.currentStep" class="current-step">
                  {{ t('taskDetailExt.currentStep') }}: {{ task.currentStep }}
                </div>
              </template>

              <div class="card-time">
                <el-icon><i class="ri-time-line" /></el-icon>
                <template v-if="task.status === 'RUNNING'">
                  {{ t('activeTasksExt.card.runningFor') }}
                  <strong class="time-running">{{ runningFor(task) }}</strong>
                </template>
                <template v-else>
                  {{ t('activeTasksExt.card.waitingFor') }}
                  <strong class="time-waiting">{{ waitingFor(task) }}</strong>
                </template>
              </div>

              <div v-if="task.expiredAt" class="card-expired">
                <el-icon :class="expiredClass(task.expiredAt)"><i class="ri-timer-line" /></el-icon>
                <span :class="expiredClass(task.expiredAt)">{{ expiredText(task.expiredAt) }}</span>
              </div>

              <el-button
                type="primary"
                size="small"
                link
                class="card-view"
                @click.stop="viewTask(task)"
              >
                {{ t('activeTasksExt.card.viewDetail') }}
              </el-button>
            </div>
          </el-col>
        </el-row>

        <div v-if="tasks.length > 0" class="bottom-tip">
          <el-icon><i class="ri-flashlight-line" /></el-icon>
          <el-text type="info" size="small">{{ t('activeTasksExt.tip') }}</el-text>
        </div>
      </WorkSection>
    </TitledSection>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getTaskList, type Task } from '@/api/client'
import { parseTime, formatDuration } from '@/utils/time'
import { formatExpiredTime } from '@/utils/taskTime'
import { usePolling } from '@/composables/usePolling'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import KpiLayout from '@/components/sdk/common/KpiLayout.vue'
import KpiSection from '@/components/sdk/common/KpiSection.vue'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const tasks = ref<Task[]>([])
const stats = reactive({ running: 0, pending: 0 })
usePolling(loadTasks, { interval: 2000 })

async function loadTasks() {
  loading.value = true
  try {
    const [pendingRes, runningRes] = await Promise.all([
      getTaskList({ status: 'PENDING', page: 1, pageSize: 25 }),
      getTaskList({ status: 'RUNNING', page: 1, pageSize: 25 })
    ])
    const pending = pendingRes.data.list || []
    const running = runningRes.data.list || []
    const merged = [...running, ...pending]
      .sort((a, b) => {
        if (a.status === 'RUNNING' && b.status !== 'RUNNING') return -1
        if (a.status !== 'RUNNING' && b.status === 'RUNNING') return 1
        const pa = a.priority ?? 0
        const pb = b.priority ?? 0
        if (pb !== pa) return pb - pa
        const ta = parseTime(a.createdAt)?.getTime() ?? 0
        const tb = parseTime(b.createdAt)?.getTime() ?? 0
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

function viewTask(row: Task) {
  router.push(`/tasks/${row.id}`)
}

async function copyId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
    ElMessage.success(t('taskDetailExt.copySuccess'))
  } catch {
    ElMessage.warning(id)
  }
}

function runningFor(task: Task): string {
  if (!task.startedAt) return '-'
  const start = parseTime(task.startedAt)
  if (!start) return '-'
  return formatDuration(Date.now() - start.getTime())
}

function waitingFor(task: Task): string {
  if (!task.createdAt) return '-'
  const start = parseTime(task.createdAt)
  if (!start) return '-'
  return formatDuration(Date.now() - start.getTime())
}

function expiredInfo(expiredAt: string) {
  return formatExpiredTime(expiredAt)
}

function expiredText(expiredAt: string): string {
  const info = expiredInfo(expiredAt)
  if (info.isExpired) return t('activeTasksExt.card.expired')
  const unit = info.text.includes('天') ? t('activeTasksExt.card.days')
    : info.text.includes('小时') ? t('activeTasksExt.card.hours')
    : t('activeTasksExt.card.minutes')
  return `${t('activeTasksExt.card.expireIn')} ${info.text}${t('activeTasksExt.card.after')}`
}

function expiredClass(expiredAt: string): string {
  const info = expiredInfo(expiredAt)
  if (info.isExpired) return 'expired'
  if (info.isExpiring) return 'expiring'
  return 'normal'
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped lang="scss">
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
  transition: all 0.2s;
  height: 100%;

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
  margin-bottom: 8px;

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
  margin-bottom: 8px;

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
  margin-bottom: 8px;

  .priority {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;

    strong {
      color: var(--el-color-primary);
    }
  }
}

.current-step {
  font-size: 11px;
  color: var(--el-text-color-regular);
  margin: 6px 0;
}

.card-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;

  .time-running { color: var(--el-color-success); }
  .time-waiting { color: var(--el-color-warning); }
}

.card-expired {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  margin-top: 4px;

  .expired { color: var(--el-color-danger); }
  .expiring { color: var(--el-color-warning); }
  .normal { color: var(--el-text-color-secondary); }
}

.card-view {
  margin-top: 8px;
  padding: 0;
  height: auto;
}

.bottom-tip {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
