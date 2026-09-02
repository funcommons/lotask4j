/**
 * useSidebarNavItems — 项目侧 nav 工厂 (基于 SDK 底座).
 *
 * 双域控制台菜单 (2026-09 路由域分离):
 *   - platform (tenant_id=0): 治理页 — 全量任务/Worker/类型/系统/嵌入配置/租户
 *   - tenant (tenant_id>0): 业务页 — 活跃任务/自己的任务列表+提交/模拟器/指南
 * identity 为 null 时 (claim 缺失) 按 platform 兜底, 与后端 admin API 语义一致。
 */
import { computed, type ComputedRef } from 'vue'
import type { Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Odometer, Lightning, List, Monitor, Grid,
  Setting, Files, Reading, MagicStick, Tools, Key,
} from '@element-plus/icons-vue'
import type { NavItem } from '@/components/sdk'
import type { AuthIdentity } from '@/store/auth'

/** 默认展开的 sub-menu id 列表 (无 sub-menu 时为空数组) */
export const NAV_DEFAULT_OPENEDS: string[] = []

export function useSidebarNavItems(identity: ComputedRef<AuthIdentity | null>): ComputedRef<NavItem[]> {
  const { t } = useI18n()
  return computed<NavItem[]>(() => {
    const isPlatform = (identity.value ?? 'platform') === 'platform'

    if (isPlatform) {
      return [
        {
          index: '/platform/dashboard',
          label: t('router.dashboard'),
          icon: Odometer as unknown as Component,
        },
        {
          type: 'group',
          index: 'nav-task',
          label: t('lotask.nav.task'),
          icon: List as unknown as Component,
          visible: true,
          children: [
            { index: '/platform/tasks', label: t('lotask.nav.allTasks'), icon: List as unknown as Component },
            { index: '/platform/workers', label: t('router.worker-nodes'), icon: Monitor as unknown as Component },
          ],
        },
        {
          type: 'group',
          index: 'nav-system',
          label: t('lotask.nav.system'),
          icon: Setting as unknown as Component,
          visible: true,
          children: [
            { index: '/platform/types', label: t('router.task-type-config'), icon: Grid as unknown as Component },
            { index: '/platform/settings', label: t('router.system-settings'), icon: Setting as unknown as Component },
            { index: '/platform/embed-config', label: t('router.web-embed-config'), icon: Files as unknown as Component },
            { index: '/platform/tenants', label: t('router.tenants'), icon: Key as unknown as Component },
          ],
        },
        {
          type: 'group',
          index: 'nav-help',
          label: t('lotask.nav.help'),
          icon: Reading as unknown as Component,
          visible: true,
          children: [
            { index: '/platform/guide', label: t('router.user-guide'), icon: Reading as unknown as Component },
          ],
        },
        {
          index: '/dev',
          label: t('lotask.nav.dev'),
          icon: Tools as unknown as Component,
        },
      ]
    }

    return [
      {
        index: '/tenant/active',
        label: t('router.active-tasks'),
        icon: Lightning as unknown as Component,
      },
      {
        type: 'group',
        index: 'nav-task',
        label: t('lotask.nav.task'),
        icon: List as unknown as Component,
        visible: true,
        children: [
          { index: '/tenant/tasks', label: t('router.task-list'), icon: List as unknown as Component },
          { index: '/tenant/demo', label: t('router.demo-simulator'), icon: MagicStick as unknown as Component },
        ],
      },
      {
        type: 'group',
        index: 'nav-help',
        label: t('lotask.nav.help'),
        icon: Reading as unknown as Component,
        visible: true,
        children: [
          { index: '/tenant/guide', label: t('router.user-guide'), icon: Reading as unknown as Component },
        ],
      },
      {
        index: '/dev',
        label: t('lotask.nav.dev'),
        icon: Tools as unknown as Component,
      },
    ]
  })
}
