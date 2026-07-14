import axios from 'axios'

const http = axios.create({
  baseURL: '/api/v1/client',
  timeout: 10000
})

http.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.code !== undefined && data.code !== 0) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error) => {
    return Promise.reject(error)
  }
)

export interface TaskStatsVO {
  pending: number
  running: number
  success: number
  failed: number
  cancelled: number
}

export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  list: T[]
}

export interface Task {
  id: string
  type: string
  typeName?: string
  status: string
  progress: number
  currentStep?: string
  priority?: number
  retryCount?: number
  workerIp?: string
  callbackUrl?: string
  callbackStatus?: number
  startedAt?: string
  finishedAt?: string
  updatedAt?: string
  expiredAt?: string
  durationSeconds?: number | string
  payload?: any
  result?: any
  errorMsg?: string
  createdAt?: string
  stepsDetail?: TaskStep[]
}

export interface TaskStep {
  key: string
  name?: string
  status: string
  progress?: number
  detail?: string
  start_time?: string
  startTime?: string
  end_time?: string
  endTime?: string
  cost_ms?: number
}

export function getTaskList(params: {
  page?: number
  pageSize?: number
  status?: string
  taskType?: string
  id?: string
  isArchived?: boolean
  createdAtStart?: string
  createdAtEnd?: string
} = {}) {
  return http.get<any, { data: PageResult<Task> }>('/tasks', { params })
}

export function getTaskDetail(taskId: string) {
  return http.get<any, { data: Task }>(`/tasks/${taskId}`)
}

export function submitTask(data: any) {
  return http.post<any, { data: { id: string } }>('/tasks', data)
}

export function cancelTask(taskId: string) {
  return http.post<any, { data: null }>(`/tasks/${taskId}/cancel`)
}

export function getTaskStats() {
  return http.get<any, { data: TaskStatsVO }>('/tasks/stats')
}

export default http
