<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getTaskList, type Task } from '@/services/api'

const loading = ref(true)
const tasks = ref<Task[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filterStatus = ref<string>('')

const filteredTasks = computed(() => {
  if (!filterStatus.value) return tasks.value
  return tasks.value.filter(t => t.status === filterStatus.value)
})

async function loadTasks() {
  loading.value = true
  try {
    const res = await getTaskList({
      page: page.value,
      pageSize: pageSize.value,
      status: filterStatus.value || undefined
    })
    tasks.value = res.data.items
    total.value = res.data.total
  } catch (err: any) {
    console.error('加载任务失败:', err)
  } finally {
    loading.value = false
  }
}

function getStatusClass(status: string) {
  return `status-badge status-${status.toLowerCase()}`
}

onMounted(loadTasks)
</script>

<template>
  <div class="task-list">
    <div class="header">
      <h3>任务列表</h3>
      <div class="filters">
        <select v-model="filterStatus" @change="loadTasks">
          <option value="">全部状态</option>
          <option value="PENDING">待处理</option>
          <option value="RUNNING">执行中</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
          <option value="CANCELLED">已取消</option>
        </select>
        <button class="btn" @click="loadTasks">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="spinner">加载中...</div>

    <div v-else-if="tasks.length === 0" class="empty">
      暂无任务
    </div>

    <table v-else class="task-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>类型</th>
          <th>状态</th>
          <th>进度</th>
          <th>创建时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="task in filteredTasks" :key="task.id">
          <td class="task-id">{{ task.id }}</td>
          <td>{{ task.typeName || task.type }}</td>
          <td>
            <span :class="getStatusClass(task.status)">{{ task.status }}</span>
          </td>
          <td>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: task.progress + '%' }"></div>
              <span class="progress-text">{{ task.progress }}%</span>
            </div>
          </td>
          <td class="time">{{ task.createdAt }}</td>
        </tr>
      </tbody>
    </table>

    <div v-if="total > pageSize" class="pagination">
      <button class="btn" :disabled="page === 1" @click="page--; loadTasks()">上一页</button>
      <span>{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
      <button class="btn" :disabled="page * pageSize >= total" @click="page++; loadTasks()">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.task-list {
  padding: 16px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header h3 {
  font-size: 18px;
  font-weight: 600;
}

.filters {
  display: flex;
  gap: 8px;
}

.filters select,
.btn {
  padding: 6px 12px;
  border: 1px solid #d1d1d6;
  border-radius: 6px;
  background: white;
  font-size: 13px;
  cursor: pointer;
}

.btn {
  background: #007aff;
  color: white;
  border-color: #007aff;
}

.btn:hover {
  background: #096dd9;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.task-table {
  width: 100%;
  border-collapse: collapse;
  background: white;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e5ea;
}

.task-table th {
  background: #f5f5f7;
  padding: 10px 12px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: #86868b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.task-table td {
  padding: 12px;
  border-top: 1px solid #e5e5ea;
  font-size: 13px;
}

.task-id {
  font-family: 'SF Mono', Monaco, monospace;
  color: #007aff;
  font-size: 12px;
}

.time {
  color: #86868b;
  font-size: 12px;
}

.status-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.status-pending { background: #f2f2f7; color: #1d1d1f; }
.status-running { background: #fff7e6; color: #fa8c16; }
.status-success { background: #f6ffed; color: #52c41a; }
.status-failed { background: #fff2f0; color: #ff4d4f; }
.status-cancelled { background: #e5e5ea; color: #86868b; }
.status-cancelling { background: #fff7e6; color: #fa8c16; }

.progress-bar {
  position: relative;
  height: 18px;
  background: #f5f5f7;
  border-radius: 9px;
  overflow: hidden;
  min-width: 80px;
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
  line-height: 18px;
  font-size: 11px;
  color: #1d1d1f;
}

.empty {
  text-align: center;
  padding: 40px;
  color: #86868b;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  font-size: 13px;
}
</style>
