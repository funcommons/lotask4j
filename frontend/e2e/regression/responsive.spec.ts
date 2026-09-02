import { test, expect } from '@playwright/test'

/**
 * 回归: 响应式 (tablet 768×1024 / mobile 375×812, desktop 也跑一份对照)
 * - mobile: 侧栏隐藏, 走 drawer 导航
 * - tablet/desktop: 常驻侧栏可用
 * 双域: 布局壳用 platform 身份, 任务列表用 tenant 身份。
 */
const PLATFORM_TOKEN = 'mock.eyJzdWIiOiJQTEFURk9STSIsInRlbmFudF9pZCI6MH0.sig'
const TENANT_TOKEN = 'mock.eyJzdWIiOiJURU5BTlQiLCJ0ZW5hbnRfaWQiOjkxMDF9.sig'

test('布局壳完整渲染 (viewport 对照, platform 域)', async ({ page }) => {
  await page.addInitScript((token) => {
    localStorage.setItem('lotask4j:access_token', token)
    localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
    localStorage.setItem('lotask4j:app_id', 'ADMIN')
  }, PLATFORM_TOKEN)
  await page.goto('/platform/dashboard')
  const width = page.viewportSize()?.width ?? 1440

  if (width < 768) {
    // mobile: 侧栏隐藏, drawer 打开后展开分组点叶子
    await expect(page.locator('.mobile-menu-trigger')).toBeVisible()
    await page.locator('.mobile-menu-trigger').click()
    await page.locator('.el-sub-menu__title', { hasText: '任务管理' }).click()
    await page.getByRole('menuitem', { name: '全量任务' }).click()
    await expect(page).toHaveURL(/\/platform\/tasks$/)
  } else {
    // desktop/tablet: 常驻侧栏
    const groupTitle = page.locator('.el-sub-menu__title', { hasText: '任务管理' })
    if (await groupTitle.isVisible()) {
      // 展开态 (desktop): 标题可点展开
      await groupTitle.click()
    } else {
      // 折叠轨 (tablet 自动收起): 点分组图标弹 popper
      await page.locator('.el-sub-menu').first().click()
    }
    await page.getByRole('menuitem', { name: '全量任务' }).first().click()
    await expect(page).toHaveURL(/\/platform\/tasks$/)
  }
})

test('页面无横向溢出 (tenant 任务列表)', async ({ page }) => {
  await page.addInitScript((token) => {
    localStorage.setItem('lotask4j:access_token', token)
    localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
    localStorage.setItem('lotask4j:app_id', 'order-service')
  }, TENANT_TOKEN)
  await page.goto('/tenant/tasks')
  await expect(page.getByText('视频转码').first()).toBeVisible({ timeout: 10_000 })
  const overflow = await page.evaluate(() =>
    document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBeLessThanOrEqual(2) // 允许亚像素
})

test('任务列表在窄屏可横向滚动查看 (不丢内容)', async ({ page }) => {
  test.skip(page.viewportSize() === undefined || page.viewportSize()!.width >= 768, '窄屏用例')
  await page.addInitScript((token) => {
    localStorage.setItem('lotask4j:access_token', token)
    localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
    localStorage.setItem('lotask4j:app_id', 'order-service')
  }, TENANT_TOKEN)
  await page.goto('/tenant/tasks')
  await expect(page.getByText('视频转码').first()).toBeVisible({ timeout: 10_000 })
  await expect(page.getByText('PDF 生成').first()).toBeVisible()
})
