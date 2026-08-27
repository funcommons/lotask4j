/**
 * Web Embed 配置 API — /api/v1/admin/embed-config/** (AdminWebEmbedController)
 * 移植自 lotask4j-admin-frontend/src/api/embed.ts
 * 注意: 列表分页结构例外 — { items, total, page, pageSize } (非 list)
 */
import { http } from '@/api/request'
import type { EmbedComponentType, EmbedConfigPage, WebEmbedConfig } from '@/api/types'

export interface EmbedConfigQuery {
  keyword?: string
  isEnabled?: number
  page?: number
  pageSize?: number
}

export function listConfigs(params: EmbedConfigQuery): Promise<EmbedConfigPage> {
  return http.get('/api/v1/admin/embed-config/configs', { params })
}

export function getConfig(id: number): Promise<WebEmbedConfig> {
  return http.get(`/api/v1/admin/embed-config/configs/${id}`)
}

export function createConfig(data: WebEmbedConfig): Promise<number> {
  return http.post('/api/v1/admin/embed-config/configs', data)
}

export function updateConfig(id: number, data: WebEmbedConfig): Promise<null> {
  return http.put(`/api/v1/admin/embed-config/configs/${id}`, data)
}

export function deleteConfig(id: number): Promise<null> {
  return http.delete(`/api/v1/admin/embed-config/configs/${id}`)
}

export function toggleEnabled(id: number, isEnabled: boolean): Promise<null> {
  return http.post(`/api/v1/admin/embed-config/configs/${id}/toggle`, null, {
    params: { isEnabled },
  })
}

/** 预览 URL (iframe 直接打开) */
export function getPreviewUrl(id: number, componentType: EmbedComponentType, taskId?: string): Promise<{ url: string }> {
  return http.get(`/api/v1/admin/embed-config/configs/${id}/preview-url`, {
    params: { componentType, taskId },
  })
}
