<template>
  <div class="lotask-page lotask-ptasks">
    <FcSectionHeader
      :title="t('lotask.tasks.list.title')"
      :back="true"
      @back="router.back()"
    >
      <template #actions>
        <FcButton variant="secondary" :loading="loading" @click="loadTasks">
          {{ t('lotask.tasks.list.action.refresh') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <FcSection padding="md" shadow="sm">
      <div class="filters">
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
        <el-input
          v-model="filterType"
          :placeholder="t('lotask.tasks.platform.filter.type')"
          clearable
          class="fc-input filter-type"
          @change="onFilterChange"
        >
          <template #prefix><i class="ri-price-tag-3-line" /></template>
        </el-input>
        <FcSelect
          v-model="filterTenantId"
          :placeholder="t('lotask.tasks.platform.filter.tenant')"
          clearable
          filterable
          :loading="loadingTenants"
          class="filter-tenant"
          @change="onFilterChange"
        >
          <el-option :label="t('lotask.tasks.platform.filter.allTenants')" :value="0" />
          <el-option
            v-for="tn in tenantOptions"
            :key="tn.id"
            :label="`${tn.name} (#${tn.id})`"
            :value="tn.id"
          />
        </FcSelect>
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
        <el-table-column :label="t('lotask.tasks.list.id')" min-width="150">
          <template #default="{ row }">
            <span class="task-id" @click="copyId(row.id)">
              {{ String(row.id).substring(0, 12) }}...
              <i class="ri-file-copy-line copy-icon" />
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.platform.column.tenant')" min-width="140">
          <template #default="{ row }">
            <span v-if="tenantName(row.tenantId)">{{ tenantName(row.tenantId) }}</span>
            <span v-else-if="row.tenantId" class="tenant-raw">#{{ row.tenantId }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.type')" min-width="170">
          <template #default="{ row }">
            <div class="type-cell">
              <div class="type-name">{{ row.typeName || row.type }}</div>
              <div class="type-key">{{ row.type }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.status')" width="110" align="center">
          <template #default="{ row }">
            <FcTag :color="statusColor(row.status)" size="sm">
              {{ statusLabel(row.status) }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.progress')" min-width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :stroke-width="10"
              :status="progressStatus(row.status)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.list.createdAt')" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.tasks.listExt.column.runningTime')" width="120" align="center">
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
        <el-table-column :label="t('lotask.tasks.listExt.column.action')" width="100" fixed="right">
          <template #default="{ row }">
            <FcButton variant="text" size="sm" @click="showDetail(row)">
              {{ t('lotask.tasks.list.action.view') }}
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

    <!-- 任务详情对话框 (平台视角只读; 列表行数据直出, 不再请求单条接口) -->
    <FcDialog
      v-model:open="detailVisible"
      :title="t('lotask.tasks.platform.detail.title')"
      width="720px"
    >
      <template v-if="detailRow">
        <el-alert
          v-if="detailRow.errorMsg"
          :title="detailRow.errorMsg"
          type="error"
          show-icon
          :closable="false"
          class="detail-alert"
        />
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="t('lotask.tasks.list.id')">
            <span class="mono">{{ detailRow.id }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.platform.column.tenant')">
            {{ tenantName(detailRow.tenantId) || (detailRow.tenantId ? `#${detailRow.tenantId}` : '-') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.list.type')">
            {{ detailRow.typeName || detailRow.type }} ({{ detailRow.type }})
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.list.status')">
            <FcTag :color="statusColor(detailRow.status)" size="sm">
              {{ statusLabel(detailRow.status) }}
            </FcTag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.list.progress')">
            {{ detailRow.progress }}%
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.listExt.column.runningTime')">
            {{ runningDuration(detailRow) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.list.createdAt')">
            {{ formatDateTime(detailRow.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.tasks.listExt.column.expiredAt')">
            {{ detailRow.expiredAt ? formatDateTime(detailRow.expiredAt) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailRow.workerIp" :label="'Worker'">
            {{ detailRow.workerId || '' }} {{ detailRow.workerIp }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailRow.lastErrorCode" :label="'Error Code'">
            <span class="mono">{{ detailRow.lastErrorCode }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <template v-for="block in detailBlocks" :key="block.key">
          <div class="detail-block-title">{{ block.title }}</div>
          <pre class="detail-pre">{{ block.content }}</pre>
        </template>
      </template>
    </FcDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getAdminTaskList } from '@/api/admin'
import { listTenants, type TenantItem } from '@/api/tenants'
import type { TaskDetail, TaskStatus } from '@/api/types'
import {
  formatDateTime,
  formatDuration,
  TASK_STATUS_TAG_TYPE
} from '@/utils/taskStatus'
import { useClipboard } from '@/composables'
import { toast } from '@/components/sdk'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcDialog from '@/components/sdk/overlay/FcDialog.vue'
import FcPagination from '@/components/sdk/navigation/FcPagination.vue'
import FcSelect from '@/components/sdk/form/FcSelect.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskPlatformTasksPage' })

const { t } = useI18n()
const router = useRouter()
const { copy } = useClipboard()

const loading = ref(false)
const tasks = ref<TaskDetail[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filterStatus = ref<string>('')
const filterType = ref<string>('')
// 0 = 全部租户 (后端 tenantId 收窄参数不传)
const filterTenantId = ref<number>(0)

const tenantOptions = ref<TenantItem[]>([])
const loadingTenants = ref(false)

const detailVisible = ref(false)
const detailRow = ref<TaskDetail | null>(null)

const detailBlocks = computed(() => {
  const row = detailRow.value
  if (!row) return []
  const blocks: Array<{ key: string; title: string; content: string }> = []
  if (row.stepsDetail?.length) {
    blocks.push({
      key: 'steps',
      title: t('lotask.tasks.detail.steps.title'),
      content: JSON.stringify(row.stepsDetail, null, 2),
    })
  }
  if (row.payload && Object.keys(row.payload).length) {
    blocks.push({
      key: 'payload',
      title: 'Payload',
      content: JSON.stringify(row.payload, null, 2),
    })
  }
  if (row.result && Object.keys(row.result).length) {
    blocks.push({
      key: 'result',
      title: 'Result',
      content: JSON.stringify(row.result, null, 2),
    })
  }
  return blocks
})

async function loadTasks() {
  loading.value = true
  try {
    const res = await getAdminTaskList({
      page: page.value,
      pageSize: pageSize.value,
      status: filterStatus.value || undefined,
      type: filterType.value || undefined,
      tenantId: filterTenantId.value > 0 ? filterTenantId.value : undefined
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

async function loadTenants() {
  loadingTenants.value = true
  try {
    const res = await listTenants({ page: 1, pageSize: 200 })
    tenantOptions.value = res.items || []
  } catch (err) {
    console.error('加载租户列表失败:', err)
  } finally {
    loadingTenants.value = false
  }
}

function tenantName(tenantId?: number): string {
  if (!tenantId) return ''
  return tenantOptions.value.find(tn => tn.id === tenantId)?.name || ''
}

function onFilterChange() {
  page.value = 1
  loadTasks()
}

function showDetail(row: TaskDetail) {
  detailRow.value = row
  detailVisible.value = true
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

function runningDuration(row: TaskDetail): string | null {
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

watch([page, pageSize], () => {
  loadTasks()
})

onMounted(() => {
  loadTasks()
  loadTenants()
})
</script>

<style scoped lang="scss">
.lotask-ptasks {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;

  .filter-status { width: 150px; }
  .filter-type   { width: 200px; }
  .filter-tenant { width: 220px; }
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

.tenant-raw {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: 'SF Mono', Monaco, monospace;
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

.pagination-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.detail-alert {
  margin-bottom: 12px;
}

.mono {
  font-family: 'SF Mono', Monaco, monospace;
}

.detail-block-title {
  margin: 14px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text);
}

.detail-pre {
  margin: 0;
  padding: 10px 12px;
  background: var(--app-bg-page, #f5f5f5);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
  line-height: 1.5;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
