<template>
  <div class="lotask-page lotask-dashboard">
    <FcSectionHeader
      :title="t('router.dashboard')"
      :back="true"
      @back="router.back()"
    />

    <!-- 实时状态: 3 KPI -->
    <FcSection padding="md" shadow="sm">
      <template #header>
        <div class="dash-block__head">
          <i class="ri-pulse-line" />
          <h2>{{ t('lotask.tasks.dashboard.realtimeStatus') }}</h2>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <div class="kpi-tile">
            <i class="ri-time-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.pendingTasks') }}</div>
              <div class="kpi-tile__value">{{ stats?.totalPending ?? 0 }}</div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="8">
          <div class="kpi-tile">
            <i class="ri-loader-4-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.runningTasks') }}</div>
              <div class="kpi-tile__value">{{ stats?.totalRunning ?? 0 }}</div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="8">
          <div class="kpi-tile">
            <i class="ri-server-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.onlineWorkers') }}</div>
              <div class="kpi-tile__value">{{ workerStatusText }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </FcSection>

    <!-- 今日统计: 4 KPI -->
    <FcSection padding="md" shadow="sm">
      <template #header>
        <div class="dash-block__head">
          <i class="ri-bar-chart-box-line" />
          <h2>{{ t('lotask.tasks.dashboard.todayStats') }}</h2>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="6">
          <div class="kpi-tile kpi-tile--success">
            <i class="ri-checkbox-circle-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.successToday') }}</div>
              <div class="kpi-tile__value">{{ stats?.todayStats?.success ?? 0 }}</div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="kpi-tile kpi-tile--danger">
            <i class="ri-error-warning-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.failedToday') }}</div>
              <div class="kpi-tile__value">{{ stats?.todayStats?.failed ?? 0 }}</div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="kpi-tile kpi-tile--neutral">
            <i class="ri-stop-circle-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.cancelledToday') }}</div>
              <div class="kpi-tile__value">{{ stats?.todayStats?.cancelled ?? 0 }}</div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="kpi-tile kpi-tile--primary">
            <i class="ri-team-line kpi-tile__icon" />
            <div class="kpi-tile__body">
              <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.totalToday') }}</div>
              <div class="kpi-tile__value">{{ totalToday }}</div>
              <div class="kpi-tile__desc">
                {{ t('lotask.tasks.dashboard.successRate') }}: {{ successRate }}%
              </div>
              <el-progress
                :percentage="Number(successRate)"
                :stroke-width="6"
                :show-text="false"
                :color="['#007aff', '#5ac8fa']"
                class="kpi-tile__progress"
              />
            </div>
          </div>
        </el-col>
      </el-row>
    </FcSection>

    <!-- Worker 状态: 节点分布 + 在线率 -->
    <FcSection padding="md" shadow="sm">
      <template #header>
        <div class="dash-block__head">
          <i class="ri-server-line" />
          <h2>{{ t('lotask.tasks.dashboard.workerStatus') }}</h2>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <FcSectionCard padding="md" shadow="none">
            <div class="dash-subhead">
              <i class="ri-pie-chart-2-line" />
              <span>{{ t('lotask.tasks.dashboard.nodeDistribution') }}</span>
            </div>
            <el-row :gutter="16">
              <el-col :span="12">
                <div class="kpi-tile kpi-tile--success kpi-tile--compact">
                  <i class="ri-checkbox-circle-line kpi-tile__icon" />
                  <div class="kpi-tile__body">
                    <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.onlineNode') }}</div>
                    <div class="kpi-tile__value">{{ stats?.workerCount?.online ?? 0 }}</div>
                  </div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="kpi-tile kpi-tile--danger kpi-tile--compact">
                  <i class="ri-close-circle-line kpi-tile__icon" />
                  <div class="kpi-tile__body">
                    <div class="kpi-tile__title">{{ t('lotask.tasks.dashboard.offlineNode') }}</div>
                    <div class="kpi-tile__value">{{ stats?.workerCount?.offline ?? 0 }}</div>
                  </div>
                </div>
              </el-col>
            </el-row>
          </FcSectionCard>
        </el-col>
        <el-col :xs="24" :md="12">
          <FcSectionCard padding="md" shadow="none">
            <div class="dash-subhead">
              <i class="ri-line-chart-line" />
              <span>{{ t('lotask.tasks.dashboard.onlineRate') }}</span>
            </div>
            <div class="rate-chart">
              <el-progress
                type="dashboard"
                :percentage="onlineRate"
                :stroke-width="10"
                :color="['#34c759', '#5ac8fa']"
              />
              <div class="rate-text">{{ onlineRate }}%</div>
            </div>
          </FcSectionCard>
        </el-col>
      </el-row>
    </FcSection>

    <!-- 开发资源 -->
    <FcSection padding="md" shadow="sm">
      <template #header>
        <div class="dash-block__head">
          <i class="ri-book-2-line" />
          <h2>{{ t('lotask.tasks.dashboard.devResources') }}</h2>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8">
          <FcSectionCard padding="md" shadow="none" hover>
            <div class="swagger-card" @click="openSwagger">
              <div class="swagger-card__title">
                <i class="ri-code-box-line" />
                <span>{{ t('lotask.tasks.dashboard.swaggerTitle') }}</span>
              </div>
              <p class="swagger-desc">{{ t('lotask.tasks.dashboard.swaggerDesc') }}</p>
              <FcButton variant="primary" :loading="false">
                {{ t('lotask.tasks.dashboard.swaggerTitle') }}
              </FcButton>
            </div>
          </FcSectionCard>
        </el-col>
      </el-row>
    </FcSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getStatsOverview } from '@/api/admin'
import type { StatsOverview } from '@/api/types'
import { usePolling } from '@/composables/usePolling'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcSectionCard from '@/components/sdk/display/FcSectionCard.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskDashboardPage' })

const { t } = useI18n()
const router = useRouter()
const stats = ref<StatsOverview | null>(null)

usePolling(loadStats, { interval: 5000 })

const totalToday = computed(() => {
  if (!stats.value?.todayStats) return 0
  const td = stats.value.todayStats
  return td.success + td.failed + td.cancelled
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
    stats.value = res
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
.lotask-dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
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

.dash-subhead {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;

  i {
    font-size: 16px;
    color: var(--el-color-primary);
  }
}

.kpi-tile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  height: 100%;

  &__icon {
    font-size: 28px;
    color: var(--el-color-primary);
    flex-shrink: 0;
  }

  &__body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__title {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__value {
    font-size: 22px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    line-height: 1.2;
  }

  &__desc {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-top: 2px;
  }

  &__progress {
    margin-top: 4px;
  }

  &--compact {
    padding: 10px 12px;
  }

  &--success .kpi-tile__icon { color: var(--el-color-success); }
  &--danger  .kpi-tile__icon { color: var(--el-color-danger); }
  &--neutral .kpi-tile__icon { color: var(--el-text-color-secondary); }
  &--primary .kpi-tile__icon { color: var(--el-color-primary); }
}

.rate-chart {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 0;
}

.rate-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 22px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.swagger-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;

  &__title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);

    i {
      font-size: 18px;
      color: var(--el-color-primary);
    }
  }
}

.swagger-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin: 4px 0 8px;
}
</style>