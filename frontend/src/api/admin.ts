/**
 * Admin 域 API — /api/v1/admin/** (AdminTaskController)
 * 移植自 lotask4j-admin-frontend/src/api/admin.ts, 换用统一 http client
 */
import { http } from '@/api/request'
import type { StatsOverview, SystemConfig, TaskTypeConfig, WorkerNode } from '@/api/types'
import type { SubmitTaskPayload } from '@/api/types'

/** 在线 Worker 节点列表 */
export function getOnlineWorkers(): Promise<WorkerNode[]> {
  return http.get('/api/v1/admin/workers')
}

/** 统计概览 (Dashboard KPI) */
export function getStatsOverview(): Promise<StatsOverview> {
  return http.get('/api/v1/admin/stats/overview')
}

/** 全部任务类型配置 */
export function getAllTaskTypeConfigs(): Promise<TaskTypeConfig[]> {
  return http.get('/api/v1/admin/types')
}

/** 单个任务类型配置 */
export function getTaskTypeConfig(typeKey: string): Promise<TaskTypeConfig> {
  return http.get(`/api/v1/admin/types/${typeKey}`)
}

/** 保存任务类型配置 (新建/更新) */
export function saveTaskTypeConfig(data: TaskTypeConfig): Promise<null> {
  return http.post('/api/v1/admin/types', data)
}

/** 删除任务类型配置 */
export function deleteTaskTypeConfig(typeKey: string): Promise<null> {
  return http.delete(`/api/v1/admin/types/${typeKey}`)
}

/** Admin 代提交任务 (默认 priority 100) */
export function adminSubmitTask(data: SubmitTaskPayload): Promise<{ id: string }> {
  return http.post('/api/v1/admin/tasks/submit', data)
}

/** 系统配置 (系统/线程池/DB/Redis/JVM/任务统计) */
export function getSystemConfig(): Promise<SystemConfig> {
  return http.get('/api/v1/admin/system/config')
}

/** 任务事件时间线 (asts_task_execution_event) */
export function getTaskEvents(taskId: string, limit = 50): Promise<unknown[]> {
  return http.get(`/api/v1/admin/tasks/${taskId}/events`, { params: { limit } })
}
