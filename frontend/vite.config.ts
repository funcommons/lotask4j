import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import pkg from './package.json'

// lotask4j 后端 (Spring Boot :9080)
const BACKEND = 'http://localhost:9080'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // embed 模式: 构建嵌入 widget (base=/web-embed/, 产物拷入 backend static/web-embed/)
  const isEmbed = mode === 'embed'

  return {
    base: isEmbed ? '/web-embed/' : '/',
    plugins: [
      vue({
        template: {
          compilerOptions: {
            // 预留: web component 透传 (如后续接入 altcha)
            isCustomElement: (tag) => tag === 'altcha-widget',
          },
        },
      }),
      AutoImport({
        imports: ['vue', 'pinia', 'vue-i18n'],
        dts: 'auto-imports.d.ts',
        eslintrc: { enabled: false },
      }),
    ],
    define: {
      __APP_VERSION__: JSON.stringify(pkg.version || '0.0.0'),
      __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
      __EMBED_BUILD__: JSON.stringify(isEmbed),
    },
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "@/styles/variables.scss" as *;`
        }
      }
    },
    server: {
      port: 9083,
      open: true,
      allowedHosts: [
        'localhost',
        '127.0.0.1',
      ],
      proxy: {
        // lotask4j 后端 API
        '/api': {
          target: BACKEND,
          changeOrigin: true
        },
        // 嵌入 widget (backend 静态托管, dev 时代理到后端)
        '/web-embed': {
          target: BACKEND,
          changeOrigin: true
        },
        // swagger (backend)
        '/swagger-ui': {
          target: BACKEND,
          changeOrigin: true
        },
        '/v3/api-docs': {
          target: BACKEND,
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: isEmbed ? 'dist-embed' : 'dist',
      // 拆分大依赖到独立 chunk, 避免 500kB 警告
      rollupOptions: isEmbed ? {} : {
        output: {
          manualChunks: {
            // Vue 运行时 + 路由 + 状态管理
            'vue-vendor': ['vue', 'vue-router', 'pinia', 'vue-i18n'],
            // UI 库
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
          },
        },
      },
      // 单 chunk 报警阈值 (kB); element-plus 完整包约 1MB
      chunkSizeWarningLimit: 1100,
    }
  }
})
