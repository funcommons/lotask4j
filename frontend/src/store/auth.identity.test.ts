/**
 * decodeTenantClaim 单测 — 双域路由守卫的身份判定基础。
 *
 * framework4j v1.7.0 (Issue #23) 签发侧把业务 claims 嵌套写入 JWT payload.claims:
 *   平台身份 = tenant_id 0, 租户身份 = 雪花 Long (数字或字符串, 框架两侧兼容)。
 * 平面 tenant_id 兼容 v1.5.1 前 dev-mock 历史形状; 不验签, 解析失败一律 null (守卫放行)。
 */
import { describe, expect, it } from 'vitest'
import { decodeTenantClaim } from './auth'

function makeJwt(payload: unknown): string {
  const b64u = (s: string) =>
    btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${b64u('{"alg":"HS256","typ":"JWT"}')}.${b64u(JSON.stringify(payload))}.sig`
}

describe('decodeTenantClaim', () => {
  it('嵌套 claims.tenant_id=0 → 0 (平台身份, v1.7.0 签发形状)', () => {
    expect(decodeTenantClaim(makeJwt({ sub: 'PLATFORM', claims: { tenant_id: 0 } }))).toBe(0)
  })

  it('嵌套 claims.tenant_id 数字雪花 → 原值 (租户身份)', () => {
    expect(decodeTenantClaim(makeJwt({ sub: 'TENANT', claims: { tenant_id: 9101 } }))).toBe(9101)
  })

  it('嵌套 claims.tenant_id 数字字符串 → 解析为数字 (框架数字/字符串兼容语义)', () => {
    expect(decodeTenantClaim(makeJwt({ claims: { tenant_id: '9101' } }))).toBe(9101)
  })

  it('雪花大 id (超 MAX_SAFE_INTEGER, JSON.parse 丢精度) → 非 null 且非 0 (身份判定只分 0/非 0)', () => {
    const id = decodeTenantClaim(makeJwt({ claims: { tenant_id: 2096084837229187123 } }))
    expect(id).not.toBeNull()
    expect(id).not.toBe(0)
  })

  it('平面 tenant_id 兼容 (历史 dev-mock 形状)', () => {
    expect(decodeTenantClaim(makeJwt({ tenant_id: 9101 }))).toBe(9101)
    expect(decodeTenantClaim(makeJwt({ tenant_id: 0 }))).toBe(0)
  })

  it('payload 无 claims / claim 缺失 → null (v1.5.1 存量 token)', () => {
    expect(decodeTenantClaim(makeJwt({ sub: 'TENANT' }))).toBeNull()
    expect(decodeTenantClaim(makeJwt({ claims: {} }))).toBeNull()
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
