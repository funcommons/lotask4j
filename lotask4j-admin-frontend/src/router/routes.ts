// src/router/routes.ts
import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: 'nav.pages.dashboard', icon: 'ri-dashboard-line', group: 'main' },
  },
  {
    path: '/active-tasks',
    name: 'active-tasks',
    component: () => import('@/views/ActiveTasks.vue'),
    meta: { title: 'nav.pages.activeTasks', icon: 'ri-flashlight-line', group: 'main' },
  },
  {
    path: '/tasks',
    name: 'task-list',
    component: () => import('@/views/TaskList.vue'),
    meta: { title: 'nav.pages.taskList', icon: 'ri-file-list-3-line', group: 'main' },
  },
  {
    path: '/tasks/:id',
    name: 'task-detail',
    component: () => import('@/views/TaskDetail.vue'),
    meta: { title: 'nav.pages.taskDetail', hidden: true },
  },
  {
    path: '/workers',
    name: 'worker-nodes',
    component: () => import('@/views/WorkerNodes.vue'),
    meta: { title: 'nav.pages.workerNodes', icon: 'ri-server-line', group: 'main' },
  },
  {
    path: '/types',
    name: 'task-type-config',
    component: () => import('@/views/TaskTypeConfig.vue'),
    meta: { title: 'nav.pages.taskTypeConfig', icon: 'ri-apps-2-line', group: 'main' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SystemSettings.vue'),
    meta: { title: 'nav.pages.settings', icon: 'ri-settings-3-line', group: 'config' },
  },
  {
    path: '/guide',
    name: 'user-guide',
    component: () => import('@/views/UserGuide.vue'),
    meta: { title: 'nav.pages.userGuide', icon: 'ri-book-2-line', group: 'guide' },
  },
  {
    path: '/guide/client',
    name: 'client-guide',
    component: () => import('@/views/ClientGuide.vue'),
    meta: { title: 'nav.pages.clientGuide', hidden: true },
  },
  {
    path: '/guide/worker',
    name: 'worker-guide',
    component: () => import('@/views/WorkerGuide.vue'),
    meta: { title: 'nav.pages.workerGuide', hidden: true },
  },
  {
    path: '/demo',
    name: 'demo-simulator',
    component: () => import('@/views/DemoSimulator.vue'),
    meta: { title: 'nav.pages.demoSimulator', icon: 'ri-flask-line', group: 'guide' },
  },
  {
    path: '/embed-config',
    name: 'web-embed-config',
    component: () => import('@/views/WebEmbedConfigManage.vue'),
    meta: { title: 'nav.pages.embedConfig', icon: 'ri-stack-line', group: 'config' },
  },
]
