/**
 * 认证 store — client_credentials 模式
 *
 * 后端 POST /api/v1/auth/token (form-encoded) 签发 JWT access_token;
 * 无 refresh token, 续期由后端 auto-renew 策略滑动完成。
 *
 * localStorage 持久化 (lotask4j: 前缀):
 *   - lotask4j:access_token
 *   - lotask4j:app_id       (client_id, 展示用)
 *   - lotask4j:expires_at   (ms 时间戳)
 *   - lotask4j:tenant_id    (身份锚点, /auth/me 回显; 0=平台, >0=租户)
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginApi } from '@/api/auth'
import { http } from '@/api/request'

export const AUTH_STORAGE_KEYS = {
  token: 'lotask4j:access_token',
  appId: 'lotask4j:app_id',
  expiresAt: 'lotask4j:expires_at',
  tenantId: 'lotask4j:tenant_id',
} as const

function readStorage(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}

/**
 * 解 JWT payload 的 tenant_id claim (数字或字符串)。
 * 注意: framework4j-tenant v1.5.1 签发的真实 JWT payload 不含 tenant_id claim
 * (身份存 Redis 会话侧) — 此函数仅服务 dev-mock 自造 token 与测试;
 * 真实身份以 GET /api/v1/auth/me 反查为准 (见 ensureIdentity)。
 */
export function decodeTenantClaim(jwt: string | null | undefined): number | null {
  if (!jwt) return null
  const parts = jwt.split('.')
  if (parts.length !== 3 || !parts[1]) return null
  try {
    let b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    while (b64.length % 4 !== 0) b64 += '='
    const payload = JSON.parse(atob(b64)) as { tenant_id?: number | string }
    const raw = payload?.tenant_id
    if (raw === undefined || raw === null) return null
    const n = typeof raw === 'number' ? raw : Number(raw)
    return Number.isSafeInteger(n) ? n : null
  } catch {
    return null
  }
}

/** 登录身份: 'platform' (tenant_id=0) | 'tenant' (tenant_id>0) | null (未登录/未知) */
export type AuthIdentity = 'platform' | 'tenant'

export const useAuthStore = defineStore('auth', () => {
  // —— state ——
  const token = ref<string | null>(readStorage(AUTH_STORAGE_KEYS.token))
  const appId = ref<string | null>(readStorage(AUTH_STORAGE_KEYS.appId))
  // client_secret — 仅供前端 HMAC 请求签名 (submit/cancel 强制签名端点)。
  // sessionStorage: 存活于整页刷新/SPA 会话, 关标签页即清 (不落 localStorage, 不跨会话)。
  // 与已持久化的 access_token 同一暴露面 (内网运营控制台), XSS 场景下两者等价。
  const RUNTIME_SECRET_KEY = 'lotask4j:runtime_secret'
  const readRuntimeSecret = (): string | null => {
    try { return sessionStorage.getItem(RUNTIME_SECRET_KEY) } catch { return null }
  }
  const runtimeSecret = ref<string | null>(readRuntimeSecret())
  const expiresAt = ref<number>(Number(readStorage(AUTH_STORAGE_KEYS.expiresAt) || 0))
  // 登录身份的数值锚点 (GET /api/v1/auth/me 回显): 0=平台, >0=租户, null=未知
  const tenantIdValue = ref<number | null>(
    readStorage(AUTH_STORAGE_KEYS.tenantId) !== null
      ? Number(readStorage(AUTH_STORAGE_KEYS.tenantId))
      : null)
  let identityPromise: Promise<void> | null = null

  // —— getters ——
  const isLoggedIn = computed(() => !!token.value && Date.now() < expiresAt.value)
  /** 5 分钟内过期 (UI 提示用; 无 refresh, 由后端 auto-renew 滑动续期) */
  const isExpiringSoon = computed(() =>
    !!token.value && Date.now() > expiresAt.value - 5 * 60 * 1000)
  /** 登录身份域 (控制台双域路由的判定依据): 0 → 平台, >0 → 租户, null → 未知放行 */
  const identity = computed<AuthIdentity | null>(() => {
    if (!token.value || tenantIdValue.value === null) return null
    return tenantIdValue.value === 0 ? 'platform' : 'tenant'
  })

  // —— actions ——
  function setToken(newToken: string, expiresIn?: number) {
    token.value = newToken
    const exp = expiresIn ? Date.now() + expiresIn * 1000 : 0
    expiresAt.value = exp
    try {
      localStorage.setItem(AUTH_STORAGE_KEYS.token, newToken)
      localStorage.setItem(AUTH_STORAGE_KEYS.expiresAt, String(exp))
    } catch { /* noop */ }
  }

  function setAppId(id: string) {
    appId.value = id
    try { localStorage.setItem(AUTH_STORAGE_KEYS.appId, id) } catch { /* noop */ }
  }

  /** 记录身份锚点并持久化 (与 token 同生命周期) */
  function setTenantIdValue(v: number | null) {
    tenantIdValue.value = v
    try {
      if (v === null) localStorage.removeItem(AUTH_STORAGE_KEYS.tenantId)
      else localStorage.setItem(AUTH_STORAGE_KEYS.tenantId, String(v))
    } catch { /* noop */ }
  }

  /**
   * 确保 identity 可判定: tenantId 未知时反查 /api/v1/auth/me (框架 token 不带 claim)。
   * 路由守卫在放行前 await — 失败静默 (identity 保持 null, 守卫放行, 后端兜底)。
   */
  async function ensureIdentity(): Promise<void> {
    if (!token.value || tenantIdValue.value !== null) return
    identityPromise = identityPromise ?? (async () => {
      try {
        const me = await http.get<{ tenantId: number | null }>('/api/v1/auth/me')
        setTenantIdValue(me?.tenantId ?? null)
      } catch { /* 未知身份 → 放行 */ }
    })()
    await identityPromise
    identityPromise = null
  }

  /** 登录: client_credentials 换 token */
  async function login(clientId: string, clientSecret: string) {
    const result = await loginApi(clientId, clientSecret)
    setToken(result.access_token, result.expires_in)
    setAppId(clientId)
    runtimeSecret.value = clientSecret
    try { sessionStorage.setItem(RUNTIME_SECRET_KEY, clientSecret) } catch { /* noop */ }
    await ensureIdentity()
    return result
  }

  /** 清登录态 (登出 / 401 / 被踢) */
  function clearAuth() {
    token.value = null
    appId.value = null
    expiresAt.value = 0
    runtimeSecret.value = null
    identityPromise = null
    setTenantIdValue(null)
    try { sessionStorage.removeItem(RUNTIME_SECRET_KEY) } catch { /* noop */ }
    try {
      localStorage.removeItem(AUTH_STORAGE_KEYS.token)
      localStorage.removeItem(AUTH_STORAGE_KEYS.appId)
      localStorage.removeItem(AUTH_STORAGE_KEYS.expiresAt)
    } catch { /* noop */ }
  }

  return {
    token, appId, expiresAt, runtimeSecret,
    isLoggedIn, isExpiringSoon, identity, ensureIdentity,
    setToken, setAppId, login, clearAuth,
  }
})
