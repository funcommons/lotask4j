/**
 * 接入应用管理 API — /api/v1/admin/applications (AdminApplicationController)
 *
 * secret 仅在创建 / reset-secret 响应中出现一次 (明文), 列表不含。
 */
import { http } from '@/api/request'

export interface ApplicationItem {
  id: number
  name: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  createdAt?: string
  updatedAt?: string
}

/** 创建 / reset-secret 的一次性凭据响应 */
export interface ApplicationSecret {
  id: number
  name: string
  appSecret: string
}

export interface ApplicationPage {
  items: ApplicationItem[]
  total: number
  page: number
  pageSize: number
}

export interface ApplicationQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export function listApplications(params: ApplicationQuery): Promise<ApplicationPage> {
  return http.get('/api/v1/admin/applications', { params })
}

export function createApplication(data: { name: string; description?: string }): Promise<ApplicationSecret> {
  return http.post('/api/v1/admin/applications', data)
}

export function resetApplicationSecret(id: number): Promise<ApplicationSecret> {
  return http.post(`/api/v1/admin/applications/${id}/reset-secret`)
}

export function setApplicationStatus(id: number, status: 'ACTIVE' | 'INACTIVE'): Promise<null> {
  return http.post(`/api/v1/admin/applications/${id}/status`, { status })
}

export function deleteApplication(id: number): Promise<null> {
  return http.delete(`/api/v1/admin/applications/${id}`)
}
