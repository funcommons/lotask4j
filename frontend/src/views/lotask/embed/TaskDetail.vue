<template>
  <div class="lotask-page-stub">
    <h2 class="lotask-page-stub__title">{{ t('router.embed-task-detail') }}</h2>
    <p class="lotask-page-stub__hint">Phase 3 填充: COPY benefit4j 最接近页面后改造</p>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>

<style scoped lang="scss">
.lotask-page-stub {
  padding: 48px 24px;
  text-align: center;
  &__title { margin: 0 0 8px; font-size: 20px; }
  &__hint { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
}
</style>
