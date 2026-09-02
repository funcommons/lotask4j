import { test, expect, type Page } from '@playwright/test'

/**
 * 回归: 关键交互 (desktop, 登录态 + dev-mock)
 * - 侧边栏导航跳转
 * - 任务列表 Tab / 筛选 / 提交对话框
 * - 任务详情取消 confirm / 返回
 *
 * 注意: dev-mock 在 axios adapter 层短路, 无真实网络请求 →
 * 请求断言一律走 window.__devMockLog (由 mock 记录), 不用 waitForRequest。
 */
async function login(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('lotask4j:access_token', 'mock-access-token')
    localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
    localStorage.setItem('lotask4j:app_id', 'ADMIN')
  })
}

/** 等待 __devMockLog 中出现满足条件的调用 */
async function expectMockCall(page: Page, predicate: string, timeout = 5000) {
  await expect.poll(async () =>
    page.evaluate((pred) => {
      const log = (window as unknown as { __devMockLog?: Array<Record<string, unknown>> }).__devMockLog ?? []
      // eslint-disable-next-line no-new-func
      const fn = new Function('entry', `return (${pred})(entry)`)
      return log.some((entry) => fn(entry))
    }, predicate), { timeout }).toBe(true)
}

test.beforeEach(async ({ page }) => {
  await login(page)
})

test.describe('侧边栏导航', () => {
  test('分组菜单展开后点击叶子跳转', async ({ page }) => {
    await page.goto('/dashboard')
    // 分组默认折叠: 先点分组标题展开, 再点叶子 (el-menu-item → menuitem 角色)
    await page.locator('.el-sub-menu__title', { hasText: '任务管理' }).click()
    await page.getByRole('menuitem', { name: '活跃任务' }).click()
    await expect(page).toHaveURL(/\/active$/)
    await page.locator('.el-sub-menu__title', { hasText: '系统' }).click()
    await page.getByRole('menuitem', { name: '系统设置' }).click()
    await expect(page).toHaveURL(/\/settings$/)
  })

  test('顶级叶子直接可点 (仪表盘)', async ({ page }) => {
    await page.goto('/settings')
    await page.getByRole('menuitem', { name: '仪表盘' }).click()
    await expect(page).toHaveURL(/\/dashboard$/)
  })
})

test.describe('任务列表交互', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/tasks')
    await expect(page.getByText('视频转码').first()).toBeVisible({ timeout: 10_000 })
  })

  test('归档 Tab 切换 (isArchived 参数)', async ({ page }) => {
    await page.getByRole('button', { name: '归档任务' }).click()
    await expectMockCall(page, 'e => e.url?.includes("/api/v1/client/tasks") && e.params?.isArchived === 1')
  })

  test('状态筛选触发重新加载 (status=RUNNING)', async ({ page }) => {
    // el-select ≥2.13: placeholder 渲染为 span 而非 input placeholder → 点 wrapper
    await page.locator('.filter-status .el-select__wrapper').first().click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: '运行中' }).first().click()
    await expectMockCall(page, 'e => e.params?.status === "RUNNING"')
  })

  test('ID 搜索框回车触发请求', async ({ page }) => {
    await page.getByPlaceholder('搜索任务 ID').fill('YeirYkxHuQ')
    await page.getByPlaceholder('搜索任务 ID').press('Enter')
    await expectMockCall(page, 'e => e.params?.id === "YeirYkxHuQ"')
  })

  test('提交任务对话框: 非法 JSON 被拦截', async ({ page }) => {
    await page.getByRole('button', { name: '手动提交任务' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '手动提交任务' }).first()
    await expect(dialog).toBeVisible()
    await dialog.locator('textarea').fill('{ not valid json')
    await dialog.getByRole('button', { name: '提交' }).click()
    // 校验失败: 错误文案出现, 对话框仍开着, 未产生提交调用
    await expect(page.getByText('Payload 格式错误，请输入有效的 JSON')).toBeVisible({ timeout: 5000 })
    await expect(dialog).toBeVisible()
  })

  test('提交任务对话框: 合法 payload 提交成功并展示新 ID', async ({ page }) => {
    await page.getByRole('button', { name: '手动提交任务' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '手动提交任务' }).first()
    // 类型是文本输入 (非下拉), 直接填 typeKey
    await dialog.getByPlaceholder('例如：data_export').fill('data_export')
    await dialog.locator('textarea').fill('{"k":"v"}')
    await dialog.getByRole('button', { name: '提交' }).click()
    await expect(page.getByText('任务提交成功').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('MockNewTask01').first()).toBeVisible()
    // 列表页提交走 client 租户端点 (2026-09: 控制台为租户身份, admin 端点仅平台域)
    await expectMockCall(page, 'e => e.method === "post" && e.url?.endsWith("/api/v1/client/tasks/submit")')
  })
})

test.describe('任务详情交互', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/tasks/YeirYkxHuQ')
    await expect(page.getByText('拉取源文件').first()).toBeVisible({ timeout: 10_000 })
  })

  test('RUNNING 任务显示取消按钮, confirm 取消不执行', async ({ page }) => {
    const cancelBtn = page.getByRole('button', { name: '取消任务' })
    await expect(cancelBtn).toBeVisible()
    await cancelBtn.click()
    // FcConfirm 对话框 → 点「取消」不执行
    await page.getByRole('button', { name: '取 消' }).or(page.getByRole('button', { name: '取消' })).last().click()
    await expect(cancelBtn).toBeVisible()
    // 未产生 cancel 调用
    const called = await page.evaluate(() => {
      const log = (window as unknown as { __devMockLog?: Array<Record<string, unknown>> }).__devMockLog ?? []
      return log.some((e) => String(e.url || '').includes('/cancel'))
    })
    expect(called).toBe(false)
  })

  test('返回按钮回到任务列表', async ({ page }) => {
    await page.locator('.fc-section-header__back').click()
    await expect(page).toHaveURL(/\/tasks$/)
  })

  test('FAILED 任务详情渲染错误信息与失败步骤 (mock 特例 ID)', async ({ page }) => {
    await page.goto('/tasks/FailedAb9xQq')
    await expect(page.getByText(/渲染引擎超时|PDF_RENDER_TIMEOUT/).first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('渲染').first()).toBeVisible()
  })
})

test.describe('嵌入配置交互', () => {
  test('新建对话框可打开', async ({ page }) => {
    await page.goto('/embed-config')
    await page.getByRole('button', { name: /新建/ }).first().click()
    await expect(page.locator('.el-dialog').first()).toBeVisible({ timeout: 5000 })
  })
})
