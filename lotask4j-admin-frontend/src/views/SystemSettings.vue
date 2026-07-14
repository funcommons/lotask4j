<template>
  <div class="app-page">
    <TitledSection :title="t('systemSettingsExt.title')" icon="ri-settings-3-line">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">
          {{ t('systemSettingsExt.refresh') }}
        </el-button>
      </template>

      <div v-loading="loading">
        <!-- 系统基本信息 -->
        <WorkSection :title="t('systemSettingsExt.sections.system')" icon="ri-code-line" style="margin-bottom: 16px">
          <el-descriptions v-if="config" :column="2" border>
            <el-descriptions-item :label="t('systemSettingsExt.fields.appName')">{{ config.systemInfo.appName }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.appVersion')">{{ config.systemInfo.appVersion }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.springBootVersion')">{{ config.systemInfo.springBootVersion }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.javaVersion')">{{ config.systemInfo.javaVersion }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.osName')">{{ config.systemInfo.osName }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.osArch')">{{ config.systemInfo.osArch }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.startTime')">{{ config.systemInfo.startTime }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.uptime')">
              <el-tag type="success" effect="plain">{{ config.systemInfo.uptime }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </WorkSection>

        <!-- 异步线程池配置 -->
        <WorkSection
          v-if="config?.asyncConfig"
          :title="t('systemSettingsExt.sections.asyncPool')"
          icon="ri-flashlight-line"
          style="margin-bottom: 16px"
        >
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('systemSettingsExt.fields.corePoolSize')">
              <el-tag type="primary" effect="plain">{{ config.asyncConfig.corePoolSize }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.maxPoolSize')">
              <el-tag type="primary" effect="plain">{{ config.asyncConfig.maxPoolSize }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.queueCapacity')">
              <el-tag type="info" effect="plain">{{ config.asyncConfig.queueCapacity }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.threadNamePrefix')">
              <code>{{ config.asyncConfig.threadNamePrefix }}</code>
            </el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.waitForTasksToCompleteOnShutdown')">
              <el-tag :type="config.asyncConfig.waitForTasksToCompleteOnShutdown ? 'success' : 'danger'" effect="plain">
                {{ config.asyncConfig.waitForTasksToCompleteOnShutdown ? t('common.yes') : t('common.no') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.awaitTerminationSeconds')">
              {{ config.asyncConfig.awaitTerminationSeconds }} s
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="config.asyncConfig.activeCount !== undefined" class="runtime-status">
            <h4>{{ t('systemSettingsExt.runtime') }}</h4>
            <KpiLayout :columns="3">
              <KpiSection
                :title="t('systemSettingsExt.fields.activeCount')"
                :value="config.asyncConfig.activeCount"
              />
              <KpiSection
                :title="t('systemSettingsExt.fields.poolSize')"
                :value="config.asyncConfig.poolSize ?? 0"
              />
              <KpiSection
                :title="t('systemSettingsExt.fields.queueSize')"
                :value="config.asyncConfig.queueSize ?? 0"
              />
            </KpiLayout>
          </div>
        </WorkSection>

        <!-- 数据库配置 -->
        <WorkSection
          v-if="config"
          :title="t('systemSettingsExt.sections.database')"
          icon="ri-database-2-line"
          style="margin-bottom: 16px"
        >
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('systemSettingsExt.fields.dbType')">{{ config.databaseConfig.type }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.dbVersion')">{{ config.databaseConfig.version }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.dbUrl')" :span="2">
              <code class="db-url">{{ config.databaseConfig.url }}</code>
            </el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.dbMaxPoolSize')">{{ config.databaseConfig.maxPoolSize }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.dbActiveConnections')">{{ config.databaseConfig.activeConnections }}</el-descriptions-item>
          </el-descriptions>
        </WorkSection>

        <!-- Redis 配置 -->
        <WorkSection
          v-if="config"
          :title="t('systemSettingsExt.sections.redis')"
          icon="ri-cloud-line"
          style="margin-bottom: 16px"
        >
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('systemSettingsExt.fields.redisMode')">{{ config.redisConfig.mode }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.redisStatus')">
              <el-tag :type="config.redisConfig.status === 'Connected' ? 'success' : 'danger'" effect="plain">
                {{ config.redisConfig.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.redisHost')">{{ config.redisConfig.host }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.redisDatabase')">{{ config.redisConfig.database }}</el-descriptions-item>
          </el-descriptions>
        </WorkSection>

        <!-- JVM 信息 -->
        <WorkSection
          v-if="config?.jvmInfo"
          :title="t('systemSettingsExt.sections.jvm')"
          icon="ri-cpu-line"
          style="margin-bottom: 16px"
        >
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('systemSettingsExt.fields.jvmName')">{{ config.jvmInfo.name }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.jvmVersion')">{{ config.jvmInfo.version }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.maxMemory')">{{ formatBytes(config.jvmInfo.maxMemory) }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.totalMemory')">{{ formatBytes(config.jvmInfo.totalMemory) }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.usedMemory')">{{ formatBytes(config.jvmInfo.usedMemory) }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.freeMemory')">{{ formatBytes(config.jvmInfo.freeMemory) }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.cpuCores')">{{ config.jvmInfo.cpuCores }}</el-descriptions-item>
            <el-descriptions-item :label="t('systemSettingsExt.fields.threadCount')">{{ config.jvmInfo.threadCount }}</el-descriptions-item>
          </el-descriptions>
        </WorkSection>

        <!-- 任务统计 -->
        <WorkSection
          v-if="config"
          :title="t('systemSettingsExt.sections.taskStats')"
          icon="ri-bar-chart-box-line"
        >
          <KpiLayout :columns="4">
            <KpiSection
              :title="t('systemSettingsExt.fields.totalTasks')"
              :value="config.taskStats.totalTasks"
            />
            <KpiSection
              :title="t('systemSettingsExt.fields.pendingTasks')"
              :value="config.taskStats.pendingTasks"
            />
            <KpiSection
              :title="t('systemSettingsExt.fields.runningTasks')"
              :value="config.taskStats.runningTasks"
            />
            <KpiSection
              :title="t('systemSettingsExt.fields.successTasks')"
              :value="config.taskStats.successTasks"
            />
            <KpiSection
              :title="t('systemSettingsExt.fields.failedTasks')"
              :value="config.taskStats.failedTasks"
            />
            <KpiSection
              :title="t('systemSettingsExt.fields.cancelledTasks')"
              :value="config.taskStats.cancelledTasks"
            />
            <KpiSection
              :title="t('systemSettingsExt.fields.taskTypeCount')"
              :value="config.taskStats.taskTypeCount"
              :description="t('systemSettingsExt.stats.tasks')"
            />
            <KpiSection
              :title="t('systemSettingsExt.fields.onlineWorkerCount')"
              :value="config.taskStats.onlineWorkerCount"
              :description="t('systemSettingsExt.stats.workers')"
            />
          </KpiLayout>
        </WorkSection>
      </div>
    </TitledSection>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { getSystemConfig, type SystemConfig } from '@/api/admin'
import { usePolling } from '@/composables/usePolling'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import KpiLayout from '@/components/sdk/common/KpiLayout.vue'
import KpiSection from '@/components/sdk/common/KpiSection.vue'

const { t } = useI18n()
const loading = ref(false)
const config = ref<SystemConfig | null>(null)

usePolling(load, { interval: 30000 })

async function load() {
  loading.value = true
  try {
    const res = await getSystemConfig()
    config.value = res.data
  } catch (err: any) {
    console.error('加载系统配置失败:', err)
  } finally {
    loading.value = false
  }
}

function formatBytes(bytes: number): string {
  if (!bytes) return '0'
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
.runtime-status {
  margin-top: 24px;

  h4 {
    margin: 0 0 16px 0;
    font-size: 14px;
    color: var(--el-text-color-regular);
  }
}

.db-url {
  font-size: 12px;
  font-family: 'SF Mono', Monaco, monospace;
  word-break: break-all;
}
</style>
