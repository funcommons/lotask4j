/**
 * decodeTenantClaim 单测 — 双域路由守卫的身份判定基础。
 *
 * framework4j-tenant 签发的 JWT payload 带 tenant_id claim:
 *   平台身份 = 0, 租户身份 = 雪花 Long (数字或字符串, 框架两侧兼容)。
 * 前端不验签, 只解 payload — 解析失败一律返回 null (守卫放行, 后端兜底)。
 */
import { describe, expect, it } from 'vitest'
import { decodeTenantClaim } from './auth'

function makeJwt(payload: unknown): string {
  const b64u = (s: string) =>
    btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${b64u('{"alg":"HS256","typ":"JWT"}')}.${b64u(JSON.stringify(payload))}.sig`
}

describe('decodeTenantClaim', () => {
  it('tenant_id=0 → 0 (平台身份)', () => {
    expect(decodeTenantClaim(makeJwt({ sub: 'PLATFORM', tenant_id: 0 }))).toBe(0)
  })

  it('tenant_id 数字雪花 → 原值 (租户身份)', () => {
    expect(decodeTenantClaim(makeJwt({ tenant_id: 9101 }))).toBe(9101)
  })

  it('tenant_id 数字字符串 → 解析为数字 (框架数字/字符串兼容语义)', () => {
    expect(decodeTenantClaim(makeJwt({ tenant_id: '9101' }))).toBe(9101)
  })

  it('payload 缺 tenant_id claim → null', () => {
    expect(decodeTenantClaim(makeJwt({ sub: 'TENANT' }))).toBeNull()
  })

  it('非 JWT (无三段) → null; 空值 → null', () => {
    expect(decodeTenantClaim('mock-access-token')).toBeNull()
    expect(decodeTenantClaim('')).toBeNull()
    expect(decodeTenantClaim(null)).toBeNull()
    expect(decodeTenantClaim(undefined)).toBeNull()
  })

  it('payload 非 base64/非 JSON → null (不抛异常)', () => {
    expect(decodeTenantClaim('aa.!!!!.sig')).toBeNull()
    expect(decodeTenantClaim('aa.eyJ4Ijp9.sig')).toBeNull() // {"x":} 非法 JSON
  })
})
