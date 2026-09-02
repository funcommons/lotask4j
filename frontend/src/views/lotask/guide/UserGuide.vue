<template>
  <div class="lotask-page lotask-user-guide">
    <FcSectionHeader :title="t('lotask.guides.userGuide.title')" />

    <!-- 主手册大卡片 -->
    <FcSection padding="md" shadow="sm" hover>
      <div class="handbook-card">
        <div class="handbook-icon">
          <i class="ri-book-2-line" />
        </div>
        <div class="handbook-body">
          <h2 class="handbook-title">{{ t('lotask.guides.userGuide.handbookTitle') }}</h2>
          <p class="handbook-desc">{{ t('lotask.guides.userGuide.handbookDesc') }}</p>
          <FcButton type="primary" :icon="LinkIcon" @click="openHandbook">
            {{ t('lotask.guides.userGuide.openHandbook') }}
          </FcButton>
        </div>
      </div>
    </FcSection>

    <!-- 3 个子卡片 (模拟器仅租户域 — demo 路由只在 /tenant 树) -->
    <div class="guide-grid">
      <FcSection padding="md" shadow="sm" hover>
        <div class="guide-card" @click="goTo(`${base}/guide/client`)">
          <i class="ri-user-line guide-card__icon" />
          <h3>{{ t('lotask.guides.userGuide.clientTitle') }}</h3>
          <p>{{ t('lotask.guides.userGuide.clientDesc') }}</p>
        </div>
      </FcSection>

      <FcSection padding="md" shadow="sm" hover>
        <div class="guide-card" @click="goTo(`${base}/guide/worker`)">
          <i class="ri-server-line guide-card__icon" />
          <h3>{{ t('lotask.guides.userGuide.workerTitle') }}</h3>
          <p>{{ t('lotask.guides.userGuide.workerDesc') }}</p>
        </div>
      </FcSection>

      <FcSection v-if="!isPlatform" padding="md" shadow="sm" hover>
        <div class="guide-card" @click="goTo(`${base}/demo`)">
          <i class="ri-flask-line guide-card__icon" />
          <h3>{{ t('lotask.guides.userGuide.simulatorTitle') }}</h3>
          <p>{{ t('lotask.guides.userGuide.simulatorDesc') }}</p>
        </div>
      </FcSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { Link } from '@element-plus/icons-vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskUserGuidePage' })

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

// 双域挂载 (/platform/guide 与 /tenant/guide 同组件): 子页跳转跟随当前域
const isPlatform = route.path.startsWith('/platform')
const base = isPlatform ? '/platform' : '/tenant'

const LinkIcon = Link

function openHandbook() {
  window.open('/handbook.html', '_blank')
}

function goTo(path: string) {
  router.push(path)
}
</script>

<style scoped lang="scss">
.lotask-user-guide {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.handbook-card {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.handbook-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--el-color-primary) 12%, transparent);
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  flex-shrink: 0;
}

.handbook-body {
  flex: 1;
  min-width: 0;
}

.handbook-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.handbook-desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.guide-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--app-block-mb, 16px);
}

.guide-card {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__icon {
    font-size: 28px;
    color: var(--el-color-primary);
  }

  h3 {
    margin: 4px 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  p {
    margin: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.6;
  }
}

@media (max-width: 768px) {
  .guide-grid {
    grid-template-columns: 1fr;
  }
}
</style>