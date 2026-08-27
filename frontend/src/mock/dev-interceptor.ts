import type { AxiosInstance } from 'axios'

/**
 * dev-only mock interceptor — lotask4j 版.
 *
 * 目的: 无后端时让前端独立可跑.
 *   - POST /api/v1/auth/token: 任意 client_id/client_secret 都发 mock token
 *   - client/admin/embed-config 只读端点: 返回仿真数据
 * 仅 import.meta.env.DEV 生效, build 时被 Vite tree-shake.
 *
 * 安装位置: src/api/request.ts (业务 response 拦截器之前).
 */

const MOCK_ACCESS = 'mock-access-token'

// —— mock 数据 ——

const MOCK_TASK_LIST = {
  list: [
    {
      id: 'YeirYkxHuQ', type: 'video_transcode', typeName: '视频转码',
      status: 'RUNNING', progress: 42, priority: 100,
      createdAt: new Date(Date.now() - 5 * 60_000).toISOString(),
      startedAt: new Date(Date.now() - 4 * 60_000).toISOString(),
      expiredAt: new Date(Date.now() + 55 * 60_000).toISOString(),
    },
    {
      id: 'Zkjs8dH2mQ', type: 'data_export', typeName: '数据导出',
      status: 'PENDING', progress: 0, priority: 50,
      createdAt: new Date(Date.now() - 60_000).toISOString(),
      expiredAt: new Date(Date.now() + 10 * 60_000).toISOString(),
    },
    {
      id: 'Ab3kLm9xPq', type: 'pdf_generate', typeName: 'PDF 生成',
      status: 'SUCCESS', progress: 100, priority: 100,
      createdAt: new Date(Date.now() - 3600_000).toISOString(),
      startedAt: new Date(Date.now() - 3590_000).toISOString(),
      finishedAt: new Date(Date.now() - 3500_000).toISOString(),
      result: { url: 'https://example.com/mock.pdf', pages: 12 },
    },
  ],
  total: 3, page: 1, pageSize: 20, totalPages: 1,
}

const MOCK_TASK_DETAIL = {
  id: 'YeirYkxHuQ', type: 'video_transcode', typeName: '视频转码',
  status: 'RUNNING', progress: 42, priority: 100, attempt: 1, maxAttempts: 3,
  currentStep: 'transcode',
  stepsDetail: [
    { key: 'fetch', name: '拉取源文件', status: 'finished', progress: 100, start_time: new Date(Date.now() - 240_000).toISOString(), end_time: new Date(Date.now() - 180_000).toISOString(), cost_ms: 60000 },
    { key: 'transcode', name: '转码', status: 'processing', progress: 42, start_time: new Date(Date.now() - 180_000).toISOString(), detail: '720p → H.264' },
    { key: 'upload', name: '上传产物', status: 'pending' },
  ],
  payload: { source: 'https://example.com/src.mp4', resolution: '720p', codec: 'h264' },
  workerIp: '10.0.4.21',
  callbackUrl: 'https://biz.example.com/callback',
  callbackStatus: 0,
  createdAt: new Date(Date.now() - 300_000).toISOString(),
  startedAt: new Date(Date.now() - 240_000).toISOString(),
  expiredAt: new Date(Date.now() - 300_000 + 3600_000).toISOString(),
}

const MOCK_STATS_OVERVIEW = {
  totalPending: 3,
  totalRunning: 2,
  todayStats: { success: 18, failed: 2, cancelled: 1 },
  workerCount: { online: 4, offline: 1 },
}

const MOCK_WORKERS = [
  { workerKey: 'worker-video-01', workerIp: '10.0.4.21', hostname: 'video-node-1', taskTypeKey: 'video_transcode', status: 'ONLINE', lastHeartbeatAt: new Date(Date.now() - 5_000).toISOString() },
  { workerKey: 'worker-video-02', workerIp: '10.0.4.22', hostname: 'video-node-2', taskTypeKey: 'video_transcode', status: 'BUSY', lastHeartbeatAt: new Date(Date.now() - 3_000).toISOString() },
  { workerKey: 'worker-data-01', workerIp: '10.0.5.11', hostname: 'data-node-1', taskTypeKey: 'data_export', status: 'ONLINE', lastHeartbeatAt: new Date(Date.now() - 8_000).toISOString() },
  { workerKey: 'worker-data-02', workerIp: '10.0.5.12', hostname: 'data-node-2', taskTypeKey: 'data_export', status: 'OFFLINE', lastHeartbeatAt: new Date(Date.now() - 3600_000).toISOString() },
]

const MOCK_TYPE_CONFIGS = [
  { id: 1, typeKey: 'video_transcode', name: '视频转码', concurrencyLimit: 5, timeoutSeconds: 3600, maxRetries: 3, isEnabled: true, stepsConfig: [{ key: 'fetch', name: '拉取源文件', weight: 10 }, { key: 'transcode', name: '转码', weight: 80 }, { key: 'upload', name: '上传产物', weight: 10 }], createdAt: '2026-01-01 00:00:00', updatedAt: '2026-01-01 00:00:00' },
  { id: 2, typeKey: 'data_export', name: '数据导出', concurrencyLimit: 10, timeoutSeconds: 1800, maxRetries: 2, isEnabled: true, stepsConfig: [], createdAt: '2026-01-01 00:00:00', updatedAt: '2026-01-01 00:00:00' },
  { id: 3, typeKey: 'pdf_generate', name: 'PDF 生成', concurrencyLimit: 3, timeoutSeconds: 600, maxRetries: 1, isEnabled: false, stepsConfig: [], createdAt: '2026-01-01 00:00:00', updatedAt: '2026-01-01 00:00:00' },
]

const MOCK_SYSTEM_CONFIG = {
  systemInfo: { appName: 'lotask4j', appVersion: '1.0.0-SNAPSHOT', springBootVersion: '3.5.16', javaVersion: '17', osName: 'Mac OS X', osArch: 'aarch64', startTime: new Date(Date.now() - 86400_000).toISOString(), uptime: '1d' },
  databaseConfig: { type: 'PostgreSQL', version: '16', url: 'jdbc:postgresql://localhost:5432/lotask4j', maxPoolSize: 20, activeConnections: 3 },
  redisConfig: { mode: 'single', host: 'localhost', database: 0, status: 'Connected' },
  asyncConfig: { corePoolSize: 10, maxPoolSize: 50, queueCapacity: 1000, threadNamePrefix: 'async-executor-', waitForTasksToCompleteOnShutdown: true, awaitTerminationSeconds: 60, activeCount: 2, poolSize: 10, queueSize: 5 },
  jvmInfo: { name: 'OpenJDK 64-Bit Server VM', version: '17.0.9', maxMemory: 4294967296, totalMemory: 1073741824, usedMemory: 536870912, freeMemory: 536870912, cpuCores: 10, threadCount: 87 },
  taskStats: { totalTasks: 1234, pendingTasks: 3, runningTasks: 2, successTasks: 1100, failedTasks: 100, cancelledTasks: 31, taskTypeCount: 3, onlineWorkerCount: 3 },
}

const MOCK_EMBED_CONFIGS = {
  total: 2, page: 1, pageSize: 20,
  items: [
    { id: 1, configKey: 'tenant-a-list', configName: '租户A任务列表', userId: 'tenant-a', isOpen: 0, callbackUrl: 'https://a.example.com/verify', componentType: 'task-list', isEnabled: 1, config: { pageSize: 10 }, createdAt: '2026-01-01 00:00:00' },
    { id: 2, configKey: 'tenant-b-card', configName: '租户B任务卡片', userId: 'guest', isOpen: 1, componentType: 'task-card', isEnabled: 1, config: {}, createdAt: '2026-01-02 00:00:00' },
  ],
}

interface MockConfig {
  url?: string
  method?: string
  data?: unknown
  params?: Record<string, unknown>
}

function pickMockResponse(config: MockConfig): unknown | undefined {
  const url = config.url || ''
  const method = (config.method || 'get').toLowerCase()

  // client_credentials 登录: 任意凭据发 mock token
  if (method === 'post' && url.endsWith('/api/v1/auth/token')) {
    return { access_token: MOCK_ACCESS, token_type: 'Bearer', expires_in: 7200 }
  }

  // 任务列表 / 详情 (client 域, 主应用与 embed 组件共用)
  if (method === 'get' && /\/api\/v1\/client\/tasks\/[^/]+$/.test(url)) return MOCK_TASK_DETAIL
  if (method === 'get' && url.endsWith('/api/v1/client/tasks')) return MOCK_TASK_LIST
  if (method === 'get' && url.endsWith('/api/v1/client/tasks/stats')) {
    return { total: 1234, pending: 3, running: 2, success: 1100, failed: 100, cancelled: 31 }
  }

  // admin 域
  if (method === 'get' && url.endsWith('/api/v1/admin/stats/overview')) return MOCK_STATS_OVERVIEW
  if (method === 'get' && url.endsWith('/api/v1/admin/workers')) return MOCK_WORKERS
  if (method === 'get' && url.endsWith('/api/v1/admin/types')) return MOCK_TYPE_CONFIGS
  if (method === 'get' && url.endsWith('/api/v1/admin/system/config')) return MOCK_SYSTEM_CONFIG

  // embed-config 域 (分页结构例外: items 而非 list)
  if (method === 'get' && url.endsWith('/configs')) return MOCK_EMBED_CONFIGS

  return undefined
}

/**
 * 在 axios response 拦截器最前短路命中. 让真实业务代码零改动.
 * mock 数据不带 code 字段, 业务拦截器按「无 envelope 直通」返回.
 */
export function installDevMock(instance: AxiosInstance): void {
  instance.interceptors.response.use((response) => {
    const mock = pickMockResponse(response.config as MockConfig)
    if (mock !== undefined) {
      return { ...response, data: mock, status: 200, statusText: 'OK' } as typeof response
    }
    return response
  })
}
