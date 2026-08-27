<template>
  <div class="lotask-page lotask-list">
    <FcSectionHeader
      :title="t('lotask.tasks.list.title')"
      :back="true"
      @back="router.back()"
    >
      <template #actions>
        <FcButton variant="secondary" :loading="loading" @click="loadTasks">
          {{ t('lotask.tasks.list.action.refresh') }}
        </FcButton>
        <FcButton
          v-if="activeTab === 'current'"
          variant="primary"
          @click="openSubmitDialog"
        >
          {{ t('lotask.tasks.listExt.submitTask.title') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <FcSection padding="md" shadow="sm">
      <FcTabsPanel
        v-model="activeTab"
        :tabs="[
          { value: 'current',  label: t('lotask.tasks.listExt.tabs.current') },
          { value: 'archived', label: t('lotask.tasks.listExt.tabs.archived') },
        ]"
        @tab-click="onTabChange"
      />

      <div class="filters">
        <el-input
          v-model="filterTaskId"
          :placeholder="t('lotask.tasks.listExt.search.taskId')"
          clearable
          class="fc-input filter-taskid"
          @change="onFilterChange"
        >
          <template #prefix><i class="ri-search-line" /></template>
        </el-input>
        <FcSelect
          v-model="filterStatus"
          :placeholder="t('lotask.tasks.list.filter.allStatus')"
          clearable
          class="filter-status"
          @change="onFilterChange"
        >
          <el-option :label="t('lotask.tasks.list.filter.allStatus')" value="" />
          <el-option :label="t('lotask.tasks.list.filter.pending')" value="PENDING" />
          <el-option :label="t('lotask.tasks.list.filter.running')" value="RUNNING" />
          <el-option :label="t('lotask.tasks.list.filter.success')" value="SUCCESS" />
          <el-option :label="t('lotask.tasks.list.filter.failed')" value="FAILED" />
          <el-option :label="t('lotask.tasks.list.filter.cancelled')" value="CANCELLED" />
        </FcSelect>
        <FcSelect
          v-model="filterTaskType"
          :placeholder="t('lotask.tasks.listExt.filter.taskType')"
          clearable
          :loading="loadingTypes"
          class="filter-type"
          @change="onFilterChange"
        >
          <el-option :label="t('lotask.tasks.listExt.filter.allTypes')" value="" />
          <el-option
            v-for="cfg in taskTypeOptions"
            :key="cfg.typeKey"
            :label="`${cfg.name} (${cfg.typeKey})`"
            :value="cfg.typeKey"
          />
        </FcSelect>
        <el-date-picker
          v-model="filterDateRange"
          type="daterange"
          :start-placeholder="t('lotask.tasks.listExt.filter.dateStart')"
          :end-placeholder="t('lotask.tasks.listExt.filter.dateEnd')"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          class="filter-date fc-input"
          @change="onFilterChange"
        />
      </div>

      <el-table
        v-loading="loading"
        :data="tasks"
        :empty-text="t('lotask.tasks.list.empty')"
        stripe
        border
        class="fc-table"
        style="margin-top: 12px"
      >
        <el-table-column :label="t('lotask.tasks.list.id')" min-width="170">
          <template #default="{ row }">
            <span class="task-id" @click="copyId(row.id)">
              {{ row.id.substring(0, 12) }}...
              <i class="ri-file-copy-line copy-icon" />
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.type')" min-width="180">
          <template #default="{ row }">
            <div class="type-cell">
              <div class="type-name">{{ row.typeName || row.type }}</div>
              <div class="type-key">{{ row.type }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.status')" width="120" align="center">
          <template #default="{ row }">
            <FcTag :color="statusColor(row.status)" size="sm">
              {{ statusLabel(row.status) }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.progress')" min-width="170">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :stroke-width="10"
              :status="progressStatus(row.status)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.listExt.column.priority')" width="100" align="center">
          <template #default="{ row }">
            <FcTag
              :color="(row.priority ?? 0) > 80 ? 'danger' : 'gray'"
              size="sm"
            >
              {{ row.priority ?? 0 }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.createdAt')" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.listExt.column.runningTime')" width="140" align="center">
          <template #default="{ row }">
            <span
              v-if="runningDuration(row)"
              :class="['duration', { running: row.status === 'RUNNING' }]"
            >
              {{ runningDuration(row) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.listExt.column.expiredAt')" min-width="180">
          <template #default="{ row }">
            <template v-if="row.expiredAt">
              <div class="expired-cell">
                <div class="expired-time">{{ formatDateTime(row.expiredAt) }}</div>
                <template v-if="remainingLabel(row.expiredAt).expired">
                  <FcTag color="danger" size="sm">
                    {{ t('lotask.tasks.listExt.column.expired') }}
                  </FcTag>
                </template>
                <template v-else-if="remainingLabel(row.expiredAt).urgent">
                  <FcTag color="warning" size="sm">
                    {{ t('lotask.tasks.listExt.column.remaining') }} {{ remainingLabel(row.expiredAt).text }}
                  </FcTag>
                </template>
                <span v-else class="expired-text">
                  {{ t('lotask.tasks.listExt.column.remaining') }} {{ remainingLabel(row.expiredAt).text }}
                </span>
              </div>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.listExt.column.action')" width="180" fixed="right">
          <template #default="{ row }">
            <FcButton variant="text" size="sm" @click="viewTask(row)">
              {{ t('lotask.tasks.list.action.view') }}
            </FcButton>
            <FcButton
              v-if="canCancel(row.status)"
              variant="text"
              size="sm"
              @click="cancelTask(row)"
            >
              {{ t('lotask.tasks.list.action.cancel') }}
            </FcButton>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <FcPagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </FcSection>

    <!-- 提交任务对话框 -->
    <FcDialog
      v-model:open="submitDialogVisible"
      :title="t('lotask.tasks.listExt.submitTask.title')"
      width="700px"
    >
      <el-alert
        v-if="submittedTaskId"
        :title="t('lotask.tasks.listExt.submitTask.submitSuccess')"
        type="success"
        show-icon
        :closable="true"
        @close="submittedTaskId = null"
        class="submit-alert"
      >
        <template #default>
          <div class="submit-success">
            {{ t('lotask.tasks.listExt.submitTask.submittedId') }}:
            <span class="submitted-id">{{ submittedTaskId }}</span>
          </div>
          <FcButton variant="text" size="sm" @click="goToSubmittedDetail">
            {{ t('lotask.tasks.listExt.submitTask.viewDetail') }}
          </FcButton>
        </template>
      </el-alert>

      <el-alert
        :title="t('lotask.tasks.listExt.submitTask.tip')"
        type="info"
        show-icon
        :closable="false"
        class="submit-alert"
      />

      <el-form
        ref="submitFormRef"
        :model="submitForm"
        :rules="submitRules"
        label-position="top"
      >
        <el-form-item :label="t('lotask.tasks.listExt.submitTask.type')" prop="type" class="fc-form-item">
          <el-input
            v-model="submitForm.type"
            :placeholder="t('lotask.tasks.listExt.submitTask.typePlaceholder')"
            class="fc-input"
          />
        </el-form-item>
        <el-form-item :label="t('lotask.tasks.listExt.submitTask.payload')" prop="payload" class="fc-form-item">
          <el-input
            v-model="submitForm.payload"
            type="textarea"
            :rows="6"
            :placeholder="t('lotask.tasks.listExt.submitTask.payloadPlaceholder')"
            class="fc-input payload-input"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('lotask.tasks.listExt.submitTask.priority')" prop="priority" class="fc-form-item">
              <el-input-number
                v-model="submitForm.priority"
                :min="1"
                :max="1000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('lotask.tasks.listExt.submitTask.callbackUrl')" class="fc-form-item">
              <el-input
                v-model="submitForm.callbackUrl"
                :placeholder="t('lotask.tasks.listExt.submitTask.callbackUrlPlaceholder')"
                class="fc-input"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <FcButton variant="secondary" @click="submitDialogVisible = false">
          {{ t('lotask.tasks.listExt.submitTask.cancel') }}
        </FcButton>
        <FcButton variant="primary" :loading="submitting" @click="handleSubmit">
          {{ t('lotask.tasks.listExt.submitTask.submit') }}
        </FcButton>
      </template>
    </FcDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import {
  getTaskList,
  cancelTask as cancelTaskApi
} from '@/api/client'
import { adminSubmitTask } from '@/api/admin'
import type { TaskListItem } from '@/api/types'
import {
  formatDateTime,
  formatDuration,
  remainingLabel,
  TASK_STATUS_TAG_TYPE
} from '@/utils/taskStatus'
import type { TaskStatus } from '@/api/types'
import { usePolling, useClipboard } from '@/composables'
import { toast } from '@/components/sdk'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcDialog from '@/components/sdk/overlay/FcDialog.vue'
import FcPagination from '@/components/sdk/navigation/FcPagination.vue'
import FcTabsPanel from '@/components/sdk/navigation/FcTabsPanel.vue'
import FcSelect from '@/components/sdk/form/FcSelect.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskTaskListPage' })

const { t } = useI18n()
const router = useRouter()
const { copy } = useClipboard()

const loading = ref(false)
const tasks = ref<TaskListItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const activeTab = ref<'current' | 'archived'>('current')
const filterTaskId = ref('')
const filterStatus = ref<string>('')
const filterTaskType = ref<string>('')
const filterDateRange = ref<[string, string] | null>(null)

const taskTypeOptions = ref<{ typeKey: string; name: string }[]>([])
const loadingTypes = ref(false)

const submitDialogVisible = ref(false)
const submitting = ref(false)
const submittedTaskId = ref<string | null>(null)
const submitFormRef = ref<FormInstance>()
const submitForm = reactive({
  type: '',
  payload: '{\n  "key": "value"\n}',
  priority: 100,
  callbackUrl: ''
})

const submitRules = computed<FormRules>(() => ({
  type: [{ required: true, message: t('lotask.tasks.listExt.submitTask.type'), trigger: 'blur' }],
  payload: [
    { required: true, message: t('lotask.tasks.listExt.submitTask.payload'), trigger: 'blur' },
    {
      validator: (_: unknown, value: string, callback: (err?: Error) => void) => {
        try {
          JSON.parse(value)
          callback()
        } catch {
          callback(new Error(t('lotask.tasks.listExt.submitTask.payloadInvalid')))
        }
      },
      trigger: 'blur'
    }
  ],
  priority: [{ required: true, message: t('lotask.tasks.listExt.submitTask.priority'), trigger: 'blur' }]
}))

usePolling(loadTasks, {
  interval: 5000,
  predicate: () =>
    activeTab.value === 'current' && tasks.value.some((task: TaskListItem) => task.status === 'RUNNING')
})

async function loadTasks() {
  loading.value = true
  try {
    const res = await getTaskList({
      page: page.value,
      pageSize: pageSize.value,
      status: filterStatus.value || undefined,
      taskType: filterTaskType.value || undefined,
      id: filterTaskId.value || undefined,
      isArchived: activeTab.value === 'archived' ? 1 : 0,
      createdAtStart: filterDateRange.value?.[0],
      createdAtEnd: filterDateRange.value?.[1]
    })
    tasks.value = res.list || []
    total.value = Number(res.total) || 0
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    toast.error(msg || t('lotask.tasks.list.empty'))
  } finally {
    loading.value = false
  }
}

async function loadTaskTypes() {
  loadingTypes.value = true
  try {
    const res = await import('@/api/admin').then(m => m.getAllTaskTypeConfigs())
    taskTypeOptions.value = (res || []).map((cfg: { typeKey: string; name: string }) => ({ typeKey: cfg.typeKey, name: cfg.name }))
  } catch (err) {
    console.error('加载任务类型配置失败:', err)
  } finally {
    loadingTypes.value = false
  }
}

function onTabChange() {
  page.value = 1
  loadTasks()
}

function onFilterChange() {
  page.value = 1
  loadTasks()
}

function viewTask(row: TaskListItem) {
  router.push(`/tasks/${row.id}`)
}

function goToSubmittedDetail() {
  if (!submittedTaskId.value) return
  submitDialogVisible.value = false
  router.push(`/tasks/${submittedTaskId.value}`)
}

async function cancelTask(row: TaskListItem) {
  try {
    await toast.confirm({
      title: t('lotask.tasks.list.confirmCancel'),
      message: t('lotask.tasks.list.action.confirming'),
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await cancelTaskApi(row.id)
    toast.success(t('lotask.tasks.list.cancelSuccess'))
    loadTasks()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    toast.error(msg || t('lotask.tasks.list.action.cancel'))
  }
}

function canCancel(status: TaskStatus) {
  return activeTab.value === 'current' && (status === 'PENDING' || status === 'RUNNING')
}

function openSubmitDialog() {
  submittedTaskId.value = null
  submitForm.type = ''
  submitForm.payload = '{\n  "key": "value"\n}'
  submitForm.priority = 100
  submitForm.callbackUrl = ''
  submitDialogVisible.value = true
}

async function handleSubmit() {
  if (!submitFormRef.value) return
  try {
    await submitFormRef.value.validate()
  } catch {
    return
  }
  let payload: Record<string, unknown>
  try {
    payload = JSON.parse(submitForm.payload) as Record<string, unknown>
  } catch {
    toast.error(t('lotask.tasks.listExt.submitTask.payloadInvalid'))
    return
  }
  submitting.value = true
  try {
    const res = await adminSubmitTask({
      type: submitForm.type,
      payload,
      priority: submitForm.priority,
      callbackUrl: submitForm.callbackUrl || undefined
    })
    submittedTaskId.value = res.id
    toast.success(t('lotask.tasks.listExt.submitTask.submitSuccess'))
    loadTasks()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    toast.error(msg || t('lotask.tasks.listExt.submitTask.submitFailed'))
  } finally {
    submitting.value = false
  }
}

async function copyId(id: string) {
  await copy(id)
}

function statusColor(status: TaskStatus) {
  // FcTag 仅支持 6 色 brand palette, 把 el-tag 的 primary/info/light/dark 收敛
  const c = TASK_STATUS_TAG_TYPE[status]
  if (c === 'primary') return 'brand'
  if (c === 'info')    return 'gray'
  return c
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

function progressStatus(status: TaskStatus): '' | 'success' | 'exception' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'exception'
  return ''
}

function runningDuration(row: TaskListItem): string | null {
  if (!row.startedAt) return null
  const start = new Date(row.startedAt).getTime()
  if (!start) return null
  if (row.finishedAt) {
    const end = new Date(row.finishedAt).getTime()
    if (end) return formatDuration(end - start)
  }
  if (row.status === 'RUNNING') {
    return formatDuration(Date.now() - start)
  }
  return null
}

// watch page / size changes to reload
watch([page, pageSize], () => {
  loadTasks()
})

onMounted(() => {
  loadTasks()
  loadTaskTypes()
})
</script>

<style scoped lang="scss">
.lotask-list {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;

  .filter-taskid { width: 220px; }
  .filter-status { width: 150px; }
  .filter-type   { width: 200px; }
  .filter-date   { width: 280px; }
}

.task-id {
  cursor: pointer;
  font-family: 'SF Mono', Monaco, monospace;
  color: var(--el-color-primary);
  font-size: 13px;

  .copy-icon {
    margin-left: 4px;
    font-size: 12px;
    opacity: 0.6;
  }
}

.type-cell {
  display: flex;
  flex-direction: column;

  .type-name { font-size: 13px; font-weight: 500; }
  .type-key  { font-size: 11px; color: var(--el-text-color-secondary); }
}

.duration {
  font-size: 12px;
  color: var(--el-text-color-secondary);

  &.running { color: var(--el-color-success); }
}

.expired-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .expired-time { font-size: 12px; color: var(--el-text-color-regular); }
  .expired-text { font-size: 11px; color: var(--el-text-color-secondary); }
}

.pagination-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.payload-input {
  :deep(textarea) {
    font-family: 'SF Mono', Monaco, monospace;
    font-size: 12px;
  }
}

.submit-alert {
  margin-bottom: 16px;
}

.submit-success {
  font-size: 13px;
  color: var(--el-text-color-regular);

  .submitted-id {
    font-family: 'SF Mono', Monaco, monospace;
    font-weight: 600;
    color: var(--el-color-primary);
    margin: 0 4px;
  }
}
</style>