/**
 * 认证 API — client_credentials 换 access_token
 * 蓝本: benefit4j api/benefitAuth.ts (form-encoded)
 * 走统一 request client (dev mock 可拦截; envelope 由拦截器解包)
 */
import { http } from '@/api/request'

export interface TokenResult {
  access_token: string
  token_type: string
  expires_in: number
}

export async function loginApi(clientId: string, clientSecret: string): Promise<TokenResult> {
  const body = new URLSearchParams()
  body.set('grant_type', 'client_credentials')
  body.set('client_id', clientId)
  body.set('client_secret', clientSecret)

  // request 拦截器对 URLSearchParams 不覆盖 Content-Type, axios 自动加 x-www-form-urlencoded
  const result = await http.post<TokenResult>('/api/v1/auth/token', body)
  return result
}
