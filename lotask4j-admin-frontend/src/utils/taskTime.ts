/**
 * 任务时间工具 — 移植自原 React 前端 utils/taskTimeUtils.ts。
 */
import { parseTime, formatTime, formatDuration, isExpired } from './time'

export interface TaskLike {
  createdAt: string
  status: string
  timeoutSeconds?: number
}

export function calculateExpiredAt(task: TaskLike): string | null {
  if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(task.status)) return null
  const created = parseTime(task.createdAt)
  if (!created) return null
  const timeoutMs = (task.timeoutSeconds || 24 * 60 * 60) * 1000
  return new Date(created.getTime() + timeoutMs).toISOString()
}

export interface ExpiredInfo {
  text: string
  isExpiring: boolean
  isExpired: boolean
}

export function formatExpiredTime(expiredAt?: string): ExpiredInfo {
  if (!expiredAt) return { text: '-', isExpiring: false, isExpired: false }
  const expire = new Date(expiredAt).getTime()
  const remaining = Math.floor((expire - Date.now()) / 1000)
  if (remaining <= 0) return { text: '已过期', isExpiring: false, isExpired: true }
  const isExpiring = remaining < 600
  if (remaining < 60) return { text: `${remaining}秒`, isExpiring, isExpired: false }
  if (remaining < 3600) return { text: `${Math.floor(remaining / 60)}分钟`, isExpiring, isExpired: false }
  if (remaining < 86400) {
    const h = Math.floor(remaining / 3600)
    const m = Math.floor((remaining % 3600) / 60)
    return { text: `${h}小时${m}分`, isExpiring, isExpired: false }
  }
  const d = Math.floor(remaining / 86400)
  const h = Math.floor((remaining % 86400) / 3600)
  return { text: `${d}天${h}小时`, isExpiring, isExpired: false }
}

export function formatExpiredAtDisplay(expiredAt?: string): string {
  return formatTime(expiredAt)
}

export interface DurationTask {
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export function calculateTaskDuration(task: DurationTask): string {
  const created = parseTime(task.createdAt)
  if (!created) return '-'
  if (task.finishedAt) {
    const finished = parseTime(task.finishedAt)
    if (finished) return formatDuration(finished.getTime() - created.getTime())
  }
  if (task.startedAt) {
    const started = parseTime(task.startedAt)
    if (started) return formatDuration(started.getTime() - created.getTime())
  }
  return '-'
}

export function isTaskExpired(expiredAt?: string): boolean {
  if (!expiredAt) return false
  return isExpired(expiredAt)
}
