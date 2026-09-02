import { test, expect } from '@playwright/test'

/**
 * 前端 ↔ 真后端契约联调 (integration project)
 *
 * 与 smoke/regression (dev-mock) 不同: 走 vite proxy → 真实 ASTS 后端 (compose 栈)。
 * 覆盖只有真后端才能验证的契约:
 *   1. 登录 (client_credentials, 真凭据)
 *   2. 控制台手动提交任务 → axios 签名拦截器 → 后端签名校验通过 (signature.ts 契约)
 *   3. 任务出现在列表 (envelope 解包 / OpenID id / 真数据渲染)
 *
 * 运行前置:
 *   docker compose up -d --build && bash scripts/smoke.sh 之前的准备就绪即可
 *   LOTASK_PLATFORM_SECRET=smoke-platform-secret \
 *   LOTASK_BACKEND=http://localhost:19080 pnpm dev &
 *   pnpm exec playwright test --project=integration
 *
 * 未设置 LOTASK_PLATFORM_SECRET 时自动跳过 (dev-mock 环境不跑真后端联调)。
 */

const API = process.env.LOTASK_BACKEND_API ?? 'http://localhost:19080'
const PLATFORM_SECRET = process.env.LOTASK_PLATFORM_SECRET
const RUN = !!PLATFORM_SECRET

test.describe('前端 ↔ 真后端契约', () => {
  let clientId = ''
  let clientSecret = ''
  let typeKey = ''

  test.beforeAll(async ({ request }) => {
    test.skip(!RUN, 'LOTASK_PLATFORM_SECRET 未设置 — 跳过真后端联调')

    // Node 侧直接调后端 API 准备数据 (平台身份)
    const tokenRes = await request.post(`${API}/api/v1/auth/token`, {
      form: {
        grant_type: 'client_credentials',
        client_id: 'PLATFORM',
        client_secret: PLATFORM_SECRET!,
      },
    })
    const tokenJson = await tokenRes.json()
    const platformToken = tokenJson.data.access_token
    expect(platformToken).toBeTruthy()

    const ts = Date.now()
    clientId = `it-fe-${ts}`

    const tenantRes = await request.post(`${API}/api/v1/admin/tenants`, {
      headers: { Authorization: `Bearer ${platformToken}` },
      data: { name: clientId, description: 'frontend integration' },
    })
    const tenantJson = await tenantRes.json()
    clientSecret = tenantJson.data.tenantSecret
    const tenantId = tenantJson.data.id
    expect(clientSecret).toBeTruthy()

    typeKey = `fe-it-${ts}`
    const typeRes = await request.post(`${API}/api/v1/admin/types`, {
      headers: { Authorization: `Bearer ${platformToken}` },
      data: {
        typeKey, tenantId, name: '前端联调',
        concurrencyLimit: 5, timeoutSeconds: 3600, maxRetries: 1, isEnabled: true,
      },
    })
    expect((await typeRes.json()).code).toBe(0)
  })

  test('登录 → 手动提交任务 (签名) → 列表可见', async ({ page }) => {
    test.skip(!RUN, 'LOTASK_PLATFORM_SECRET 未设置')

    // 1. UI 登录 (真凭据 → 真 token)
    await page.goto('/login')
    await page.getByPlaceholder('Client ID').fill(clientId)
    await page.getByPlaceholder('Client Secret').fill(clientSecret)
    await page.getByRole('button', { name: /登/ }).click()
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })

    // 全量 API 观测: 任何 401/403/4xx 与 auth 失效码都记录 (排障: 会话被踢回登录页)
    const apiLog: string[] = []
    page.on('response', (r) => {
      if (r.url().includes('/api/') && r.status() >= 300) {
        apiLog.push(`${r.status()} ${r.request().method()} ${r.url().replace(API, '')}`)
      }
    })
    page.on('console', (msg) => {
      if (msg.text().includes('auth-expired') || msg.text().includes('[sign]')) {
        apiLog.push(`CONSOLE ${msg.text()}`)
        console.log(`[page] ${msg.text()}`)
      }
    })
    page.on('pageerror', (err) => apiLog.push(`PAGEERROR ${err.message.slice(0, 300)}`))
    page.on('console', (msg) => {
      if (msg.type() === 'error' || msg.type() === 'warning') {
        apiLog.push(`${msg.type().toUpperCase()} ${msg.text().slice(0, 260)}`)
      }
    })

    // 2. 任务列表 → 手动提交 (走 axios 签名拦截器 → 真后端签名校验)
    //    runtimeSecret 在 sessionStorage — 整页刷新不丢
    await page.goto('/tasks')
    await expect(page.getByText('手动提交任务').first()).toBeVisible()
    await page.getByRole('button', { name: /手动提交任务|提交任务/ }).first().click()

    // 自诊断: 捕获 submit 请求/响应 (签名失败 → 401/10101; 校验失败 → 20001/10106)
    const submitCalls: string[] = []
    page.on('response', (r) => {
      if (r.url().includes('/tasks/submit')) {
        submitCalls.push(`${r.status()} ${r.request().headers()['x-signature'] ? 'signed' : 'UNSIGNED'}`)
      }
    })

    const typeInput = page.getByPlaceholder('例如：data_export')
    await typeInput.fill(typeKey)
    await page
      .getByPlaceholder('{\n  "key": "value"\n}')
      .fill('{"source":"fe-integration"}')
    await page.getByRole('button', { name: /^提交$/ }).click()

    // 3. 提交成功提示 + 已提交 ID 渲染 (签名若失败此处为"任务提交失败")
    //    成功 alert + toast 各出现一次 → first()
    try {
      await expect(page.getByText('任务提交成功').first()).toBeVisible({ timeout: 15_000 })
    } catch (e) {
      throw new Error(
        `提交未成功。submit: [${submitCalls.join('; ') || '无'}] api>=300: [${apiLog.join('; ') || '无'}] url=${page.url()}`,
        { cause: e },
      )
    }

    // 4. 列表刷新后能看到该任务 (真数据渲染; 表格类型列显示 name 而非 typeKey)
    try {
      await page.reload()
      await expect(
        page.locator('.el-table, table').first(),
      ).toContainText('前端联调', { timeout: 15_000 })
    } catch (e) {
      throw new Error(`列表断言失败。diagnostics: [${apiLog.join(' || ') || '无'}]`, { cause: e })
    }
  })
})
