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
    return result
  }

  /** 清登录态 (登出 / 401 / 被踢) */
  function clearAuth() {
    token.value = null
    appId.value = null
    expiresAt.value = 0
    try {
      localStorage.removeItem(AUTH_STORAGE_KEYS.token)
      localStorage.removeItem(AUTH_STORAGE_KEYS.appId)
      localStorage.removeItem(AUTH_STORAGE_KEYS.expiresAt)
    } catch { /* noop */ }
  }

  return {
    token, appId, expiresAt,
    isLoggedIn, isExpiringSoon,
    setToken, setAppId, login, clearAuth,
  }
})
