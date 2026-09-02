import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { isFeatureEnabled, type FeatureKey } from '@/config/features'
import { useEmbedParams } from '@/composables/useEmbedParams'
import i18n from '@/locales'
import { ElMessageBox } from 'element-plus'

const t = i18n.global.t

/**
 * 嵌入 widget 构建 (vite build --mode embed):
 * 只挂 /embed/* 三条路由, base=/web-embed/, 免登录 (后端 ASTS_USER_ID cookie 鉴权).
 */
const isEmbedBuild = !!__EMBED_BUILD__

const embedRoutes: RouteRecordRaw[] = [
  {
    path: '/embed',
    component: () => import('@/layout/PageLayout.vue'),
    meta: { public: true, layout: 'page' },
    redirect: '/embed/task-list',
    children: [
      {
        path: 'task-list',
        name: 'EmbedTaskList',
        component: () => import('@/views/lotask/embed/TaskList.vue'),
        meta: { title: 'router.embed-task-list', public: true, hideInMenu: true }
      },
      {
        path: 'task-detail',
        name: 'EmbedTaskDetail',
        component: () => import('@/views/lotask/embed/TaskDetail.vue'),
        meta: { title: 'router.embed-task-detail', public: true, hideInMenu: true }
      },
      {
        path: 'task-card',
        name: 'EmbedTaskCard',
        component: () => import('@/views/lotask/embed/TaskCard.vue'),
        meta: { title: 'router.embed-task-card', public: true, hideInMenu: true }
      }
    ]
  }
]

/** 主应用路由 (双域控制台 + /dev 参考页 + Portal 门面) */
const appRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: 'router.login', public: true, hideInMenu: true, layout: 'blank' }
  },
  {
    path: '/',
    name: 'Portal',
    component: () => import('@/views/PortalHome.vue'),
    meta: { title: 'router.portal', public: true, hideInMenu: true, layout: 'blank' }
  },
  // —— 平台域控制台 (admin API; 登录身份 tenant_id=0 专属) ——
  {
    path: '/platform',
    component: () => import('@/layout/AppLayout.vue'),
    meta: { domain: 'platform' },
    redirect: '/platform/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'PlatformDashboard',
        component: () => import('@/views/lotask/Dashboard.vue'),
        meta: { title: 'router.dashboard', domain: 'platform' }
      },
      {
        path: 'tasks',
        name: 'PlatformTasks',
        component: () => import('@/views/lotask/PlatformTasks.vue'),
        meta: { title: 'router.task-list', domain: 'platform' }
      },
      {
        path: 'workers',
        name: 'WorkerNodes',
        component: () => import('@/views/lotask/WorkerNodes.vue'),
        meta: { title: 'router.worker-nodes', domain: 'platform' }
      },
      {
        path: 'types',
        name: 'TaskTypeConfig',
        component: () => import('@/views/lotask/TaskTypeConfig.vue'),
        meta: { title: 'router.task-type-config', domain: 'platform' }
      },
      {
        path: 'settings',
        name: 'SystemSettings',
        component: () => import('@/views/lotask/SystemSettings.vue'),
        meta: { title: 'router.system-settings', domain: 'platform' }
      },
      {
        path: 'embed-config',
        name: 'WebEmbedConfig',
        component: () => import('@/views/lotask/WebEmbedConfig.vue'),
        meta: { title: 'router.web-embed-config', domain: 'platform' }
      },
      {
        path: 'tenants',
        name: 'Tenants',
        component: () => import('@/views/lotask/Tenants.vue'),
        meta: { title: 'router.tenants', domain: 'platform' }
      },
      {
        path: 'guide',
        name: 'PlatformGuide',
        component: () => import('@/views/lotask/guide/UserGuide.vue'),
        meta: { title: 'router.user-guide', domain: 'platform' }
      },
      {
        path: 'guide/client',
        name: 'PlatformClientGuide',
        component: () => import('@/views/lotask/guide/ClientGuide.vue'),
        meta: { title: 'router.client-guide', domain: 'platform', hideInMenu: true }
      },
      {
        path: 'guide/worker',
        name: 'PlatformWorkerGuide',
        component: () => import('@/views/lotask/guide/WorkerGuide.vue'),
        meta: { title: 'router.worker-guide', domain: 'platform', hideInMenu: true }
      }
    ]
  },
  // —— 租户域控制台 (client/worker API; 登录身份 tenant_id>0 专属) ——
  {
    path: '/tenant',
    component: () => import('@/layout/AppLayout.vue'),
    meta: { domain: 'tenant' },
    redirect: '/tenant/tasks',
    children: [
      {
        path: 'active',
        name: 'ActiveTasks',
        component: () => import('@/views/lotask/ActiveTasks.vue'),
        meta: { title: 'router.active-tasks', domain: 'tenant' }
      },
      {
        path: 'tasks',
        name: 'TenantTasks',
        component: () => import('@/views/lotask/TaskList.vue'),
        meta: { title: 'router.task-list', domain: 'tenant' }
      },
      {
        path: 'tasks/:id',
        name: 'TenantTaskDetail',
        component: () => import('@/views/lotask/TaskDetail.vue'),
        meta: { title: 'router.task-detail', domain: 'tenant', hideInMenu: true }
      },
      {
        path: 'demo',
        name: 'DemoSimulator',
        component: () => import('@/views/lotask/DemoSimulator.vue'),
        meta: { title: 'router.demo-simulator', domain: 'tenant' }
      },
      {
        path: 'guide',
        name: 'TenantGuide',
        component: () => import('@/views/lotask/guide/UserGuide.vue'),
        meta: { title: 'router.user-guide', domain: 'tenant' }
      },
      {
        path: 'guide/client',
        name: 'TenantClientGuide',
        component: () => import('@/views/lotask/guide/ClientGuide.vue'),
        meta: { title: 'router.client-guide', domain: 'tenant', hideInMenu: true }
      },
      {
        path: 'guide/worker',
        name: 'TenantWorkerGuide',
        component: () => import('@/views/lotask/guide/WorkerGuide.vue'),
        meta: { title: 'router.worker-guide', domain: 'tenant', hideInMenu: true }
      }
    ]
  },
  // —— /dev 演示页 (17 个子页, 参考页保留; 登录即可见) ——
  {
    path: '/dev',
    component: () => import('@/layout/AppLayout.vue'),
    children: [
      {
        path: '',
        name: 'DevIndex',
        component: () => import('@/views/dev/index.vue'),
        meta: { title: 'router.dev-index', icon: 'Tools', feature: 'dev' }
      },
      {
        path: 'inspiration',
        name: 'DevInspiration',
        component: () => import('@/views/dev/InspirationDemo.vue'),
        meta: { title: 'router.dev-inspiration', hideInMenu: true }
      },
      {
        path: 'users',
        name: 'DevUsers',
        component: () => import('@/views/dev/UsersDemo.vue'),
        meta: { title: 'router.dev-users', hideInMenu: true }
      },
      {
        path: 'statistics',
        name: 'DevStatistics',
        component: () => import('@/views/dev/StatisticsDemo.vue'),
        meta: { title: 'router.dev-statistics', hideInMenu: true }
      },
      {
        path: 'workspace',
        name: 'DevWorkspace',
        component: () => import('@/views/dev/WorkspaceDemo.vue'),
        meta: { title: 'router.dev-workspace', hideInMenu: true }
      },
      {
        path: 'profile',
        name: 'DevProfile',
        component: () => import('@/views/dev/ProfileDemo.vue'),
        meta: { title: 'router.dev-profile', hideInMenu: true }
      },
      {
        path: 'creator',
        name: 'DevCreator',
        component: () => import('@/views/dev/CreatorDemo.vue'),
        meta: { title: 'router.dev-creator', hideInMenu: true }
      },
      {
        path: 'detail',
        name: 'DevDetail',
        component: () => import('@/views/dev/DetailDemo.vue'),
        meta: { title: 'router.dev-detail', hideInMenu: true }
      },
      {
        path: 'templates',
        name: 'DevTemplates',
        component: () => import('@/views/dev/TemplatesDemo.vue'),
        meta: { title: 'router.dev-templates', hideInMenu: true }
      },
      {
        path: 'chat',
        name: 'DevChat',
        component: () => import('@/views/dev/ChatDemo.vue'),
        meta: { title: 'router.dev-chat', hideInMenu: true }
      },
      {
        path: 'form',
        name: 'DevForm',
        component: () => import('@/views/dev/FormDemo.vue'),
        meta: { title: 'router.dev-form', hideInMenu: true }
      },
      {
        path: 'search',
        name: 'DevSearch',
        component: () => import('@/views/dev/SearchDemo.vue'),
        meta: { title: 'router.dev-search', hideInMenu: true }
      },
      {
        path: 'notifications',
        name: 'DevNotifications',
        component: () => import('@/views/dev/NotificationsDemo.vue'),
        meta: { title: 'router.dev-notifications', hideInMenu: true }
      },
      {
        path: 'settings',
        name: 'DevSettings',
        component: () => import('@/views/dev/SettingsDemo.vue'),
        meta: { title: 'router.dev-settings', hideInMenu: true }
      },
      {
        path: 'kanban',
        name: 'DevKanban',
        component: () => import('@/views/dev/KanbanDemo.vue'),
        meta: { title: 'router.dev-kanban', hideInMenu: true }
      },
      {
        path: 'timeline',
        name: 'DevTimeline',
        component: () => import('@/views/dev/TimelineDemo.vue'),
        meta: { title: 'router.dev-timeline', hideInMenu: true }
      },
      {
        path: 'ux-demo',
        name: 'DevUxDemo',
        component: () => import('@/views/dev/UxDemo.vue'),
        meta: { title: 'router.dev-ux-demo', hideInMenu: true }
      },
      {
        path: 'embed-test',
        name: 'DevEmbedTest',
        component: () => import('@/views/dev/EmbedTestDemo.vue'),
        meta: { title: 'router.dev-embed-test', hideInMenu: true }
      }
    ]
  }
]

const routes: RouteRecordRaw[] = isEmbedBuild ? embedRoutes : [...appRoutes, ...embedRoutes]

const router = createRouter({
  history: createWebHistory(isEmbedBuild ? '/web-embed/' : '/'),
  routes
})

// 路由守卫
router.beforeEach(async (to, _from) => {
  const title = to.meta.title as string
  if (title) {
    document.title = `${t(title)} - ${t('app.name')}`
  }

  // 嵌入模式: 解析外观参数 (brand/mode/language), 即时生效不持久化
  const embed = useEmbedParams()
  embed.applyFromRoute(to)

  // 嵌入模式 (基础级): URL 带 access_token → 写入 auth store
  const urlToken = to.query.access_token
  if (typeof urlToken === 'string' && urlToken.length > 0) {
    const auth = useAuthStore()
    auth.setToken(urlToken, undefined)
  }

  // 功能开关: 关闭的功能直接重定向到 /dev
  const feature = to.meta.feature as FeatureKey | undefined
  if (feature && !isFeatureEnabled(feature)) {
    return { path: '/dev', replace: true }
  }

  // 登录守卫: 非 public 路由需要有效 token (client_credentials)
  // embed 构建整组 public, 后端走 ASTS_USER_ID cookie 鉴权, 前端不拦截
  if (!isEmbedBuild && !to.meta.public) {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      return {
        path: '/login',
        query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined,
        replace: true,
      }
    }

    // 身份未判定 (整页刷新恢复的 token) → 先反查 /auth/me 再判域
    await auth.ensureIdentity()

    // 域守卫: 登录身份与路由域不匹配 → 重定向回所属域首页。
    // 体验层校验 — 真实鉴权由后端 @PlatformDomain/@TenantDomain 三域守卫兜底;
    // identity 为 null (身份查询失败) 时放行, 保持旧行为。
    const domain = to.meta.domain as 'platform' | 'tenant' | undefined
    if (domain && auth.identity && auth.identity !== domain) {
      return {
        path: auth.identity === 'platform' ? '/platform/dashboard' : '/tenant/tasks',
        replace: true,
      }
    }
  }

  return true
})

// 全局兜底: 任意路由变化都过一遍 dirty form registry (#2)
router.beforeEach(async (_to, _from) => {
  const dirty = dirtyFormRegistry.findDirty()
  if (dirty) {
    try {
      await ElMessageBox.confirm(
        i18n.global.t('ux.dirty-form.message'),
        i18n.global.t('ux.dirty-form.title'),
        {
          confirmButtonText: i18n.global.t('ux.dirty-form.discard'),
          cancelButtonText: i18n.global.t('ux.dirty-form.cancel'),
          type: 'warning',
        },
      )
      dirty.discard()
      return true
    } catch {
      return false
    }
  }
  return true
})

/**
 * 脏表单全局注册表 (#2 表单未保存提示 — 路由级兜底).
 * useDirtyForm 自动 register/unregister, 此处提供 findDirty 给 router guard 用.
 */
export const dirtyFormRegistry = {
  _forms: new Set<{ isDirty: () => boolean; discard: () => void }>(),

  register(form: { isDirty: () => boolean; discard: () => void }) {
    this._forms.add(form)
  },

  unregister(form: { isDirty: () => boolean; discard: () => void }) {
    this._forms.delete(form)
  },

  findDirty() {
    for (const f of this._forms) {
      if (f.isDirty()) return f
    }
    return null
  },
}

export default router
