/**
 * Client 域 API — /api/v1/client/tasks/** (ClientTaskController)
 * 移植自 lotask4j-admin-frontend/src/api/client.ts, 换用统一 http client (envelope 已解包)
 */
import { http } from '@/api/request'
import type { LotaskPage, SubmitTaskPayload, TaskDetail, TaskListItem } from '@/api/types'

export interface TaskListQuery {
  page?: number
  pageSize?: number
  status?: string
  taskType?: string
  id?: string
  isArchived?: number
  createdAtStart?: string
  createdAtEnd?: string
}

/** 任务列表 (当前 / 归档由 isArchived 区分) */
export function getTaskList(params: TaskListQuery): Promise<LotaskPage<TaskListItem>> {
  return http.get('/api/v1/client/tasks', { params })
}

/** 任务详情 (id 为 OpenID 混淆字符串) */
export function getTaskDetail(taskId: string): Promise<TaskDetail> {
  return http.get(`/api/v1/client/tasks/${taskId}`)
}

/** 提交任务 */
export function submitTask(data: SubmitTaskPayload): Promise<{ id: string }> {
  return http.post('/api/v1/client/tasks', data)
}

/** 取消任务 (仅 PENDING / RUNNING) */
export function cancelTask(taskId: string): Promise<null> {
  return http.post(`/api/v1/client/tasks/${taskId}/cancel`)
}

/** 任务统计 (client 视角) */
export function getTaskStats(): Promise<Record<string, number>> {
  return http.get('/api/v1/client/tasks/stats')
}
