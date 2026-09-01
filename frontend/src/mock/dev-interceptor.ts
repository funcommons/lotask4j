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
      createdAt: '2026-08-20T10:00:00+08:00',
      startedAt: '2026-08-20T10:01:00+08:00',
      expiredAt: '2026-08-20T11:00:00+08:00',
    },
    {
      id: 'Zkjs8dH2mQ', type: 'data_export', typeName: '数据导出',
      status: 'PENDING', progress: 0, priority: 50,
      createdAt: '2026-08-20T09:59:00+08:00',
      expiredAt: '2026-08-20T10:10:00+08:00',
    },
    {
      id: 'FailedAb9xQq', type: 'pdf_generate', typeName: 'PDF 生成',
      status: 'FAILED', progress: 30, priority: 100,
      errorMsg: 'mock: 渲染引擎超时 (PDF_RENDER_TIMEOUT)',
      createdAt: '2026-08-20T09:00:00+08:00',
      startedAt: '2026-08-20T09:00:30+08:00',
      finishedAt: '2026-08-20T09:02:00+08:00',
      lastErrorCode: 'PDF_RENDER_TIMEOUT',
    },
    {
      id: 'Ab3kLm9xPq', type: 'pdf_generate', typeName: 'PDF 生成',
      status: 'SUCCESS', progress: 100, priority: 100,
      createdAt: '2026-08-20T08:00:00+08:00',
      startedAt: '2026-08-20T08:00:10+08:00',
      finishedAt: '2026-08-20T08:02:00+08:00',
      result: { url: 'https://example.com/mock.pdf', pages: 12 },
    },
  ],
  total: 4, page: 1, pageSize: 20, totalPages: 1,
}

const MOCK_TASK_DETAIL = {
  id: 'YeirYkxHuQ', type: 'video_transcode', typeName: '视频转码',
  status: 'RUNNING', progress: 42, priority: 100, attempt: 1, maxAttempts: 3,
  currentStep: 'transcode',
  stepsDetail: [
    { key: 'fetch', name: '拉取源文件', status: 'finished', progress: 100, start_time: '2026-08-20T10:01:10+08:00', end_time: '2026-08-20T10:02:10+08:00', cost_ms: 60000 },
    { key: 'transcode', name: '转码', status: 'processing', progress: 42, start_time: '2026-08-20T10:02:10+08:00', detail: '720p → H.264' },
    { key: 'upload', name: '上传产物', status: 'pending' },
  ],
  payload: { source: 'https://example.com/src.mp4', resolution: '720p', codec: 'h264' },
  workerIp: '10.0.4.21',
  callbackUrl: 'https://biz.example.com/callback',
  callbackStatus: 0,
  createdAt: '2026-08-20T10:00:00+08:00',
  startedAt: '2026-08-20T10:01:00+08:00',
  expiredAt: '2026-08-20T11:00:00+08:00',
}

const MOCK_FAILED_DETAIL = {
  id: 'FailedAb9xQq', type: 'pdf_generate', typeName: 'PDF 生成',
  status: 'FAILED', progress: 30, priority: 100, attempt: 2, maxAttempts: 3,
  errorMsg: 'mock: 渲染引擎超时 (PDF_RENDER_TIMEOUT)',
  lastErrorCode: 'PDF_RENDER_TIMEOUT',
  lastErrorMessage: 'render engine timeout',
  stepsDetail: [
    { key: 'render', name: '渲染', status: 'failed', progress: 30, start_time: '2026-08-20T09:00:30+08:00', detail: 'page 3/10' },
  ],
  payload: { template: 'invoice', pages: 10 },
  workerIp: '10.0.5.11',
  createdAt: '2026-08-20T09:00:00+08:00',
  startedAt: '2026-08-20T09:00:30+08:00',
  finishedAt: '2026-08-20T09:02:00+08:00',
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

  // client_credentials 登录: 'bad-secret' 走失败分支 (error-states 用例), 其余发 mock token
  if (method === 'post' && url.endsWith('/api/v1/auth/token')) {
    const body = String(config.data ?? '')
    if (body.includes('client_secret=bad-secret')) {
      return { code: 20105, message: 'client_id 或 client_secret 无效', data: null }
    }
    return { access_token: MOCK_ACCESS, token_type: 'Bearer', expires_in: 7200 }
  }

  // 任务详情: 特例 ID → 404 业务码 / FAILED 详情 (error-states 用例); 其余返回 RUNNING 详情
  if (method === 'get' && /\/api\/v1\/client\/tasks\/[^/?]+$/.test(url)) {
    const id = url.split('/').pop()
    if (id === 'NotFound404') {
      return { code: 20100, message: '任务不存在: NotFound404', data: null }
    }
    if (id === 'FailedAb9xQq') return MOCK_FAILED_DETAIL
    return MOCK_TASK_DETAIL
  }
  // 任务列表 / 统计 (client 域, 主应用与 embed 组件共用)
  if (method === 'get' && url.endsWith('/api/v1/client/tasks')) return MOCK_TASK_LIST
  if (method === 'get' && url.endsWith('/api/v1/client/tasks/stats')) {
    return { total: 1234, pending: 3, running: 2, success: 1100, failed: 100, cancelled: 31 }
  }

  // 提交 / 取消 (写路径, mock 固定 ID; 列表页提交走 admin 代提交)
  if (method === 'post' && url.endsWith('/api/v1/client/tasks/submit')) {
    return { id: 'MockNewTask01' }
  }
  if (method === 'post' && url.endsWith('/api/v1/admin/tasks/submit')) {
    return { id: 'MockNewTask01' }
  }
  if (method === 'post' && /\/api\/v1\/client\/tasks\/[^/]+\/cancel$/.test(url)) {
    return null
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
 * axios adapter 层短路 — 命中 mock 的请求完全不走网络 (无后端也能跑 UI).
 * (benefit4j 原版挂 response 拦截器, 依赖后端真实返回 200 后替换 body;
 *  lotask4j dev 环境常无后端, 网络层直接失败 response 拦截器不会触发,
 *  故改为 adapter: 命中即合成 response, 未命中回落原 adapter。)
 * mock 数据不带 code 字段, 业务拦截器按「无 envelope 直通」返回。
 *
 * e2e 支持: 命中的调用记录到 window.__devMockLog (最近 50 条,
 * {url, method, params}), 供 playwright 断言 —— adapter 短路后无真实网络请求,
 * page.waitForRequest 对 mock 端点永不触发, 必须走 log。
 */
declare global {
  interface Window {
    __devMockLog?: Array<{ url: string; method: string; params?: Record<string, unknown> }>
  }
}

function recordMockCall(config: MockConfig): void {
  if (typeof window === 'undefined') return
  window.__devMockLog = window.__devMockLog ?? []
  window.__devMockLog.push({
    url: config.url || '',
    method: (config.method || 'get').toLowerCase(),
    params: (config.params ?? undefined) as Record<string, unknown> | undefined,
  })
  if (window.__devMockLog.length > 50) window.__devMockLog.shift()
}

export function installDevMock(instance: AxiosInstance): void {
  const originalAdapter = instance.defaults.adapter
  instance.defaults.adapter = async (config) => {
    const mock = pickMockResponse(config as MockConfig)
    if (mock !== undefined) {
      recordMockCall(config as MockConfig)
      return {
        data: mock,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }
    if (typeof originalAdapter === 'function') {
      return originalAdapter(config)
    }
    throw new Error(`[dev-mock] no adapter available for ${config.url}`)
  }
}
