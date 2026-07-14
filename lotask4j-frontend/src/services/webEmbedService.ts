import axios from 'axios'
import type { AxiosInstance } from 'axios'

const api: AxiosInstance = axios.create({
  baseURL: '/api/v1/admin/embed-config',
  timeout: 10000
})

api.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.code !== undefined && data.code !== 0) {
      throw new Error(data.message || '请求失败')
    }
    return data
  },
  (error) => {
    console.error('Web Embed API 请求失败:', error)
    return Promise.reject(error)
  }
)

export interface WebEmbedConfig {
  id?: number
  configKey: string
  configName: string
  userId: string
  isOpen: number
  callbackUrl?: string
  config?: Record<string, any>
  componentType: string
  allowedDomains?: string
  isEnabled?: number
  createdAt?: string
  updatedAt?: string
  embedUrl?: string
}

export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  items: T[]
}

/** 分页查询 */
export function listConfigs(params: { keyword?: string; isEnabled?: number; page?: number; pageSize?: number }) {
  return api.get<any, { data: PageResult<WebEmbedConfig> }>('/configs', { params })
}

/** 获取单个 */
export function getConfig(id: number) {
  return api.get<any, { data: WebEmbedConfig }>(`/configs/${id}`)
}

/** 创建 */
export function createConfig(data: WebEmbedConfig) {
  return api.post<any, { data: number }>('/configs', data)
}

/** 更新 */
export function updateConfig(id: number, data: WebEmbedConfig) {
  return api.put<any, { data: null }>(`/configs/${id}`, data)
}

/** 删除 */
export function deleteConfig(id: number) {
  return api.delete<any, { data: null }>(`/configs/${id}`)
}

/** 启用/禁用 */
export function toggleEnabled(id: number, isEnabled: number) {
  return api.post<any, { data: null }>(`/configs/${id}/toggle`, null, {
    params: { isEnabled }
  })
}

/** 生成预览 URL */
export function previewUrl(id: number, componentType: string, taskId?: string) {
  return api.get<any, { data: { url: string } }>(`/configs/${id}/preview-url`, {
    params: { componentType, taskId }
  })
}

export default api
