import { test, expect } from '@playwright/test'

/**
 * 冒烟: lotask 主页面可达性 (登录态注入 + dev-mock 数据)
 */
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('lotask4j:access_token', 'mock-access-token')
    localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
    localStorage.setItem('lotask4j:app_id', 'ADMIN')
  })
})

test.describe('lotask pages smoke', () => {
  for (const [path, name] of [
    ['/dashboard', '仪表盘'],
    ['/active', '活跃任务'],
    ['/tasks', '任务管理'],
    ['/workers', 'Worker 节点'],
    ['/types', '任务类型'],
    ['/settings', '系统设置'],
    ['/tenants', '租户管理'],
    ['/embed-config', '嵌入配置'],
    ['/guide', '使用指南'],
    ['/demo', '模拟测试'],
    ['/dev', '开发示例'],
  ] as const) {
    test(`${name} (${path}) 可达`, async ({ page }) => {
      await page.goto(path)
      await expect(page).toHaveURL(new RegExp(path.replace('/', '\\/')))
      // 页面标题元素存在 (不 blank)
      await expect(page.locator('#app')).not.toBeEmpty()
    })
  }

  test('任务列表 mock 数据渲染', async ({ page }) => {
    await page.goto('/tasks')
    // mock 3 条任务: 视频转码 / 数据导出 / PDF 生成
    await expect(page.getByText('视频转码').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('数据导出').first()).toBeVisible()
  })

  test('任务详情 mock 渲染 (步骤 timeline)', async ({ page }) => {
    await page.goto('/tasks/YeirYkxHuQ')
    await expect(page.getByText('拉取源文件').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('转码').first()).toBeVisible()
  })

  test('租户管理 mock 数据渲染', async ({ page }) => {
    await page.goto('/tenants')
    await expect(page.getByText('order-service').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('media-worker').first()).toBeVisible()
  })

  test('embed 组件免登录直达', async ({ page }) => {
    await page.goto('/embed/task-list')
    await expect(page.locator('#app')).not.toBeEmpty()
  })
})
