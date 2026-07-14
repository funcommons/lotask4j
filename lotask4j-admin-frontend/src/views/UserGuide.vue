<template>
  <div class="app-page">
    <TitledSection :title="t('userGuide.title')" icon="ri-book-2-line">
      <WorkSection>
        <el-row :gutter="16">
          <el-col :span="24">
            <RowCard
              :title="t('userGuide.handbookTitle')"
              icon="ri-book-2-line"
              tone="primary"
            >
              <p class="guide-desc">{{ t('userGuide.handbookDesc') }}</p>
              <el-button
                type="primary"
                size="large"
                :icon="Link"
                @click="openHandbook"
                style="margin-top: 12px"
              >
                {{ t('userGuide.openHandbook') }}
              </el-button>
            </RowCard>
          </el-col>
        </el-row>

        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :span="8">
            <RowCard
              :title="t('userGuide.clientTitle')"
              icon="ri-user-line"
              @click="$router.push('/guide/client')"
              style="cursor: pointer"
            >
              <p class="guide-desc">{{ t('userGuide.clientDesc') }}</p>
            </RowCard>
          </el-col>
          <el-col :span="8">
            <RowCard
              :title="t('userGuide.workerTitle')"
              icon="ri-server-line"
              @click="$router.push('/guide/worker')"
              style="cursor: pointer"
            >
              <p class="guide-desc">{{ t('userGuide.workerDesc') }}</p>
            </RowCard>
          </el-col>
          <el-col :span="8">
            <RowCard
              :title="t('userGuide.simulatorTitle')"
              icon="ri-flask-line"
              @click="$router.push('/demo')"
              style="cursor: pointer"
            >
              <p class="guide-desc">{{ t('userGuide.simulatorDesc') }}</p>
            </RowCard>
          </el-col>
        </el-row>
      </WorkSection>
    </TitledSection>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Link } from '@element-plus/icons-vue'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import RowCard from '@/components/sdk/common/RowCard.vue'

const { t } = useI18n()

function openHandbook() {
  window.open('/handbook.html', '_blank')
}
</script>

<style scoped lang="scss">
.guide-desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin: 8px 0 0;
}
</style>
