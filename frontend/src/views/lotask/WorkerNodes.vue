<template>
  <div class="lotask-page lotask-worker-nodes">
    <FcSectionHeader :title="t('lotask.system.workerNodes.title')" :back="true" @back="router.back()">
      <template #actions>
        <FcButton variant="secondary" :loading="loading" :icon="Refresh" @click="loadWorkers">
          {{ t('lotask.system.common.refresh') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <FcSection padding="md" shadow="sm">
      <div class="filters">
        <FcSelect
          v-model="filterStatus"
          :placeholder="t('lotask.system.workerNodes.filterAll')"
          clearable
          class="filter-status"
        >
          <FcSelect.Option :label="t('lotask.system.workerNodes.online')" value="ONLINE" />
          <FcSelect.Option :label="t('lotask.system.workerNodes.busy')" value="BUSY" />
          <FcSelect.Option :label="t('lotask.system.workerNodes.offline')" value="OFFLINE" />
        </FcSelect>
        <span class="counter">{{ t('lotask.system.workerNodes.total', { n: filtered.length }) }}</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filtered"
        :empty-text="t('lotask.system.workerNodes.empty')"
        stripe
        border
        class="fc-table"
        style="margin-top: 12px"
      >
        <el-table-column :label="t('lotask.system.workerNodes.column.workerKey')" min-width="140">
          <template #default="{ row }">
            <code>{{ row.workerKey || row.id }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.workerNodes.column.workerIp')" min-width="140">
          <template #default="{ row }">
            <code>{{ row.workerIp || row.ip }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.workerNodes.column.hostname')" min-width="180">
          <template #default="{ row }">{{ row.hostname || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.workerNodes.column.taskType')" min-width="140">
          <template #default="{ row }">{{ row.taskTypeKey || row.taskType || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.workerNodes.column.status')" width="100" align="center">
          <template #default="{ row }">
            <FcTag :color="statusColor(row.status)" size="sm">
              {{ statusLabel(row.status) }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.workerNodes.column.lastHeartbeat')" min-width="220">
          <template #default="{ row }">
            <div class="heartbeat-cell">
              <div>{{ formatDateTime(row.lastHeartbeatAt) }}</div>
              <div class="relative">{{ relativeTime(row.lastHeartbeatAt) }}</div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </FcSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getOnlineWorkers } from '@/api/admin'
import type { WorkerNode } from '@/api/types'
import { formatDateTime, relativeTime } from '@/utils/taskStatus'
import { usePolling } from '@/composables'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcSelect from '@/components/sdk/form/FcSelect.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskWorkerNodesPage' })

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const workers = ref<WorkerNode[]>([])
const filterStatus = ref<string>('')

usePolling(loadWorkers, { interval: 10000 })

const filtered = computed(() => {
  if (!filterStatus.value) return workers.value
  return workers.value.filter((w: WorkerNode) => w.status === filterStatus.value)
})

async function loadWorkers() {
  loading.value = true
  try {
    const res = await getOnlineWorkers()
    workers.value = Array.isArray(res) ? res : []
  } catch (err) {
    console.error('load workers failed:', err)
  } finally {
    loading.value = false
  }
}

function statusColor(status: string): 'success' | 'warning' | 'gray' {
  if (status === 'ONLINE') return 'success'
  if (status === 'BUSY') return 'warning'
  return 'gray'
}

function statusLabel(status: string): string {
  if (status === 'ONLINE') return t('lotask.system.workerNodes.online')
  if (status === 'BUSY') return t('lotask.system.workerNodes.busy')
  if (status === 'OFFLINE') return t('lotask.system.workerNodes.offline')
  return status
}

onMounted(() => {
  loadWorkers()
})
</script>

<style scoped lang="scss">
.lotask-worker-nodes {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.filters {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;

  .filter-status { width: 180px; }
  .counter {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.heartbeat-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;

  .relative {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}
</style>
