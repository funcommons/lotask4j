import { test, expect } from '@playwright/test'

/**
 * 回归: 路由守卫矩阵 (desktop)
 * - 未登录访问受保护路由 → /login?redirect=...
 * - public 路由直接可达
 * - 登录后按 redirect 回跳
 */
const PROTECTED = ['/dashboard', '/tasks', '/active', '/workers', '/types', '/settings', '/embed-config', '/guide', '/demo', '/dev']

test.describe('路由守卫', () => {
  for (const path of PROTECTED) {
    test(`未登录访问 ${path} → 重定向 /login 且带 redirect`, async ({ page }) => {
      await page.goto(path)
      await expect(page).toHaveURL(/\/login\?redirect=/)
    })
  }

  test('未登录访问任务详情 → 同样拦截', async ({ page }) => {
    await page.goto('/tasks/YeirYkxHuQ')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })

  test('public 路由免登录: portal / login / embed', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/$/)
    await page.goto('/login')
    await expect(page).toHaveURL(/\/login$/)
    await page.goto('/embed/task-list')
    await expect(page).toHaveURL(/\/embed\/task-list$/)
  })

  test('登录后按 redirect 回跳原页面', async ({ page }) => {
    await page.goto('/workers')
    await expect(page).toHaveURL(/\/login\?redirect=/)
    await page.getByPlaceholder('Client ID').fill('ADMIN')
    await page.getByPlaceholder('Client Secret').fill('any')
    await page.getByRole('button', { name: /登/ }).click()
    await expect(page).toHaveURL(/\/workers$/)
  })

  test('token 过期 (expires_at 过去时) 同样拦截', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('lotask4j:access_token', 'stale-token')
      localStorage.setItem('lotask4j:expires_at', String(Date.now() - 1000))
    })
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })
})
