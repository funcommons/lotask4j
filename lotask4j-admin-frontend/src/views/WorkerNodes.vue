<template>
  <div class="app-page">
    <TitledSection :title="t('workerNodes.title')" icon="ri-server-line">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadWorkers">
          {{ t('taskList.action.refresh') }}
        </el-button>
      </template>
      <WorkSection>
        <div class="filters">
          <el-select
            v-model="filterStatus"
            :placeholder="t('workerNodes.filter.all')"
            clearable
            style="width: 180px"
          >
            <el-option :label="t('workerNodes.online')" value="ONLINE" />
            <el-option :label="t('workerNodesExt.column.busy')" value="BUSY" />
            <el-option :label="t('workerNodes.offline')" value="OFFLINE" />
          </el-select>
          <span class="counter">{{ t('workerNodesExt.total', { n: filtered.length }) }}</span>
        </div>

        <el-table
          v-loading="loading"
          :data="filtered"
          :empty-text="t('workerNodes.empty')"
          stripe
          border
          style="margin-top: 12px"
        >
          <el-table-column :label="t('workerNodesExt.column.workerId')" prop="workerKey" min-width="120">
            <template #default="{ row }">
              <code>{{ row.workerKey || row.id }}</code>
            </template>
          </el-table-column>
          <el-table-column :label="t('workerNodes.ip')" min-width="140">
            <template #default="{ row }">
              <code>{{ row.workerIp || row.ip }}</code>
            </template>
          </el-table-column>
          <el-table-column :label="t('workerNodesExt.column.hostname')" min-width="180">
            <template #default="{ row }">{{ row.hostname || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('workerNodes.taskType')" min-width="140">
            <template #default="{ row }">{{ row.taskTypeKey || row.taskType || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('workerNodes.status')" min-width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="light">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('workerNodes.lastHeartbeat')" min-width="220">
            <template #default="{ row }">
              <div class="heartbeat-cell">
                <div>{{ formatTime(row.lastHeartbeatAt) }}</div>
                <div class="relative">{{ getRelativeTime(row.lastHeartbeatAt) }}</div>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </WorkSection>
    </TitledSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { getOnlineWorkers, type WorkerNodeVO } from '@/api/admin'
import { formatTime, getRelativeTime } from '@/utils/time'
import { usePolling } from '@/composables/usePolling'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'

const { t } = useI18n()
const loading = ref(false)
const workers = ref<WorkerNodeVO[]>([])
const filterStatus = ref<string>('')

usePolling(loadWorkers, { interval: 10000 })

const filtered = computed(() => {
  if (!filterStatus.value) return workers.value
  return workers.value.filter((w) => w.status === filterStatus.value)
})

async function loadWorkers() {
  loading.value = true
  try {
    const res = await getOnlineWorkers()
    workers.value = res.data || []
  } catch (err) {
    console.error('加载 Worker 列表失败:', err)
  } finally {
    loading.value = false
  }
}

function statusType(status: string): 'success' | 'warning' | 'danger' {
  if (status === 'ONLINE') return 'success'
  if (status === 'BUSY') return 'warning'
  return 'danger'
}

function statusLabel(status: string): string {
  if (status === 'ONLINE') return t('workerNodes.online')
  if (status === 'BUSY') return t('workerNodesExt.column.busy')
  if (status === 'OFFLINE') return t('workerNodes.offline')
  return status
}

onMounted(() => {
  loadWorkers()
})

</script>

<style scoped lang="scss">
.filters {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

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
