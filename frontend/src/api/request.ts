import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { v4 as uuidv4 } from 'uuid'
import { ApiError, HTTP_STATUS } from '@/api/errorCodes'
import type { ApiFieldError } from '@/api/errorCodes'
import { getOrCreateTraceId, TRACE_ID_STORAGE_KEY } from '@/utils/trace'
import { authBus } from '@/utils/authBus'
import router from '@/router'
import i18n from '@/locales'
import { logger } from '@/utils'
import { installDevMock } from '@/mock/dev-interceptor'

const t = i18n.global.t

// API 基础配置
// 开发环境使用代理，生产环境使用实际地址
const BASE_URL = ''
const TIMEOUT = 30000

const WRITE_METHODS = new Set(['post', 'put', 'patch', 'delete'])

// 鉴权相关白名单 (不注入 Bearer Token)
const AUTH_PUBLIC_PATHS = new Set([
  '/api/v1/auth/token',
])

/** embed 构建 (嵌入 widget): 无登录态, 走后端 ASTS_USER_ID cookie, 不注入 Bearer 不跳登录 */
const IS_EMBED_BUILD = !!__EMBED_BUILD__

/** 懒加载 authStore (避免循环依赖: store 引用 api, api 引用 store) */
let authStoreGetter: (() => { token: string | null; isLoggedIn: boolean; clearAuth: () => void }) | null = null
export function setAuthStoreGetter(getter: typeof authStoreGetter) {
  authStoreGetter = getter
}

/**
 * 登录态失效统一处理: 清态 + 广播 + 跳登录页
 * 仿 benefit4j benefitClient.handleAuthFailure (10201 过期 / 10205 被踢 / 10208 注销 / HTTP 401)
 */
function handleAuthFailure(code: number) {
  authBus.emit({ type: 'auth-expired', code, timestamp: Date.now() })
  if (authStoreGetter) {
    const store = authStoreGetter()
    store.clearAuth()
  }
  if (!IS_EMBED_BUILD) {
    redirectToLogin()
  }
}

// 创建 Axios 实例
const request: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: {
    'Content-Type': 'application/json'
  }
})

// dev-only mock 拦截器 (build 时被 Vite tree-shake 掉)
// 必须在业务 response 拦截器之前注册: axios 响应拦截器按注册顺序执行, 否则业务拦截器
// 会先把 response 解构成 raw data, mock 拿到的 response.config 就是 undefined.
if (import.meta.env.DEV) {
  installDevMock(request)
}

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // P0 修复: FormData 上传时必须让 axios 自动加 multipart/form-data + boundary
    // 全局默认 Content-Type: application/json 会让 axios 把 FormData 序列化成 JSON 字符串
    // (axios 不会覆盖 explicit headers, 即使 data 是 FormData)
    // 这里按 data 类型动态调整: FormData → 删 Content-Type, 其他 → 强制 application/json
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      config.headers.delete('Content-Type')
    } else if (!config.headers.has('Content-Type') && !(config.data instanceof URLSearchParams)) {
      config.headers.set('Content-Type', 'application/json')
    }

    // 注入 Bearer Token (除鉴权公开接口外; embed 构建无登录态)
    const url = config.url || ''
    const isAuthPublic = AUTH_PUBLIC_PATHS.has(url) || url.includes('/api/v1/auth/')
    if (!IS_EMBED_BUILD && authStoreGetter && !isAuthPublic) {
      const store = authStoreGetter()
      if (store.token) {
        config.headers.set('Authorization', `Bearer ${store.token}`)
      }
    } else if (IS_EMBED_BUILD && !isAuthPublic) {
      // embed 短期 token (2026-09 租户化): /web-embed/{type} 入口按 accessKey 归属租户
      // 签发 TENANT 型 token 种入 cookie, 此处读取后以 Bearer 调 client GET
      // (cookie 非 httpOnly — iframe 嵌入场景; token 会话级, 随 cookie 过期)
      const m = document.cookie.match(/(?:^|;\s*)ASTS_EMBED_TOKEN=([^;]*)/)
      if (m && m[1]) {
        config.headers.set('Authorization', `Bearer ${decodeURIComponent(m[1])}`)
      }
    }

    // 链路追踪: 同一会话保持 trace_id, 方便前后端日志串联
    config.headers.set('X-Trace-Id', getOrCreateTraceId())

    // 写操作幂等性: 同一 key 重试时, 后端可识别并返回首次结果 (防止重复提交)
    if (config.method && WRITE_METHODS.has(config.method.toLowerCase())) {
      config.headers.set('Idempotency-Key', uuidv4())
    }

    return config
  },
  (error) => {
    logger.error('Request error:', error)
    return Promise.reject(error)
  }
)

/**
 * 从 config 中读取 silent 标记
 */
function isSilent(config?: AxiosRequestConfig): boolean {
  return (config as RequestConfig | undefined)?.silent === true
}

/**
 * 从响应 (header + body) 提取 trace_id, 并写回 sessionStorage
 * 保证后续请求继续携带同一个 trace, 整条链路日志可串联
 */
function captureTraceId(headers: AxiosResponse['headers'], body: { trace_id?: string } | null | undefined): string {
  const headerVal = headers['x-trace-id'] || headers['X-Trace-Id']
  const traceId = (typeof headerVal === 'string' ? headerVal : '') || body?.trace_id || ''
  if (traceId) {
    try { sessionStorage.setItem(TRACE_ID_STORAGE_KEY, traceId) } catch { /* noop */ }
  }
  return traceId
}

/**
 * 响应拦截器
 * <p>
 * 业务错误 → throw ApiError（不弹窗，由 handleError 统一处理）
 * HTTP 错误 → throw ApiError（带 status）
 * 401 特殊处理：清登录态 + 跳登录页
 */
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data, headers } = response

    // 如果响应直接是数据（无 code 字段），返回它
    if (data === null || data === undefined || data.code === undefined) {
      captureTraceId(headers, null)
      return data
    }

    // 业务成功
    if (data.code === 200 || data.code === 0) {
      captureTraceId(headers, data)
      return data.data
    }

    // 业务错误：转换为 ApiError
    const errorMsg = data.message || data.msg || t('api.request-failed')
    const traceId = captureTraceId(headers, data)
    // framework4j 字段级错误 (param validate 失败 / 部分业务校验) -> ApiError.details
    const details: ApiFieldError[] = Array.isArray(data.error)
      ? data.error
          .filter((e: any) => e != null && typeof e.message === 'string')
          .map((e: any) => ({
            field: typeof e.field === 'string' ? e.field : '',
            code: typeof e.code === 'string' ? e.code : undefined,
            message: e.message,
            rejectedValue: e.rejectedValue,
          }))
      : []
    const apiError = new ApiError(data.code, errorMsg, {
      traceId,
      details: details.length > 0 ? details : undefined,
    })
    if (isSilent(response.config)) {
      apiError.silent = true
    }

    // framework4j accesstoken 失效码 (HTTP 200 envelope 内): 10201 过期 / 10205 被踢 / 10208 注销 / 10200 未认证
    if (AUTH_FAIL_CODES.has(data.code) && !IS_EMBED_BUILD) {
      handleAuthFailure(data.code)
      apiError.silent = true
    }
    return Promise.reject(apiError)
  },
  (error) => {
    // HTTP 错误处理
    const silent = isSilent(error.config)

    // HTTP 401: 无 refresh 机制, 统一清态 + 跳登录 (embed 构建豁免)
    if (error.response?.status === HTTP_STATUS.UNAUTHORIZED && !IS_EMBED_BUILD) {
      const bodyCode = (error.response.data as { code?: number } | undefined)?.code
      handleAuthFailure(bodyCode ?? error.response.status)
      const apiError = new ApiError(HTTP_STATUS.UNAUTHORIZED, t('api.unauthorized'), { silent })
      return Promise.reject(apiError)
    }

    if (error.response) {
      const { status, data, headers } = error.response
      const traceId = captureTraceId(headers, data)
      let message = t('api.network-error')

      switch (status) {
        case HTTP_STATUS.BAD_REQUEST:
          message = data?.message || t('api.bad-request')
          break
        case HTTP_STATUS.UNAUTHORIZED:
          message = t('api.unauthorized')
          redirectToLogin()
          break
        case HTTP_STATUS.FORBIDDEN:
          message = t('api.forbidden')
          break
        case HTTP_STATUS.NOT_FOUND:
          message = t('api.not-found')
          break
        case HTTP_STATUS.INTERNAL_SERVER_ERROR:
          message = data?.message || t('api.server-error')
          break
        default:
          message = data?.message || `${t('api.request-failed')} (${status})`
      }

      const apiError = new ApiError(status, message, { status, silent, traceId })
      return Promise.reject(apiError)
    }

    if (error.code === 'ECONNABORTED') {
      const apiError = new ApiError(0, t('api.timeout'), { silent })
      return Promise.reject(apiError)
    }

    // 其他网络错误
    const apiError = new ApiError(0, t('api.network-error'), { silent })
    return Promise.reject(apiError)
  }
)

/** framework4j accesstoken 失效业务码 (envelope code) */
const AUTH_FAIL_CODES = new Set<number>([10200, 10201, 10205, 10208])

/** 跳登录页 (若路由存在) */
function redirectToLogin() {
  if (router.hasRoute('Login') || router.getRoutes().some(r => r.path === '/login')) {
    const current = router.currentRoute.value
    if (current.path !== '/login') {
      router.push({ path: '/login', query: current.fullPath !== '/' ? { redirect: current.fullPath } : undefined })
    }
  }
}

/**
 * 封装的请求方法
 * <p>
 * 支持 `silent: true` 选项：开启后响应拦截器不会自动弹窗，
 * 由调用方决定如何处理错误。
 */
export interface RequestConfig extends AxiosRequestConfig {
  /** 静默请求：拦截器不弹窗，由调用方 handleError 处理 */
  silent?: boolean
}

export const http = {
  get<T = unknown>(url: string, config?: RequestConfig): Promise<T> {
    return request.get(url, config)
  },

  post<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return request.post(url, data, config)
  },

  put<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return request.put(url, data, config)
  },

  delete<T = unknown>(url: string, config?: RequestConfig): Promise<T> {
    return request.delete(url, config)
  },

  patch<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return request.patch(url, data, config)
  }
}

export default request
