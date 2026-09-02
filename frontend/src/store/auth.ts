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
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginApi } from '@/api/auth'

export const AUTH_STORAGE_KEYS = {
  token: 'lotask4j:access_token',
  appId: 'lotask4j:app_id',
  expiresAt: 'lotask4j:expires_at',
} as const

function readStorage(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}

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

  // —— getters ——
  const isLoggedIn = computed(() => !!token.value && Date.now() < expiresAt.value)
  /** 5 分钟内过期 (UI 提示用; 无 refresh, 由后端 auto-renew 滑动续期) */
  const isExpiringSoon = computed(() =>
    !!token.value && Date.now() > expiresAt.value - 5 * 60 * 1000)

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

  /** 登录: client_credentials 换 token */
  async function login(clientId: string, clientSecret: string) {
    const result = await loginApi(clientId, clientSecret)
    setToken(result.access_token, result.expires_in)
    setAppId(clientId)
    runtimeSecret.value = clientSecret
    try { sessionStorage.setItem(RUNTIME_SECRET_KEY, clientSecret) } catch { /* noop */ }
    return result
  }

  /** 清登录态 (登出 / 401 / 被踢) */
  function clearAuth() {
    token.value = null
    appId.value = null
    expiresAt.value = 0
    runtimeSecret.value = null
    try { sessionStorage.removeItem(RUNTIME_SECRET_KEY) } catch { /* noop */ }
    try {
      localStorage.removeItem(AUTH_STORAGE_KEYS.token)
      localStorage.removeItem(AUTH_STORAGE_KEYS.appId)
      localStorage.removeItem(AUTH_STORAGE_KEYS.expiresAt)
    } catch { /* noop */ }
  }

  return {
    token, appId, expiresAt, runtimeSecret,
    isLoggedIn, isExpiringSoon,
    setToken, setAppId, login, clearAuth,
  }
})
