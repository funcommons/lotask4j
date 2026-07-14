import axios from 'axios'

const http = axios.create({
  baseURL: '/api/v1/admin',
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

export interface TodayStats {
  success: number
  failed: number
  cancelled: number
}

export interface WorkerCount {
  online: number
  offline: number
}

export interface StatsOverview {
  totalPending: number
  totalRunning: number
  todayStats: TodayStats
  workerCount: WorkerCount
}

export interface WorkerNodeVO {
  id: string
  workerKey?: string
  taskTypeKey: string
  workerIp: string
  hostname?: string
  status: string
  lastHeartbeatAt: string
}

export function getOnlineWorkers() {
  return http.get<any, { data: WorkerNodeVO[] }>('/workers')
}

export function getStatsOverview() {
  return http.get<any, { data: StatsOverview }>('/stats/overview')
}

export interface TaskTypeConfigVO {
  id: string
  typeKey: string
  name: string
  description?: string
  concurrencyLimit: number
  maxQueueSize?: number
  timeoutSeconds: number
  maxRetries: number
  isEnabled: boolean
  stepsConfig: Array<{ key: string; name: string; weight: number }>
  createdAt?: string
  updatedAt?: string
}

export interface TaskTypeConfigRequest {
  id?: number
  typeKey: string
  name: string
  description?: string
  concurrencyLimit: number
  maxQueueSize?: number
  timeoutSeconds: number
  maxRetries: number
  isEnabled?: number
  stepsDefinition: any[]
}

export function getAllTaskTypeConfigs() {
  return http.get<any, { data: TaskTypeConfigVO[] }>('/types')
}

export function saveTaskTypeConfig(data: TaskTypeConfigRequest) {
  return http.post<any, { data: null }>('/types', data)
}

export function deleteTaskTypeConfig(typeKey: string) {
  return http.delete<any, { data: null }>(`/types/${typeKey}`)
}

export interface AdminSubmitTaskRequest {
  type: string
  payload: Record<string, any>
  priority?: number
  callbackUrl?: string
}

export function adminSubmitTask(data: AdminSubmitTaskRequest) {
  return http.post<any, { data: { id: string } }>('/tasks/submit', data)
}

export interface SystemInfo {
  appName: string
  appVersion: string
  springBootVersion: string
  javaVersion: string
  osName: string
  osArch: string
  startTime: string
  uptime: string
}

export interface DatabaseConfig {
  type: string
  version: string
  url: string
  maxPoolSize: number
  activeConnections: number
}

export interface RedisConfig {
  mode: string
  host: string
  database: number
  status: string
}

export interface AsyncConfig {
  corePoolSize: number
  maxPoolSize: number
  queueCapacity: number
  threadNamePrefix: string
  waitForTasksToCompleteOnShutdown: boolean
  awaitTerminationSeconds: number
  activeCount?: number
  poolSize?: number
  queueSize?: number
}

export interface JvmInfo {
  name: string
  version: string
  maxMemory: number
  totalMemory: number
  usedMemory: number
  freeMemory: number
  cpuCores: number
  threadCount: number
}

export interface TaskStatsOverview {
  totalTasks: number
  pendingTasks: number
  runningTasks: number
  successTasks: number
  failedTasks: number
  cancelledTasks: number
  taskTypeCount: number
  onlineWorkerCount: number
}

export interface SystemConfig {
  systemInfo: SystemInfo
  databaseConfig: DatabaseConfig
  redisConfig: RedisConfig
  asyncConfig?: AsyncConfig
  jvmInfo?: JvmInfo
  taskStats: TaskStatsOverview
}

export function getSystemConfig() {
  return http.get<any, { data: SystemConfig }>('/system/config')
}

export default http
