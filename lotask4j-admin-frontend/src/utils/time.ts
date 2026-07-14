/**
 * 时间处理工具 — GMT+8 时区固定。
 * 移植自原 React 前端 utils/timeUtils.ts，去掉 appConfig 依赖，内联时区常量。
 */

const TIMEZONE_OFFSET = 8 * 60 * 60 * 1000 // +08:00
const TIMEZONE_ISO = '+08:00'

export function parseTime(time?: string | number | Date | null): Date | null {
  if (!time) return null
  if (time instanceof Date) {
    return isNaN(time.getTime()) ? null : time
  }
  if (typeof time === 'number') {
    const date = new Date(time)
    return isNaN(date.getTime()) ? null : date
  }
  if (typeof time === 'string') {
    const ts = Number(time)
    if (!isNaN(ts) && ts > 0) {
      const d = new Date(ts)
      if (!isNaN(d.getTime())) return d
    }
    if (/^\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}$/.test(time)) {
      const d = new Date(time.replace(' ', 'T') + TIMEZONE_ISO)
      return isNaN(d.getTime()) ? null : d
    }
    if (/^\d{4}-\d{2}-\d{2}$/.test(time)) {
      const d = new Date(time + 'T00:00:00' + TIMEZONE_ISO)
      return isNaN(d.getTime()) ? null : d
    }
    const d = new Date(time)
    return isNaN(d.getTime()) ? null : d
  }
  return null
}

export function formatTime(time?: string | number | Date | null): string {
  const date = parseTime(time)
  if (!date) return '-'
  const gmt8 = new Date(date.getTime() + TIMEZONE_OFFSET - date.getTimezoneOffset() * 60 * 1000)
  const y = gmt8.getFullYear()
  const mo = String(gmt8.getMonth() + 1).padStart(2, '0')
  const d = String(gmt8.getDate()).padStart(2, '0')
  const h = String(gmt8.getHours()).padStart(2, '0')
  const mi = String(gmt8.getMinutes()).padStart(2, '0')
  const s = String(gmt8.getSeconds()).padStart(2, '0')
  return `${y}-${mo}-${d} ${h}:${mi}:${s}`
}

export function formatDate(time?: string | number | Date | null): string {
  const f = formatTime(time)
  return f === '-' ? '-' : f.split(' ')[0]
}

export function toGMT8ISO(time?: string | number | Date | null): string | null {
  const date = parseTime(time)
  if (!date) return null
  const gmt8 = new Date(date.getTime() + TIMEZONE_OFFSET)
  const y = gmt8.getUTCFullYear()
  const mo = String(gmt8.getUTCMonth() + 1).padStart(2, '0')
  const d = String(gmt8.getUTCDate()).padStart(2, '0')
  const h = String(gmt8.getUTCHours()).padStart(2, '0')
  const mi = String(gmt8.getUTCMinutes()).padStart(2, '0')
  const s = String(gmt8.getUTCSeconds()).padStart(2, '0')
  return `${y}-${mo}-${d}T${h}:${mi}:${s}${TIMEZONE_ISO}`
}

export function nowGMT8(): Date {
  return new Date(Date.now() + TIMEZONE_OFFSET)
}

export function timeDiff(
  t1?: string | number | Date | null,
  t2?: string | number | Date | null
): number | null {
  const a = parseTime(t1)
  const b = parseTime(t2)
  if (!a || !b) return null
  return Math.abs(a.getTime() - b.getTime())
}

export function formatDuration(milliseconds?: number | null): string {
  if (milliseconds === null || milliseconds === undefined || milliseconds < 0) return '-'
  const seconds = Math.floor(milliseconds / 1000)
  if (seconds < 60) return `${seconds}秒`
  if (seconds < 3600) {
    const m = Math.floor(seconds / 60)
    const s = seconds % 60
    return s > 0 ? `${m}分${s}秒` : `${m}分钟`
  }
  if (seconds < 86400) {
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    return m > 0 ? `${h}小时${m}分` : `${h}小时`
  }
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  return h > 0 ? `${d}天${h}小时` : `${d}天`
}

export function isExpired(time?: string | number | Date | null): boolean {
  const d = parseTime(time)
  if (!d) return false
  return d.getTime() < Date.now()
}

export function getRelativeTime(time?: string | number | Date | null): string {
  const d = parseTime(time)
  if (!d) return '-'
  const diff = Date.now() - d.getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}分钟前`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}小时前`
  if (seconds < 2592000) return `${Math.floor(seconds / 86400)}天前`
  return formatDate(time)
}
