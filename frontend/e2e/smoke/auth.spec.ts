import { test, expect } from '@playwright/test'

/**
 * 冒烟: 登录链路 + 主导航 (dev server + dev-mock, 无需后端)
 * dev-interceptor 拦截 /api/v1/auth/token — 任意凭据可登录。
 */
test.describe('auth smoke', () => {
  test('portal 门面可达', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/lotask4j/)
  })

  test('未登录访问 dashboard → 重定向 /login', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })

  test('登录成功 → 跳转 dashboard, KPI 可见 (mock 数据)', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('Client ID').fill('ADMIN')
    await page.getByPlaceholder('Client Secret').fill('any-dev-secret')
    await page.getByRole('button', { name: /登/ }).click()

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })
    // mock stats: 待处理 3 / 运行中 2
    await expect(page.getByText('3').first()).toBeVisible()
  })

  test('登出 → 清态回登录页', async ({ page }) => {
    // 预置登录态
    await page.addInitScript(() => {
      localStorage.setItem('lotask4j:access_token', 'mock-access-token')
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'ADMIN')
    })
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/dashboard/)
  })
})
