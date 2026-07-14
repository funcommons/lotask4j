<script setup lang="ts">
// KpiSection — 单 KPI 数据块 (icon + value + unit + trend) (SDK).
// 直接继承 WorkSection (不 wrap TitledSection, 避免图标重复).
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatNumber } from '@/utils/format'
import WorkSection from '@/components/sdk/common/WorkSection.vue'

interface Props {
  title: string
  icon?: string
  value: number | string
  unit?: string
  trend?: number
  description?: string
}
const props = withDefaults(defineProps<Props>(), { icon: '', unit: '', trend: undefined, description: '' })

const { t } = useI18n()

const trendUp = computed(() => (props.trend ?? 0) > 0)

const autoDesc = computed(() => {
  if (props.description) return props.description
  if (props.trend === undefined || props.trend === null) return ''
  const sign = trendUp.value ? ' +' : ' '
  const pct = props.unit === '%' ? '%' : ''
  return `${t('common.vsLastWeek')}${sign}${props.trend}${pct}`
})
</script>

<template>
  <WorkSection no-header-border>
    <template #header>
      <div class="kpi-head">
        <span class="kpi-head__label">{{ title }}</span>
        <span v-if="autoDesc" class="kpi-head__trend" :class="{ 'is-up': trendUp }">
          <i :class="trendUp ? 'ri-arrow-up-line' : 'ri-arrow-down-line'" />
          {{ autoDesc }}
        </span>
      </div>
    </template>

    <div class="kpi-body">
      <div v-if="icon" class="kpi-body__icon">
        <i :class="icon" />
      </div>
      <div class="kpi-body__main">
        <div class="kpi-body__value">
          <span class="kpi-body__number">{{ typeof value === 'number' ? formatNumber(value) : value }}</span>
          <small v-if="unit" class="kpi-body__unit">{{ unit }}</small>
        </div>
        <div v-if="trend !== undefined && trend !== null" class="kpi-body__delta" :class="{ 'is-up': trendUp }">
          <i :class="trendUp ? 'ri-arrow-up-line' : 'ri-arrow-down-line'" />
          <span>{{ trendUp ? '+' : '' }}{{ trend }}{{ unit === '%' ? '%' : '' }}</span>
        </div>
      </div>
    </div>
  </WorkSection>
</template>

<style scoped>
.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.kpi-head__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text);
}
.kpi-head__trend {
  font-size: 11px;
  color: var(--app-text-tertiary);
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.kpi-head__trend.is-up { color: var(--el-color-success); }

.kpi-body {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0;
}
.kpi-body__icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  border-radius: var(--app-radius-md);
  background: var(--el-color-primary-light-9);
  color: var(--app-primary);
  flex-shrink: 0;
}
.kpi-body__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kpi-body__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.kpi-body__number {
  font-size: 32px;
  font-weight: 700;
  color: var(--app-text);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.kpi-body__unit {
  font-size: 14px;
  font-weight: 400;
  color: var(--app-text-secondary);
}
.kpi-body__delta {
  font-size: 11px;
  color: var(--app-text-tertiary);
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.kpi-body__delta.is-up { color: var(--el-color-success); }
</style>
