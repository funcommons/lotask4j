<template>
  <div class="app-page">
    <TitledSection :title="t('taskList.title')" icon="ri-file-list-3-line">
      <template #actions>
        <el-button :icon="Refresh" @click="loadTasks">{{ t('taskList.action.refresh') }}</el-button>
        <el-button
          v-if="activeTab === 'current'"
          type="primary"
          :icon="Plus"
          @click="openSubmitDialog"
        >
          {{ t('taskListExt.submitTask.title') }}
        </el-button>
      </template>

      <WorkSection>
        <el-tabs v-model="activeTab" class="list-tabs" @tab-change="onTabChange">
          <el-tab-pane :label="t('taskListExt.tabs.current')" name="current" />
          <el-tab-pane :label="t('taskListExt.tabs.archived')" name="archived" />
        </el-tabs>

        <div class="filters">
          <el-input
            v-model="filterTaskId"
            :placeholder="t('taskListExt.search.taskId')"
            clearable
            style="width: 220px"
            @change="onFilterChange"
          >
            <template #prefix><el-icon><i class="ri-search-line" /></el-icon></template>
          </el-input>
          <el-select
            v-model="filterStatus"
            :placeholder="t('taskList.filter.allStatus')"
            clearable
            style="width: 150px"
            @change="onFilterChange"
          >
            <el-option :label="t('taskList.filter.allStatus')" value="" />
            <el-option :label="t('taskList.filter.pending')" value="PENDING" />
            <el-option :label="t('taskList.filter.running')" value="RUNNING" />
            <el-option :label="t('taskList.filter.success')" value="SUCCESS" />
            <el-option :label="t('taskList.filter.failed')" value="FAILED" />
            <el-option :label="t('taskList.filter.cancelled')" value="CANCELLED" />
          </el-select>
          <el-select
            v-model="filterTaskType"
            :placeholder="t('taskListExt.filter.taskType')"
            clearable
            style="width: 180px"
            @change="onFilterChange"
          >
            <el-option :label="t('taskListExt.filter.allTypes')" value="" />
            <el-option label="video_transcode" value="video_transcode" />
            <el-option label="data_export" value="data_export" />
            <el-option label="image_process" value="image_process" />
            <el-option label="pdf_generate" value="pdf_generate" />
          </el-select>
          <el-date-picker
            v-model="filterDateRange"
            type="daterange"
            :start-placeholder="t('taskListExt.filter.dateStart')"
            :end-placeholder="t('taskListExt.filter.dateEnd')"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 280px"
            @change="onFilterChange"
          />
        </div>

        <el-table
          v-loading="loading"
          :data="tasks"
          :empty-text="t('taskList.empty')"
          stripe
          border
          style="margin-top: 12px"
        >
          <el-table-column :label="t('taskList.id')" min-width="170">
            <template #default="{ row }">
              <el-text size="small" class="task-id" @click="copyId(row.id)">
                {{ row.id.substring(0, 12) }}...
                <el-icon class="copy-icon"><i class="ri-file-copy-line" /></el-icon>
              </el-text>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskList.type')" min-width="160">
            <template #default="{ row }">
              <div class="type-cell">
                <div class="type-name">{{ row.typeName || row.type }}</div>
                <div class="type-key">{{ row.type }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskList.status')" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" effect="light">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskList.progress')" min-width="170">
            <template #default="{ row }">
              <el-progress
                :percentage="row.progress"
                :stroke-width="10"
                :status="progressStatus(row.status)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('taskListExt.column.priority')" width="90" align="center">
            <template #default="{ row }">
              <el-tag
                :type="(row.priority ?? 0) > 80 ? 'danger' : 'info'"
                effect="plain"
                size="small"
              >
                {{ row.priority ?? 0 }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskList.createdAt')" min-width="170">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('taskListExt.column.runningTime')" width="130" align="center">
            <template #default="{ row }">
              <span v-if="runningDuration(row)" :class="['duration', { running: row.status === 'RUNNING' }]">
                {{ runningDuration(row) }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskListExt.column.expiredAt')" min-width="160">
            <template #default="{ row }">
              <template v-if="row.expiredAt">
                <div class="expired-cell">
                  <div class="expired-time">{{ formatTime(row.expiredAt) }}</div>
                  <el-tag
                    v-if="expiredInfo(row.expiredAt).isExpired"
                    type="danger"
                    size="small"
                    effect="plain"
                  >
                    {{ t('taskListExt.column.expired') }}
                  </el-tag>
                  <el-tag
                    v-else-if="expiredInfo(row.expiredAt).isExpiring"
                    type="warning"
                    size="small"
                    effect="plain"
                  >
                    {{ t('taskListExt.column.remaining') }} {{ expiredInfo(row.expiredAt).text }}
                  </el-tag>
                  <el-text v-else type="info" size="small">
                    {{ t('taskListExt.column.remaining') }} {{ expiredInfo(row.expiredAt).text }}
                  </el-text>
                </div>
              </template>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.action')" width="170" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="viewTask(row)">
                {{ t('taskList.action.view') }}
              </el-button>
              <el-button
                v-if="canCancel(row.status)"
                type="danger"
                size="small"
                link
                @click="cancelTask(row)"
              >
                {{ t('taskList.action.cancel') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top: 16px; justify-content: flex-end;"
        />
      </WorkSection>
    </TitledSection>

    <AppDialog
      :visible="submitDialogVisible"
      @update:visible="submitDialogVisible = $event"
      :title="t('taskListExt.submitTask.title')"
      width="700px"
    >
      <el-alert
        v-if="submittedTaskId"
        :title="t('taskListExt.submitTask.submitSuccess')"
        type="success"
        show-icon
        :closable="true"
        @close="submittedTaskId = null"
        style="margin-bottom: 16px"
      >
        <div>
          {{ t('taskListExt.submitTask.submittedId') }}:
          <el-text class="submitted-id">{{ submittedTaskId }}</el-text>
        </div>
        <el-button type="primary" size="small" link @click="goToSubmittedDetail">
          {{ t('taskListExt.submitTask.viewDetail') }}
        </el-button>
      </el-alert>

      <el-alert
        :title="t('taskListExt.submitTask.tip')"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-form
        ref="submitFormRef"
        :model="submitForm"
        :rules="submitRules"
        label-position="top"
      >
        <el-form-item :label="t('taskListExt.submitTask.type')" prop="type">
          <el-input v-model="submitForm.type" :placeholder="t('taskListExt.submitTask.typePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('taskListExt.submitTask.payload')" prop="payload">
          <el-input
            v-model="submitForm.payload"
            type="textarea"
            :rows="6"
            :placeholder="t('taskListExt.submitTask.payloadPlaceholder')"
            class="payload-input"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('taskListExt.submitTask.priority')" prop="priority">
              <el-input-number v-model="submitForm.priority" :min="1" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('taskListExt.submitTask.callbackUrl')">
              <el-input
                v-model="submitForm.callbackUrl"
                :placeholder="t('taskListExt.submitTask.callbackUrlPlaceholder')"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="submitDialogVisible = false">{{ t('taskListExt.submitTask.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('taskListExt.submitTask.submit') }}
        </el-button>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import { getTaskList, cancelTask as cancelTaskApi, type Task } from '@/api/client'
import { adminSubmitTask } from '@/api/admin'
import { formatTime, parseTime, formatDuration } from '@/utils/time'
import { formatExpiredTime } from '@/utils/taskTime'
import { usePolling } from '@/composables/usePolling'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import AppDialog from '@/components/sdk/common/AppDialog.vue'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const tasks = ref<Task[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const activeTab = ref<'current' | 'archived'>('current')
const filterTaskId = ref('')
const filterStatus = ref<string>('')
const filterTaskType = ref<string>('')
const filterDateRange = ref<[string, string] | null>(null)

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

const submitRules: FormRules = {
  type: [{ required: true, message: () => t('taskListExt.submitTask.type'), trigger: 'blur' }],
  payload: [
    { required: true, message: () => t('taskListExt.submitTask.payload'), trigger: 'blur' },
    {
      validator: (_: any, value: string, callback: any) => {
        try { JSON.parse(value); callback() } catch { callback(new Error(t('taskListExt.submitTask.payloadInvalid'))) }
      },
      trigger: 'blur'
    }
  ],
  priority: [{ required: true, message: () => t('taskListExt.submitTask.priority'), trigger: 'blur' }]
}

usePolling(loadTasks, {
  interval: 5000,
  predicate: () =>
    activeTab.value === 'current' && tasks.value.some(t => t.status === 'RUNNING'),
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
      isArchived: activeTab.value === 'archived',
      createdAtStart: filterDateRange.value?.[0],
      createdAtEnd: filterDateRange.value?.[1]
    })
    tasks.value = res.data.list || []
    total.value = Number(res.data.total) || 0
  } catch (err: any) {
    ElMessage.error(err.message || t('common.loadFailed'))
  } finally {
    loading.value = false
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

function viewTask(row: Task) {
  router.push(`/tasks/${row.id}`)
}

function goToSubmittedDetail() {
  if (!submittedTaskId.value) return
  submitDialogVisible.value = false
  router.push(`/tasks/${submittedTaskId.value}`)
}

async function cancelTask(row: Task) {
  try {
    await ElMessageBox.confirm(
      t('taskList.action.confirming'),
      t('taskList.confirmCancel'),
      { type: 'warning' }
    )
  } catch { return }
  try {
    await cancelTaskApi(row.id)
    ElMessage.success(t('taskList.cancelSuccess'))
    loadTasks()
  } catch (err: any) {
    ElMessage.error(err.message || t('common.cancelFailed'))
  }
}

function canCancel(status: string) {
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
  await submitFormRef.value.validate()
  let payload: any
  try {
    payload = JSON.parse(submitForm.payload)
  } catch {
    ElMessage.error(t('taskListExt.submitTask.payloadInvalid'))
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
    submittedTaskId.value = res.data.id
    ElMessage.success(t('taskListExt.submitTask.submitSuccess'))
    loadTasks()
  } catch (err: any) {
    ElMessage.error(err.message || t('taskListExt.submitTask.submitFailed'))
  } finally {
    submitting.value = false
  }
}

async function copyId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
    ElMessage.success(t('taskDetailExt.copySuccess'))
  } catch {
    ElMessage.warning(id)
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

function progressStatus(status: string): '' | 'success' | 'exception' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'exception'
  return ''
}

function runningDuration(row: Task): string | null {
  if (!row.startedAt) return null
  const start = parseTime(row.startedAt)
  if (!start) return null
  if (row.finishedAt) {
    const end = parseTime(row.finishedAt)
    if (end) return formatDuration(end.getTime() - start.getTime())
  }
  if (row.status === 'RUNNING') {
    return formatDuration(Date.now() - start.getTime())
  }
  return null
}

function expiredInfo(expiredAt: string) {
  return formatExpiredTime(expiredAt)
}

onMounted(() => {
  loadTasks()
})

watch([page, pageSize], () => {
  loadTasks()
})
</script>

<style scoped lang="scss">
.list-tabs {
  margin-bottom: 8px;
}

.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.task-id {
  cursor: pointer;
  font-family: 'SF Mono', Monaco, monospace;
  color: var(--el-color-primary);

  .copy-icon {
    margin-left: 4px;
    font-size: 12px;
    opacity: 0.6;
  }
}

.type-cell {
  display: flex;
  flex-direction: column;

  .type-name {
    font-size: 13px;
    font-weight: 500;
  }

  .type-key {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}

.duration {
  font-size: 12px;
  color: var(--el-text-color-secondary);

  &.running {
    color: var(--el-color-success);
  }
}

.expired-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .expired-time {
    font-size: 12px;
    color: var(--el-text-color-regular);
  }
}

.payload-input {
  :deep(textarea) {
    font-family: 'SF Mono', Monaco, monospace;
    font-size: 12px;
  }
}

.submitted-id {
  font-family: 'SF Mono', Monaco, monospace;
  font-weight: 600;
  color: var(--el-color-primary);
  margin: 0 4px;
}
</style>
