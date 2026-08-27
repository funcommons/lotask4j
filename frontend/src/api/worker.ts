/**
 * Worker 域 API — /api/v1/worker/** (WorkerTaskController)
 * 给 DemoSimulator 用: 注册 / 拉取 / 进度 / 结果 四个接口
 * 类型按后端 DTO 字段显式定义, 暂不抽到 api/types.ts (避免污染共享类型空间)
 */
import { http } from '@/api/request'

/** Worker 注册请求 */
export interface WorkerRegisterRequest {
  workerKey: string
  taskTypeKey: string
  ip: string
  hostname?: string
}

/** Worker 注册响应 */
export interface WorkerRegisterResponse {
  workerKey: string
  taskTypeKey: string
  status: 'ONLINE'
  registeredAt: string
}

/** Worker poll 请求 */
export interface PollTaskRequest {
  taskType: string
  workerId: string
  strategy?: 'PRIORITY' | 'FIFO'
}

/** Worker poll 响应 (有任务时) */
export interface PollTaskResponse {
  id: string
  type: string
  payload: Record<string, unknown>
  priority: number
  executionToken: string
  version: number
  attempt: number
  leaseExpireAt: string
}

/** Worker 上报进度请求 */
export interface ReportProgressRequest {
  currentStepKey: string
  stepProgress: number
  executionToken: string
  version: number
}

/** Worker 上报结果请求 */
export interface ReportResultRequest {
  status: 'SUCCESS' | 'FAILED' | 'CANCELLED'
  result?: Record<string, unknown>
  errorMsg?: string
  executionToken: string
  version: number
}

/** 注册 worker (拿到 workerKey) */
export function registerWorker(data: WorkerRegisterRequest): Promise<WorkerRegisterResponse> {
  return http.post('/api/v1/worker/register', data)
}

/** 拉取一个待执行任务 (无任务返回 null/空对象) */
export function pollTask(data: PollTaskRequest): Promise<PollTaskResponse | null> {
  return http.post('/api/v1/worker/tasks/poll', data)
}

/** 上报执行进度 */
export function reportProgress(taskId: string, data: ReportProgressRequest): Promise<null> {
  return http.post(`/api/v1/worker/tasks/${taskId}/progress`, data)
}

/** 上报最终结果 */
export function reportResult(taskId: string, data: ReportResultRequest): Promise<null> {
  return http.post(`/api/v1/worker/tasks/${taskId}/result`, data)
}