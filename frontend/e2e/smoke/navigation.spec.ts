import { test, expect } from '@playwright/test'

/**
 * 冒烟: 双域主页面可达性 (登录态注入 + dev-mock 数据)
 *
 * 2026-09 路由域分离: /platform/* (平台治理, tenant_id=0) 与 /tenant/* (租户业务, tenant_id>0)。
 * 注入与 dev-mock 签发同形的 JWT token, 域守卫按 claim 分域放行。
 */
const PLATFORM_TOKEN = 'mock.eyJzdWIiOiJQTEFURk9STSIsInRlbmFudF9pZCI6MH0.sig'
const TENANT_TOKEN = 'mock.eyJzdWIiOiJURU5BTlQiLCJ0ZW5hbnRfaWQiOjkxMDF9.sig'

test.describe('platform 域 smoke', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.setItem('lotask4j:access_token', token)
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'ADMIN')
    }, PLATFORM_TOKEN)
  })

  for (const [path, name] of [
    ['/platform/dashboard', '仪表盘'],
    ['/platform/tasks', '任务管理'],
    ['/platform/workers', 'Worker 节点'],
    ['/platform/types', '任务类型'],
    ['/platform/settings', '系统设置'],
    ['/platform/tenants', '租户管理'],
    ['/platform/embed-config', '嵌入配置'],
    ['/platform/guide', '使用指南'],
    ['/dev', '开发示例'],
  ] as const) {
    test(`${name} (${path}) 可达`, async ({ page }) => {
      await page.goto(path)
      await expect(page).toHaveURL(new RegExp(path.replace(/\//g, '\\/')))
      // 页面标题元素存在 (不 blank)
      await expect(page.locator('#app')).not.toBeEmpty()
    })
  }

  test('平台任务列表 mock 数据渲染 (含归属租户)', async ({ page }) => {
    await page.goto('/platform/tasks')
    // mock 3 条任务: 视频转码 / 数据导出 / PDF 生成
    await expect(page.getByText('视频转码').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('数据导出').first()).toBeVisible()
  })

  test('租户管理 mock 数据渲染', async ({ page }) => {
    await page.goto('/platform/tenants')
    await expect(page.getByText('order-service').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('media-worker').first()).toBeVisible()
  })

  test('embed 组件免登录直达', async ({ page }) => {
    await page.goto('/embed/task-list')
    await expect(page.locator('#app')).not.toBeEmpty()
  })
})

test.describe('tenant 域 smoke', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.setItem('lotask4j:access_token', token)
      localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
      localStorage.setItem('lotask4j:app_id', 'order-service')
    }, TENANT_TOKEN)
  })

  for (const [path, name] of [
    ['/tenant/active', '活跃任务'],
    ['/tenant/tasks', '任务管理'],
    ['/tenant/guide', '使用指南'],
    ['/tenant/demo', '模拟测试'],
  ] as const) {
    test(`${name} (${path}) 可达`, async ({ page }) => {
      await page.goto(path)
      await expect(page).toHaveURL(new RegExp(path.replace(/\//g, '\\/')))
      await expect(page.locator('#app')).not.toBeEmpty()
    })
  }

  test('任务列表 mock 数据渲染', async ({ page }) => {
    await page.goto('/tenant/tasks')
    await expect(page.getByText('视频转码').first()).toBeVisible({ timeout: 10_000 })
  })

  test('任务详情 mock 渲染 (步骤 timeline)', async ({ page }) => {
    await page.goto('/tenant/tasks/YeirYkxHuQ')
    await expect(page.getByText('拉取源文件').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('转码').first()).toBeVisible()
  })
})
