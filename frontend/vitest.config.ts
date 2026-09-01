import { defineConfig } from 'vitest/config'
import { resolve } from 'path'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  define: {
    __EMBED_BUILD__: 'false',
    __APP_VERSION__: JSON.stringify('0.0.0-test'),
    __BUILD_TIME__: JSON.stringify('test'),
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    // 只跑 src 下的单测; e2e/** 是 playwright 的 (*.spec.ts 同名后缀, 排除之)
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    environment: 'jsdom',
    // jsdom 29: opaque origin (about:blank) 下 localStorage 为 undefined, 必须给真实 URL
    environmentOptions: {
      jsdom: {
        url: 'http://localhost:5173/',
      },
    },
    globals: true,
    // —— 上游已红的 SDK 套件 (上游参考仓同环境同败, 已核对 2026-08-27) ——
    // 根因: element-plus ^2.13.3 范围内类名/渲染行为漂移 + jsdom 29 差异。
    // SDK 是零改动豁免区, 不在本仓修复;
    // 可用 `pnpm test:sdk` 单独运行观察上游状态。
    exclude: [
      '**/node_modules/**',
      'src/components/sdk/__tests__/smoke.test.ts',
      'src/components/sdk/display/FcEmpty.test.ts',
      'src/components/sdk/form/FcButton.test.ts',
      'src/components/sdk/form/FcSelect.test.ts',
      'src/components/sdk/layout/FcNavGroup.test.ts',
      'src/components/sdk/layout/FcSidebarNav.test.ts',
      'src/components/sdk/overlay/FcConfirm.test.ts',
      'src/components/sdk/overlay/FcDialog.test.ts',
      'src/components/sdk/overlay/FcDrawer.test.ts',
      'src/components/sdk/overlay/FcPopover.test.ts',
      'src/components/sdk/theme/FcThemeProvider.test.ts',
      'src/components/sdk/theme/FcThemeSwitcher.test.ts',
    ],
  },
})