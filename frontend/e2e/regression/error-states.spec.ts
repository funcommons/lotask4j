import { test, expect } from '@playwright/test'

/**
 * 回归: 失败态
 * - 登录失败 (mock bad-secret → 20105) 停留登录页 + toast
 * - 任务详情不存在 (mock 特例 → 20100) toast + 页面不崩 (tenant 域)
 * - 失败列表项渲染错误态 (tenant 域)
 */
const TENANT_TOKEN = 'mock.eyJzdWIiOiJURU5BTlQiLCJ0ZW5hbnRfaWQiOjkxMDF9.sig'

test.describe('失败态', () => {
  test('登录失败: 错误凭据 → toast + 停留 /login', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('Client ID').fill('ADMIN')
    await page.getByPlaceholder('Client Secret').fill('bad-secret')
    await page.getByRole('button', { name: /登/ }).click()
    // ApiError → toast (el-message), 停留登录页
    await expect(page.locator('.el-message').first()).toBeVisible({ timeout: 5000 })
    await expect(page).toHaveURL(/\/login/)
  })

  test('登录失败后凭据清空可重试成功 → 平台首页', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('Client ID').fill('ADMIN')
    await page.getByPlaceholder('Client Secret').fill('bad-secret')
    await page.getByRole('button', { name: /登/ }).click()
    await expect(page).toHaveURL(/\/login/)
    await page.getByPlaceholder('Client Secret').fill('good-secret')
    await page.getByRole('button', { name: /登/ }).click()
    await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 10_000 })
  })

  test('任务详情不存在: toast + 页面不崩 + 可返回', async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.setItem('lotask4j:access_token', token)
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'order-service')
    }, TENANT_TOKEN)
    await page.goto('/tenant/tasks/NotFound404')
    await expect(page.locator('.el-message').first()).toBeVisible({ timeout: 5000 })
    // 不崩: 步骤 timeline 未渲染, 返回按钮可用
    await expect(page.getByText('拉取源文件')).toHaveCount(0)
    await page.locator('.fc-section-header__back').click()
    await expect(page).toHaveURL(/\/tenant\/tasks$/)
  })

  test('任务列表含 FAILED 项: 失败状态标签可见', async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.setItem('lotask4j:access_token', token)
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'order-service')
    }, TENANT_TOKEN)
    await page.goto('/tenant/tasks')
    // mock 列表含 1 条 FAILED; 表格状态列渲染 '失败' 标签 (exact 避免命中 '今日失败' 等文案)
    await expect(page.getByText('失败', { exact: true }).first()).toBeVisible({ timeout: 10_000 })
  })
})
