import { defineConfig } from 'vitepress'

// lotask4j 用户文档站 (VitePress) — 内容源即本目录 markdown
// 本地预览: pnpm --dir frontend docs:dev    构建: pnpm --dir frontend docs:build
export default defineConfig({
  lang: 'zh-CN',
  title: 'lotask4j 异步慢任务服务',
  description:
    'ASTS — 分布式异步任务处理平台: 实时进度、任务取消、可靠回调、多租户隔离与可视化管理后台',
  base: '/lotask4j/',
  srcDir: '.',
  ignoreDeadLinks: false,
  themeConfig: {
    siteTitle: 'lotask4j 文档中心',
    outline: { level: [2, 3], label: '本页目录' },
    docFooter: { prev: '上一篇', next: '下一篇' },
    lastUpdated: { text: '最后更新' },
    search: { provider: 'local', options: { translations: { button: { buttonText: '搜索文档' } } } },
    sidebar: [
      {
        text: '产品简介',
        items: [
          { text: '什么是异步慢任务服务', link: '/product/introduction' },
          { text: '应用场景', link: '/product/scenarios' },
          { text: '产品架构', link: '/product/architecture' },
          { text: '基本概念与术语表', link: '/product/glossary' },
        ],
      },
      {
        text: '快速入门',
        items: [
          { text: '准备工作', link: '/quick-start/prepare' },
          { text: '提交第一个任务', link: '/quick-start/first-task' },
          { text: '实现第一个 Worker', link: '/quick-start/first-worker' },
        ],
      },
      {
        text: '用户指南',
        items: [
          { text: '任务生命周期', link: '/user-guide/lifecycle' },
          { text: '任务提交与幂等', link: '/user-guide/submit-idempotency' },
          { text: '进度上报与取消', link: '/user-guide/progress-cancel' },
          { text: '回调与 Webhook', link: '/user-guide/webhook' },
          { text: '任务归档', link: '/user-guide/archive' },
          { text: '嵌入组件', link: '/user-guide/embed' },
        ],
      },
      {
        text: '开发指南',
        items: [
          { text: '认证与凭据', link: '/dev-guide/auth' },
          { text: '公共约定', link: '/dev-guide/api-conventions' },
          { text: 'Client API', link: '/dev-guide/client-api' },
          { text: 'Worker API', link: '/dev-guide/worker-api' },
          { text: 'Admin API', link: '/dev-guide/admin-api' },
          { text: '错误码', link: '/dev-guide/error-codes' },
        ],
      },
      {
        text: '最佳实践',
        items: [
          { text: 'Worker 开发规范', link: '/best-practice/worker' },
          { text: 'Webhook 验签与 verify-then-act', link: '/best-practice/webhook-verify' },
          { text: '背压与限流', link: '/best-practice/backpressure' },
        ],
      },
      {
        text: '管理指南',
        items: [
          { text: '租户管理', link: '/admin-guide/tenant' },
          { text: '任务类型配置', link: '/admin-guide/task-type' },
          { text: '监控与运维', link: '/admin-guide/monitoring' },
        ],
      },
      {
        text: '安全',
        items: [
          { text: '多租户隔离', link: '/security/tenant-isolation' },
          { text: '密钥管理与轮换', link: '/security/credential-rotation' },
        ],
      },
      {
        text: '附录',
        items: [
          { text: 'FAQ', link: '/faq' },
          { text: '发布记录', link: '/release-notes' },
        ],
      },
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/funcommons/lotask4j' },
    ],
  },
})
