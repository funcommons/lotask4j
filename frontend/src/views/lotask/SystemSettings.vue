<template>
  <div class="lotask-page lotask-system-settings">
    <FcSectionHeader :title="t('lotask.system.systemSettings.title')" :back="true" @back="router.back()">
      <template #actions>
        <FcButton variant="secondary" :loading="loading" :icon="Refresh" @click="load">
          {{ t('lotask.system.systemSettings.refresh') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <div v-loading="loading">
      <!-- 系统基本信息 -->
      <FcSection padding="md" shadow="sm" class="section-block">
        <template #header>
          <div class="sec-head">
            <i class="ri-code-line" />
            <h3>{{ t('lotask.system.systemSettings.sections.system') }}</h3>
          </div>
        </template>
        <el-descriptions v-if="config" :column="2" border>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.appName')">{{ config.systemInfo.appName }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.appVersion')">{{ config.systemInfo.appVersion }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.springBootVersion')">{{ config.systemInfo.springBootVersion }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.javaVersion')">{{ config.systemInfo.javaVersion }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.osName')">{{ config.systemInfo.osName }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.osArch')">{{ config.systemInfo.osArch }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.startTime')">{{ config.systemInfo.startTime }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.uptime')">
            <FcTag color="success" size="sm">{{ config.systemInfo.uptime }}</FcTag>
          </el-descriptions-item>
        </el-descriptions>
      </FcSection>

      <!-- 异步线程池配置 -->
      <FcSection
        v-if="config?.asyncConfig"
        padding="md"
        shadow="sm"
        class="section-block"
      >
        <template #header>
          <div class="sec-head">
            <i class="ri-flashlight-line" />
            <h3>{{ t('lotask.system.systemSettings.sections.asyncPool') }}</h3>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.corePoolSize')">
            <FcTag color="primary" size="sm">{{ config.asyncConfig.corePoolSize }}</FcTag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.maxPoolSize')">
            <FcTag color="primary" size="sm">{{ config.asyncConfig.maxPoolSize }}</FcTag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.queueCapacity')">
            <FcTag color="gray" size="sm">{{ config.asyncConfig.queueCapacity }}</FcTag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.threadNamePrefix')">
            <code>{{ config.asyncConfig.threadNamePrefix }}</code>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.waitForTasksToCompleteOnShutdown')">
            <FcTag :color="config.asyncConfig.waitForTasksToCompleteOnShutdown ? 'success' : 'danger'" size="sm">
              {{ config.asyncConfig.waitForTasksToCompleteOnShutdown ? t('lotask.system.common.yes') : t('lotask.system.common.no') }}
            </FcTag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.awaitTerminationSeconds')">
            {{ config.asyncConfig.awaitTerminationSeconds }} {{ t('lotask.system.taskType.execTimeoutUnit') }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="config.asyncConfig.activeCount !== undefined" class="runtime-status">
          <h4 class="runtime-title">{{ t('lotask.system.systemSettings.sections.runtime') }}</h4>
          <div class="kpi-grid">
            <div class="kpi-card">
              <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.activeCount') }}</div>
              <div class="kpi-value">{{ config.asyncConfig.activeCount }}</div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.poolSize') }}</div>
              <div class="kpi-value">{{ config.asyncConfig.poolSize ?? 0 }}</div>
            </div>
            <div class="kpi-card">
              <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.queueSize') }}</div>
              <div class="kpi-value">{{ config.asyncConfig.queueSize ?? 0 }}</div>
            </div>
          </div>
        </div>
      </FcSection>

      <!-- 数据库配置 -->
      <FcSection v-if="config" padding="md" shadow="sm" class="section-block">
        <template #header>
          <div class="sec-head">
            <i class="ri-database-2-line" />
            <h3>{{ t('lotask.system.systemSettings.sections.database') }}</h3>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.dbType')">{{ config.databaseConfig.type }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.dbVersion')">{{ config.databaseConfig.version }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.dbUrl')" :span="2">
            <code class="db-url">{{ config.databaseConfig.url }}</code>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.dbMaxPoolSize')">{{ config.databaseConfig.maxPoolSize }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.dbActiveConnections')">{{ config.databaseConfig.activeConnections }}</el-descriptions-item>
        </el-descriptions>
      </FcSection>

      <!-- Redis 配置 -->
      <FcSection v-if="config" padding="md" shadow="sm" class="section-block">
        <template #header>
          <div class="sec-head">
            <i class="ri-cloud-line" />
            <h3>{{ t('lotask.system.systemSettings.sections.redis') }}</h3>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.redisMode')">{{ config.redisConfig.mode }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.redisStatus')">
            <FcTag :color="config.redisConfig.status === 'Connected' ? 'success' : 'danger'" size="sm">
              {{ config.redisConfig.status }}
            </FcTag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.redisHost')">{{ config.redisConfig.host }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.redisDatabase')">{{ config.redisConfig.database }}</el-descriptions-item>
        </el-descriptions>
      </FcSection>

      <!-- JVM 信息 -->
      <FcSection v-if="config?.jvmInfo" padding="md" shadow="sm" class="section-block">
        <template #header>
          <div class="sec-head">
            <i class="ri-cpu-line" />
            <h3>{{ t('lotask.system.systemSettings.sections.jvm') }}</h3>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.jvmName')">{{ config.jvmInfo.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.jvmVersion')">{{ config.jvmInfo.version }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.maxMemory')">{{ formatBytes(config.jvmInfo.maxMemory) }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.totalMemory')">{{ formatBytes(config.jvmInfo.totalMemory) }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.usedMemory')">{{ formatBytes(config.jvmInfo.usedMemory) }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.freeMemory')">{{ formatBytes(config.jvmInfo.freeMemory) }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.cpuCores')">{{ config.jvmInfo.cpuCores }}</el-descriptions-item>
          <el-descriptions-item :label="t('lotask.system.systemSettings.fields.threadCount')">{{ config.jvmInfo.threadCount }}</el-descriptions-item>
        </el-descriptions>
      </FcSection>

      <!-- 任务统计 -->
      <FcSection v-if="config" padding="md" shadow="sm" class="section-block">
        <template #header>
          <div class="sec-head">
            <i class="ri-bar-chart-box-line" />
            <h3>{{ t('lotask.system.systemSettings.sections.taskStats') }}</h3>
          </div>
        </template>
        <div class="kpi-grid cols-4">
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.totalTasks') }}</div>
            <div class="kpi-value">{{ config.taskStats.totalTasks }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.pendingTasks') }}</div>
            <div class="kpi-value">{{ config.taskStats.pendingTasks }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.runningTasks') }}</div>
            <div class="kpi-value">{{ config.taskStats.runningTasks }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.successTasks') }}</div>
            <div class="kpi-value success">{{ config.taskStats.successTasks }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.failedTasks') }}</div>
            <div class="kpi-value danger">{{ config.taskStats.failedTasks }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.cancelledTasks') }}</div>
            <div class="kpi-value">{{ config.taskStats.cancelledTasks }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.taskTypeCount') }}</div>
            <div class="kpi-value">{{ config.taskStats.taskTypeCount }}</div>
            <div class="kpi-desc">{{ t('lotask.system.systemSettings.stats.tasks') }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">{{ t('lotask.system.systemSettings.fields.onlineWorkerCount') }}</div>
            <div class="kpi-value">{{ config.taskStats.onlineWorkerCount }}</div>
            <div class="kpi-desc">{{ t('lotask.system.systemSettings.stats.workers') }}</div>
          </div>
        </div>
      </FcSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getSystemConfig } from '@/api/admin'
import type { SystemConfig } from '@/api/types'
import { usePolling } from '@/composables'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskSystemSettingsPage' })

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const config = ref<SystemConfig | null>(null)

usePolling(load, { interval: 30000 })

async function load() {
  loading.value = true
  try {
    const res = await getSystemConfig()
    config.value = res as SystemConfig
  } catch (err) {
    console.error('load system config failed:', err)
  } finally {
    loading.value = false
  }
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const gb = bytes / 1024 / 1024 / 1024
  if (gb >= 1) return `${gb.toFixed(2)} GB`
  const mb = bytes / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(2)} MB`
  return `${(bytes / 1024).toFixed(2)} KB`
}

onMounted(() => {
  load()
})
</script>

<style scoped lang="scss">
.lotask-system-settings {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.section-block {
  :deep(.el-descriptions__label) {
    color: var(--el-text-color-secondary);
    width: 140px;
  }

  :deep(.el-descriptions__content) {
    word-break: break-all;
  }
}

.sec-head {
  display: flex;
  align-items: center;
  gap: 8px;

  i {
    font-size: 18px;
    color: var(--el-color-primary);
  }

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}

.runtime-status {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);

  .runtime-title {
    margin: 0 0 12px 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;

  &.cols-4 {
    grid-template-columns: repeat(4, 1fr);
  }
}

.kpi-card {
  padding: 14px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;

  .kpi-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 6px;
  }

  .kpi-value {
    font-size: 22px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    line-height: 1.2;

    &.success { color: var(--el-color-success); }
    &.danger  { color: var(--el-color-danger); }
  }

  .kpi-desc {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
  }
}

.db-url {
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
}
</style>
