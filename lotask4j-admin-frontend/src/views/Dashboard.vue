<template>
  <div class="app-page">
    <div class="dashboard">
      <!-- 实时状态：3 KPI -->
      <section class="dash-block">
        <header class="dash-block__head">
          <i class="ri-pulse-line" />
          <h2>{{ t('dashboard.realtimeStatus') }}</h2>
        </header>
        <KpiLayout :columns="3">
          <KpiSection
            :title="t('dashboard.pendingTasks')"
            icon="ri-time-line"
            :value="stats?.totalPending ?? 0"
          />
          <KpiSection
            :title="t('dashboard.runningTasks')"
            icon="ri-loader-4-line"
            :value="stats?.totalRunning ?? 0"
          />
          <KpiSection
            :title="t('dashboard.onlineWorkers')"
            icon="ri-server-line"
            :value="workerStatusText"
          />
        </KpiLayout>
      </section>

      <!-- 今日统计：4 KPI -->
      <section class="dash-block">
        <header class="dash-block__head">
          <i class="ri-bar-chart-box-line" />
          <h2>{{ t('dashboard.todayStats') }}</h2>
        </header>
        <KpiLayout :columns="4">
          <KpiSection
            :title="t('dashboard.successToday')"
            icon="ri-checkbox-circle-line"
            :value="stats?.todayStats?.success ?? 0"
          />
          <KpiSection
            :title="t('dashboard.failedToday')"
            icon="ri-error-warning-line"
            :value="stats?.todayStats?.failed ?? 0"
          />
          <KpiSection
            :title="t('dashboard.cancelledToday')"
            icon="ri-stop-circle-line"
            :value="stats?.todayStats?.cancelled ?? 0"
          />
          <KpiSection
            :title="t('dashboard.totalToday')"
            icon="ri-team-line"
            :value="totalToday"
            :description="`${t('dashboard.successRate')}: ${successRate}%`"
          >
            <el-progress
              :percentage="Number(successRate)"
              :stroke-width="8"
              :color="['#007aff', '#5ac8fa']"
              :show-text="false"
              style="margin-top: 8px"
            />
          </KpiSection>
        </KpiLayout>
      </section>

      <!-- Worker 状态：节点分布 + 在线率 -->
      <section class="dash-block">
        <header class="dash-block__head">
          <i class="ri-server-line" />
          <h2>{{ t('dashboard.workerStatus') }}</h2>
        </header>
        <el-row :gutter="16">
          <el-col :span="12">
            <RowCard :title="t('dashboard.nodeDistribution')" icon="ri-pie-chart-2-line">
              <KpiLayout :columns="2">
                <KpiSection
                  :title="t('dashboard.onlineNode')"
                  icon="ri-checkbox-circle-line"
                  :value="stats?.workerCount?.online ?? 0"
                />
                <KpiSection
                  :title="t('dashboard.offlineNode')"
                  icon="ri-close-circle-line"
                  :value="stats?.workerCount?.offline ?? 0"
                />
              </KpiLayout>
            </RowCard>
          </el-col>
          <el-col :span="12">
            <RowCard :title="t('dashboard.onlineRate')" icon="ri-line-chart-line">
              <div class="rate-chart">
                <el-progress
                  type="dashboard"
                  :percentage="onlineRate"
                  :stroke-width="10"
                  :color="['#34c759', '#5ac8fa']"
                />
                <div class="rate-text">{{ onlineRate }}%</div>
              </div>
            </RowCard>
          </el-col>
        </el-row>
      </section>

      <!-- 开发资源 -->
      <section class="dash-block">
        <header class="dash-block__head">
          <i class="ri-book-2-line" />
          <h2>{{ t('dashboardExt.devResources') }}</h2>
        </header>
        <el-row :gutter="16">
          <el-col :span="8">
            <RowCard
              :title="t('dashboardExt.swaggerTitle')"
              icon="ri-code-box-line"
              tone="primary"
              class="swagger-card"
              @click="openSwagger"
            >
              <p class="swagger-desc">{{ t('dashboardExt.swaggerDesc') }}</p>
              <el-button type="primary" :icon="Link" style="margin-top: 12px">
                {{ t('dashboardExt.swaggerTitle') }}
              </el-button>
            </RowCard>
          </el-col>
        </el-row>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Link } from '@element-plus/icons-vue'
import { getStatsOverview, type StatsOverview } from '@/api/admin'
import { usePolling } from '@/composables/usePolling'
import KpiLayout from '@/components/sdk/common/KpiLayout.vue'
import KpiSection from '@/components/sdk/common/KpiSection.vue'
import RowCard from '@/components/sdk/common/RowCard.vue'

const { t } = useI18n()
const stats = ref<StatsOverview | null>(null)

usePolling(loadStats, { interval: 5000 })

const totalToday = computed(() => {
  if (!stats.value?.todayStats) return 0
  const t = stats.value.todayStats
  return t.success + t.failed + t.cancelled
})

const successRate = computed(() => {
  const total = totalToday.value
  if (total === 0) return '0.0'
  const success = stats.value?.todayStats?.success ?? 0
  return ((success / total) * 100).toFixed(1)
})

const onlineRate = computed(() => {
  const online = stats.value?.workerCount?.online ?? 0
  const offline = stats.value?.workerCount?.offline ?? 0
  const total = online + offline
  if (total === 0) return 0
  return Math.round((online / total) * 100)
})

const workerStatusText = computed(() => {
  const online = stats.value?.workerCount?.online ?? 0
  const offline = stats.value?.workerCount?.offline ?? 0
  return `${online} / ${online + offline}`
})

async function loadStats() {
  try {
    const res = await getStatsOverview()
    stats.value = res.data
  } catch (err) {
    console.error('加载统计失败:', err)
  }
}

function openSwagger() {
  window.open('/swagger-ui.html', '_blank')
}

onMounted(() => {
  loadStats()
})

</script>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dash-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dash-block__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;

  i {
    font-size: 18px;
    color: var(--el-color-primary);
  }

  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}

.rate-chart {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 0;
}

.rate-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 24px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.swagger-card {
  cursor: pointer;
}

.swagger-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin: 8px 0 0;
}
</style>
