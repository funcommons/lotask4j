/**
 * 任务状态/时间展示工具 — 移植自 lotask4j-admin-frontend 各 view 的散落逻辑, 统一收口
 */
import type { StepStatus, TaskStatus } from '@/api/types'

/** 任务状态 → el-tag / FcTag 类型 */
export const TASK_STATUS_TAG_TYPE: Record<TaskStatus, 'info' | 'warning' | 'success' | 'danger' | 'primary'> = {
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELLING: 'warning',
  CANCELLED: 'info',
}

/** 任务状态 → 文案 key (lotask.status.*) */
export const TASK_STATUS_LABEL_KEY: Record<TaskStatus, string> = {
  PENDING: 'lotask.status.pending',
  RUNNING: 'lotask.status.running',
  SUCCESS: 'lotask.status.success',
  FAILED: 'lotask.status.failed',
  CANCELLING: 'lotask.status.cancelling',
  CANCELLED: 'lotask.status.cancelled',
}

/** 步骤状态 → tag 类型 */
export const STEP_STATUS_TAG_TYPE: Record<StepStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  pending: 'info',
  processing: 'warning',
  finished: 'success',
  failed: 'danger',
}

/** 回调状态: 0 未回调 / 1 成功 / 2 失败 */
export const CALLBACK_STATUS_TEXT: Record<number, string> = {
  0: 'lotask.callback.not-called',
  1: 'lotask.callback.success',
  2: 'lotask.callback.failed',
}

export function isTerminal(status: TaskStatus): boolean {
  return status === 'SUCCESS' || status === 'FAILED' || status === 'CANCELLED'
}

export function isCancellable(status: TaskStatus): boolean {
  return status === 'PENDING' || status === 'RUNNING'
}

// —— 时间格式化 ——

/** ISO → 'YYYY-MM-DD HH:mm:ss' (本地时区) */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '-'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 时长 (ms) → '1h 23m' / '5m 30s' / '45s' 自适应 */
export function formatDuration(ms: number): string {
  if (!Number.isFinite(ms) || ms < 0) return '-'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ${s % 60}s`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ${m % 60}m`
  const d = Math.floor(h / 24)
  return `${d}d ${h % 24}h`
}

/** 距 ISO 时间点的剩余时长文案; 已过期为负 */
export function remainingLabel(iso?: string | null): { text: string; expired: boolean; urgent: boolean } {
  if (!iso) return { text: '-', expired: false, urgent: false }
  const diff = new Date(iso).getTime() - Date.now()
  if (diff <= 0) return { text: formatDuration(0), expired: true, urgent: true }
  return { text: formatDuration(diff), expired: false, urgent: diff < 10 * 60 * 1000 }
}

/** 相对时间 (心跳 '5s 前' / '3m 前') */
export function relativeTime(iso?: string | null): string {
  if (!iso) return '-'
  const diff = Date.now() - new Date(iso).getTime()
  if (diff < 0) return formatDateTime(iso)
  if (diff < 60_000) return `${Math.floor(diff / 1000)}s 前`
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m 前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h 前`
  return `${Math.floor(diff / 86_400_000)}d 前`
}

/** 三段时长: 等待 (created→started) / 执行 (started→finished|now) / 总计 */
export function taskDurations(task: { createdAt: string; startedAt?: string; finishedAt?: string }) {
  const created = new Date(task.createdAt).getTime()
  const started = task.startedAt ? new Date(task.startedAt).getTime() : null
  const end = task.finishedAt ? new Date(task.finishedAt).getTime() : Date.now()
  return {
    waiting: started !== null ? started - created : Date.now() - created,
    executing: started !== null ? end - started : 0,
    total: end - created,
  }
}
