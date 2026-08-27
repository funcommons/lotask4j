/**
 * useSidebarNavItems — 项目侧 nav 工厂 (基于 SDK 底座).
 *
 * lotask4j 菜单分组: 任务管理 / 系统 / 帮助 / 开发者 (/dev 参考页入口).
 */
import { computed, type ComputedRef } from 'vue'
import type { Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Odometer, Lightning, List, Monitor, Grid,
  Setting, Files, Reading, MagicStick, Tools,
} from '@element-plus/icons-vue'
import type { NavItem } from '@/components/sdk'

/** 默认展开的 sub-menu id 列表 (无 sub-menu 时为空数组) */
export const NAV_DEFAULT_OPENEDS: string[] = []

export function useSidebarNavItems(): ComputedRef<NavItem[]> {
  const { t } = useI18n()
  return computed<NavItem[]>(() => [
    {
      index: '/dashboard',
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
        { index: '/active', label: t('router.active-tasks'), icon: Lightning as unknown as Component },
        { index: '/tasks', label: t('router.task-list'), icon: List as unknown as Component },
        { index: '/workers', label: t('router.worker-nodes'), icon: Monitor as unknown as Component },
        { index: '/types', label: t('router.task-type-config'), icon: Grid as unknown as Component },
      ],
    },
    {
      type: 'group',
      index: 'nav-system',
      label: t('lotask.nav.system'),
      icon: Setting as unknown as Component,
      visible: true,
      children: [
        { index: '/settings', label: t('router.system-settings'), icon: Setting as unknown as Component },
        { index: '/embed-config', label: t('router.web-embed-config'), icon: Files as unknown as Component },
      ],
    },
    {
      type: 'group',
      index: 'nav-help',
      label: t('lotask.nav.help'),
      icon: Reading as unknown as Component,
      visible: true,
      children: [
        { index: '/guide', label: t('router.user-guide'), icon: Reading as unknown as Component },
        { index: '/demo', label: t('router.demo-simulator'), icon: MagicStick as unknown as Component },
      ],
    },
    {
      index: '/dev',
      label: t('lotask.nav.dev'),
      icon: Tools as unknown as Component,
    },
  ])
}
