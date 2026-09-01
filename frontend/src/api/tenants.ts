/**
 * 租户管理 API (租户即接入方) — /api/v1/admin/tenants (AdminApplicationController)
 *
 * secret 仅在创建 / reset-secret 响应中出现一次 (明文), 列表不含。
 */
import { http } from '@/api/request'

export interface TenantItem {
  id: number
  name: string
  description?: string
  status: 'ACTIVE' | 'SUSPEND'
  createdAt?: string
  updatedAt?: string
}

/** 创建 / reset-secret 的一次性凭据响应 */
export interface TenantSecret {
  id: number
  name: string
  tenantSecret: string
}

export interface TenantPage {
  items: TenantItem[]
  total: number
  page: number
  pageSize: number
}

export interface TenantQuery {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}

export function listTenants(params: TenantQuery): Promise<TenantPage> {
  return http.get('/api/v1/admin/tenants', { params })
}

export function createTenant(data: { name: string; description?: string }): Promise<TenantSecret> {
  return http.post('/api/v1/admin/tenants', data)
}

export function resetTenantSecret(id: number): Promise<TenantSecret> {
  return http.post(`/api/v1/admin/tenants/${id}/reset-secret`)
}

export function setTenantStatus(id: number, status: 'ACTIVE' | 'SUSPEND'): Promise<null> {
  return http.post(`/api/v1/admin/tenants/${id}/status`, { status })
}

export function deleteTenant(id: number): Promise<null> {
  return http.delete(`/api/v1/admin/tenants/${id}`)
}
