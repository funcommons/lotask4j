import { test, expect, type Page } from '@playwright/test'

/**
 * 回归: 视觉基线 (desktop, light/dark 双主题)
 *
 * 只截稳定页 (无轮询/无动态时间):
 *   login / portal / dashboard (静态 mock KPI) / settings / guide
 * 轮询页 (task-detail 走秒 / active / workers 相对时间) 不进像素基线,
 * 由 interactions / navigation / error-states 覆盖功能回归。
 *
 * 重生成基线: npx playwright test visual.spec.ts --update-snapshots
 */
const PAGES: Array<{ name: string; path: string; auth: boolean; ready: string }> = [
  { name: 'login', path: '/login', auth: false, ready: 'Client ID' },
  { name: 'portal', path: '/', auth: false, ready: 'lotask4j' },
  { name: 'dashboard', path: '/platform/dashboard', auth: true, ready: '待处理' },
  { name: 'settings', path: '/platform/settings', auth: true, ready: '系统基本信息' },
  { name: 'guide', path: '/platform/guide', auth: true, ready: '使用手册' },
  { name: 'ptasks', path: '/platform/tasks', auth: true, ready: '视频转码' },
]

async function setTheme(page: Page, theme: 'light' | 'dark') {
  await page.evaluate((t) => {
    document.documentElement.setAttribute('data-theme', t)
  }, theme)
  await page.waitForTimeout(300) // token 切换过渡
}

/** 等所有图片加载完成 (portal 首页有懒加载图, 不等完截图会矮一截) */
async function waitForImages(page: Page, timeout = 8000) {
  await page.waitForFunction(() =>
    Array.from(document.images).every((img) => img.complete), undefined, { timeout })
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(`visual ${theme}`, () => {
    for (const p of PAGES) {
      test(`${p.name} (${p.path})`, async ({ page }) => {
        if (p.auth) {
          await page.addInitScript((token) => {
            localStorage.setItem('lotask4j:access_token', token)
            localStorage.setItem('lotask4j:expires_at', String(Date.now() + 3600_000))
            localStorage.setItem('lotask4j:app_id', 'ADMIN')
          }, 'mock.eyJzdWIiOiJQTEFURk9STSIsInRlbmFudF9pZCI6MH0.sig')
        }
        await page.goto(p.path)
        // 等关键内容出现 (dev-mock 无网络请求, networkidle 不可靠)
        await expect(page.getByText(p.ready).first()).toBeVisible({ timeout: 10_000 })
        await waitForImages(page)
        await setTheme(page, theme)
        await expect(page).toHaveScreenshot(`${p.name}-${theme}.png`, {
          fullPage: true,
          animations: 'disabled',
          caret: 'hide',
          maxDiffPixelRatio: 0.02, // 容忍字体渲染亚像素差
        })
      })
    }
  })
}
