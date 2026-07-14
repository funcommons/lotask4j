import axios from 'axios'

const http = axios.create({
  baseURL: '/api/v1/admin/embed-config',
  timeout: 10000
})

http.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.code !== undefined && data.code !== 0) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error) => {
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
  total: number | string
  page: number | string
  pageSize: number | string
  items: T[]
}

export function listConfigs(params: {
  keyword?: string
  isEnabled?: number
  page?: number
  pageSize?: number
} = {}) {
  return http.get<any, { data: PageResult<WebEmbedConfig> }>('/configs', { params })
}

export function getConfig(id: number) {
  return http.get<any, { data: WebEmbedConfig }>(`/configs/${id}`)
}

export function createConfig(data: WebEmbedConfig) {
  return http.post<any, { data: number }>('/configs', data)
}

export function updateConfig(id: number, data: WebEmbedConfig) {
  return http.put<any, { data: null }>(`/configs/${id}`, data)
}

export function deleteConfig(id: number) {
  return http.delete<any, { data: null }>(`/configs/${id}`)
}

export function toggleEnabled(id: number, isEnabled: number) {
  return http.post<any, { data: null }>(`/configs/${id}/toggle`, null, {
    params: { isEnabled }
  })
}

export function getPreviewUrl(id: number, componentType?: string, taskId?: string) {
  return http.get<any, { data: { url: string } }>(`/configs/${id}/preview-url`, {
    params: { componentType, taskId }
  })
}

export default http
