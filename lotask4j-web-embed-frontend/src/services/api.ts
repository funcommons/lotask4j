import axios from 'axios'
import type { AxiosInstance } from 'axios'

/**
 * API 客户端
 * 复用后端 /api/v1/client/* 接口
 * 鉴权通过 Cookie (ASTS_USER_ID)
 */
const api: AxiosInstance = axios.create({
  baseURL: '/api/v1/client',
  timeout: 10000,
  withCredentials: true
})

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.code !== undefined && data.code !== 0) {
      throw new Error(data.message || '请求失败')
    }
    return data
  },
  (error) => {
    console.error('[Web Embed] API 请求失败:', error)
    return Promise.reject(error)
  }
)

// ==================== 任务接口 ====================

export interface Task {
  id: string
  type: string
  typeName?: string
  status: string
  progress: number
  currentStep?: string
  payload?: any
  result?: any
  errorMsg?: string
  createdAt?: string
  finishedAt?: string
}

export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  items: T[]
}

/** 任务列表 */
export function getTaskList(params: {
  page?: number
  pageSize?: number
  status?: string
  taskType?: string
} = {}) {
  return api.get<any, { data: PageResult<Task> }>('/tasks', { params })
}

/** 任务详情 */
export function getTaskDetail(taskId: string) {
  return api.get<any, { data: Task }>(`/tasks/${taskId}`)
}

/** 任务统计 */
export function getTaskStats() {
  return api.get<any, { data: { pending: number; running: number; success: number; failed: number; cancelled: number } }>('/tasks/stats')
}

export default api
