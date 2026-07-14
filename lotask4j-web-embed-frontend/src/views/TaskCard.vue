<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTaskDetail, type Task } from '@/services/api'

const route = useRoute()
const loading = ref(true)
const task = ref<Task | null>(null)
let timer: number | null = null

async function loadTask() {
  const taskId = route.query.taskId as string
  if (!taskId) {
    loading.value = false
    return
  }
  try {
    const res = await getTaskDetail(taskId)
    task.value = res.data
  } catch (err: any) {
    console.error('加载任务卡片失败:', err)
  } finally {
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  timer = window.setInterval(loadTask, 3000)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(() => route.query.taskId, () => {
  loadTask()
  startPolling()
})

onMounted(() => {
  loadTask()
  startPolling()
})

onUnmounted(stopPolling)
</script>

<template>
  <div class="task-card">
    <div v-if="loading && !task" class="spinner">加载中...</div>
    <div v-else-if="!task" class="empty">未指定任务</div>
    <div v-else>
      <div class="card-header">
        <span class="task-type">{{ task.typeName || task.type }}</span>
        <span :class="`status-badge status-${task.status.toLowerCase()}`">{{ task.status }}</span>
      </div>

      <div class="task-id">{{ task.id }}</div>

      <div class="progress-section">
        <div class="circular-progress">
          <svg viewBox="0 0 100 100">
            <circle cx="50" cy="50" r="42" class="progress-bg" />
            <circle
              cx="50" cy="50" r="42"
              class="progress-fg"
              :stroke-dasharray="2 * Math.PI * 42"
              :stroke-dashoffset="2 * Math.PI * 42 * (1 - task.progress / 100)"
            />
          </svg>
          <div class="progress-text">{{ task.progress }}%</div>
        </div>
      </div>

      <div v-if="task.currentStep" class="current-step">
        当前步骤：<strong>{{ task.currentStep }}</strong>
      </div>

      <div v-if="task.errorMsg" class="error">
        {{ task.errorMsg }}
      </div>

      <div v-else-if="task.status === 'SUCCESS' && task.result" class="success">
        ✓ 执行成功
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-card {
  padding: 20px;
  max-width: 400px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.task-type {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
}

.task-id {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 11px;
  color: #86868b;
  margin-bottom: 20px;
}

.progress-section {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}

.circular-progress {
  position: relative;
  width: 120px;
  height: 120px;
}

.circular-progress svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.progress-bg {
  fill: none;
  stroke: #f5f5f7;
  stroke-width: 8;
}

.progress-fg {
  fill: none;
  stroke: #007aff;
  stroke-width: 8;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.5s ease;
}

.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 20px;
  font-weight: 600;
  color: #1d1d1f;
}

.current-step {
  text-align: center;
  font-size: 13px;
  color: #86868b;
  margin-bottom: 12px;
}

.status-badge {
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.status-pending { background: #f2f2f7; color: #1d1d1f; }
.status-running { background: #fff7e6; color: #fa8c16; }
.status-success { background: #f6ffed; color: #52c41a; }
.status-failed { background: #fff2f0; color: #ff4d4f; }
.status-cancelled { background: #e5e5ea; color: #86868b; }

.error {
  text-align: center;
  padding: 8px;
  background: #fff2f0;
  color: #ff4d4f;
  border-radius: 6px;
  font-size: 12px;
}

.success {
  text-align: center;
  padding: 8px;
  background: #f6ffed;
  color: #52c41a;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
}

.empty {
  text-align: center;
  padding: 40px;
  color: #86868b;
}
</style>
