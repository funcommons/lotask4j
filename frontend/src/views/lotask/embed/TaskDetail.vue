<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTaskDetail } from '@/api/client'
import type { TaskDetail } from '@/api/types'
import { reportHeightToParent } from '@/utils/postMessage'

defineOptions({ name: 'LotaskEmbedTaskDetailPage' })

const route = useRoute()
const loading = ref(true)
const task = ref<TaskDetail | null>(null)
let stopResize: (() => void) | null = null

async function loadTask() {
  const taskId = route.query.taskId as string
  if (!taskId) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const detail = await getTaskDetail(taskId)
    task.value = detail
  } catch (err) {
    console.error('加载任务详情失败:', err)
  } finally {
    loading.value = false
  }
}

watch(() => route.query.taskId, () => {
  loadTask()
})

onMounted(() => {
  stopResize = reportHeightToParent()
  loadTask()
})

onUnmounted(() => {
  if (stopResize) stopResize()
})
</script>

<template>
  <div class="task-detail">
    <div v-if="loading" class="spinner">加载中...</div>
    <div v-else-if="!task" class="empty">未指定任务 ID</div>
    <div v-else>
      <div class="header">
        <h3>任务详情</h3>
        <span :class="`status-badge status-${task.status.toLowerCase()}`">{{ task.status }}</span>
      </div>

      <div class="info-grid">
        <div class="info-item">
          <span class="label">任务 ID</span>
          <span class="value mono">{{ task.id }}</span>
        </div>
        <div class="info-item">
          <span class="label">任务类型</span>
          <span class="value">{{ task.typeName || task.type }}</span>
        </div>
        <div class="info-item">
          <span class="label">当前步骤</span>
          <span class="value">{{ task.currentStep || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="label">创建时间</span>
          <span class="value">{{ task.createdAt }}</span>
        </div>
        <div v-if="task.finishedAt" class="info-item">
          <span class="label">完成时间</span>
          <span class="value">{{ task.finishedAt }}</span>
        </div>
      </div>

      <div class="progress-section">
        <div class="progress-label">进度</div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: task.progress + '%' }"></div>
          <span class="progress-text">{{ task.progress }}%</span>
        </div>
      </div>

      <div v-if="task.errorMsg" class="error-box">
        <strong>错误信息：</strong>{{ task.errorMsg }}
      </div>

      <div v-if="task.result" class="result-box">
        <h4>执行结果</h4>
        <pre>{{ JSON.stringify(task.result, null, 2) }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-detail {
  padding: 16px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.status-pending { background: #f2f2f7; color: #1d1d1f; }
.status-running { background: #fff7e6; color: #fa8c16; }
.status-success { background: #f6ffed; color: #52c41a; }
.status-failed { background: #fff2f0; color: #ff4d4f; }
.status-cancelled { background: #e5e5ea; color: #86868b; }

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.label {
  font-size: 12px;
  color: #86868b;
}

.value {
  font-size: 14px;
  color: #1d1d1f;
}

.mono {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
  color: #007aff;
}

.progress-section {
  margin-bottom: 16px;
}

.progress-label {
  font-size: 12px;
  color: #86868b;
  margin-bottom: 4px;
}

.progress-bar {
  position: relative;
  height: 24px;
  background: #f5f5f7;
  border-radius: 12px;
  overflow: hidden;
}

.progress-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, #007aff, #5856d6);
  transition: width 0.3s;
}

.progress-text {
  position: relative;
  z-index: 1;
  display: block;
  text-align: center;
  line-height: 24px;
  font-size: 12px;
  color: #1d1d1f;
  font-weight: 500;
}

.error-box {
  padding: 12px 16px;
  background: #fff2f0;
  border-left: 3px solid #ff4d4f;
  border-radius: 4px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #cf1322;
}

.result-box {
  background: #f5f5f7;
  border-radius: 8px;
  padding: 12px 16px;
}

.result-box h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
}

.result-box pre {
  margin: 0;
  font-size: 12px;
  font-family: 'SF Mono', Monaco, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  color: #1d1d1f;
}

.empty,
.spinner {
  text-align: center;
  padding: 40px;
  color: #86868b;
  font-size: 13px;
}
</style>