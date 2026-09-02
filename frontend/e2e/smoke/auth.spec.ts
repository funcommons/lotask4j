import { test, expect } from '@playwright/test'

/**
 * 冒烟: 登录链路 + 双域守卫 (dev server + dev-mock, 无需后端)
 *
 * dev-interceptor 签发 JWT 形状 mock token (payload 带 tenant_id claim):
 *   client_id='ADMIN'        → 平台 token (tenant_id=0)  → 落 /platform/*
 *   client_id='order-service' → 租户 token (tenant_id=9101) → 落 /tenant/*
 * 也可用 addInitScript 预置 token 直达 (与 dev-mock 常量同形, 见各 describe)。
 */
const PLATFORM_TOKEN = 'mock.eyJzdWIiOiJQTEFURk9STSIsInRlbmFudF9pZCI6MH0.sig'
const TENANT_TOKEN = 'mock.eyJzdWIiOiJURU5BTlQiLCJ0ZW5hbnRfaWQiOjkxMDF9.sig'

test.describe('auth smoke', () => {
  test('portal 门面可达', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/lotask4j/)
  })

  test('未登录访问 /platform/dashboard → 重定向 /login', async ({ page }) => {
    await page.goto('/platform/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })

  test('平台凭据登录 → 跳转 /platform/dashboard, KPI 可见 (mock 数据)', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('Client ID').fill('ADMIN')
    await page.getByPlaceholder('Client Secret').fill('any-dev-secret')
    await page.getByRole('button', { name: /登/ }).click()

    await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 10_000 })
    // mock stats: 待处理 3 / 运行中 2
    await expect(page.getByText('3').first()).toBeVisible()
  })

  test('租户凭据登录 → 跳转 /tenant/tasks', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('Client ID').fill('order-service')
    await page.getByPlaceholder('Client Secret').fill('tenant-secret')
    await page.getByRole('button', { name: /登/ }).click()

    await expect(page).toHaveURL(/\/tenant\/tasks$/, { timeout: 10_000 })
    // mock 任务列表渲染
    await expect(page.getByText('视频转码').first()).toBeVisible({ timeout: 10_000 })
  })

  test('域守卫: 平台身份访问 /tenant/* → 弹回 /platform/dashboard', async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.setItem('lotask4j:access_token', token)
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'ADMIN')
    }, PLATFORM_TOKEN)
    await page.goto('/tenant/tasks')
    await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 10_000 })
  })

  test('域守卫: 租户身份访问 /platform/* → 弹回 /tenant/tasks', async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.setItem('lotask4j:access_token', token)
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'order-service')
    }, TENANT_TOKEN)
    await page.goto('/platform/tenants')
    await expect(page).toHaveURL(/\/tenant\/tasks$/, { timeout: 10_000 })
  })

  test('登出 → 清态回登录页 (预置平台登录态)', async ({ page }) => {
    // 预置登录态
    await page.addInitScript((token) => {
      localStorage.setItem('lotask4j:access_token', token)
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'ADMIN')
    }, PLATFORM_TOKEN)
    await page.goto('/platform/dashboard')
    await expect(page).toHaveURL(/\/platform\/dashboard/)
  })
})
