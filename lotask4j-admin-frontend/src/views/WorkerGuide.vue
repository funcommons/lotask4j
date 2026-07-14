<template>
  <div class="app-page">
    <TitledSection :title="t('workerGuide.title')" icon="ri-server-line">
      <WorkSection>
        <el-tabs v-model="activeTab" class="guide-tabs">
          <el-tab-pane :label="`📚 ${t('workerGuideExt.tabs.overview')}`" name="overview">
            <div class="markdown-body" v-html="overviewHtml" />
          </el-tab-pane>
          <el-tab-pane :label="`📡 ${t('workerGuideExt.tabs.api')}`" name="api">
            <div class="markdown-body" v-html="apiHtml" />
          </el-tab-pane>
          <el-tab-pane :label="`🛠 ${t('workerGuideExt.tabs.implementation')}`" name="implementation">
            <div class="markdown-body" v-html="implementationHtml" />
          </el-tab-pane>
          <el-tab-pane :label="`⭐ ${t('workerGuideExt.tabs.bestPractices')}`" name="bestPractices">
            <div class="markdown-body" v-html="bestPracticesHtml" />
          </el-tab-pane>
        </el-tabs>
      </WorkSection>
    </TitledSection>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import 'highlight.js/styles/github.css'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import { renderMarkdown } from '@/utils/markdown'
import overviewMd from '@/content/worker-guide/overview.md?raw'
import apiListMd from '@/content/worker-guide/api-list.md?raw'
import implementationMd from '@/content/worker-guide/implementation.md?raw'
import bestPracticesMd from '@/content/worker-guide/best-practices.md?raw'

const { t } = useI18n()
const activeTab = ref<'overview' | 'api' | 'implementation' | 'bestPractices'>('overview')

const overviewHtml = computed(() => renderMarkdown(overviewMd))
const apiHtml = computed(() => renderMarkdown(apiListMd))
const implementationHtml = computed(() => renderMarkdown(implementationMd))
const bestPracticesHtml = computed(() => renderMarkdown(bestPracticesMd))
</script>

<style scoped lang="scss">
.guide-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}

.markdown-body {
  font-size: 14px;
  line-height: 1.75;
  color: var(--el-text-color-primary);

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    font-weight: 600;
    margin: 24px 0 12px;
    color: var(--el-text-color-primary);
  }

  :deep(h1) { font-size: 22px; border-bottom: 2px solid var(--el-border-color); padding-bottom: 8px; }
  :deep(h2) { font-size: 18px; border-bottom: 1px solid var(--el-border-color-lighter); padding-bottom: 6px; }
  :deep(h3) { font-size: 16px; }
  :deep(h4) { font-size: 14px; }

  :deep(p) {
    margin: 10px 0;
  }

  :deep(ul),
  :deep(ol) {
    margin: 10px 0;
    padding-left: 24px;
  }

  :deep(li) {
    margin: 4px 0;
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 16px 0;
    font-size: 13px;

    th,
    td {
      border: 1px solid var(--el-border-color);
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: var(--el-fill-color-light);
      font-weight: 600;
    }

    tr:nth-child(2n) {
      background: var(--el-fill-color-lighter);
    }
  }

  :deep(pre.hljs) {
    background: var(--el-fill-color-dark) !important;
    border: 1px solid var(--el-border-color);
    border-radius: 6px;
    padding: 12px 16px;
    overflow-x: auto;
    margin: 12px 0;
    font-size: 12px;
    font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;

    code {
      background: transparent;
      color: var(--el-text-color-primary);
    }
  }

  :deep(code) {
    background: var(--el-fill-color-light);
    color: var(--el-color-primary);
    padding: 2px 6px;
    border-radius: 3px;
    font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace;
    font-size: 0.92em;
  }

  :deep(pre code) {
    background: transparent;
    color: inherit;
    padding: 0;
  }

  :deep(blockquote) {
    border-left: 4px solid var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    padding: 8px 16px;
    margin: 12px 0;
    color: var(--el-text-color-regular);
  }

  :deep(hr) {
    border: 0;
    border-top: 1px solid var(--el-border-color);
    margin: 20px 0;
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
