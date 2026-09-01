import { defineConfig, mergeConfig } from 'vitest/config'
import baseConfig from './vitest.config'

/**
 * SDK 全量测试配置 — 包含上游已红的豁免区套件 (观察上游状态用).
 *
 * 默认 `pnpm test` 排除 12 个上游已红套件 (element-plus 类名漂移 + jsdom 29 差异,
 * 上游参考仓同环境同败, 见 vitest.config.ts exclude 注释);
 * 本配置去掉 exclude, 完整跑 SDK 测试。
 */
export default mergeConfig(
  baseConfig,
  defineConfig({
    test: {
      exclude: ['**/node_modules/**'],
    },
  }),
)
