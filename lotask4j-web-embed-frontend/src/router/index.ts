import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory('/web-embed/'),
  routes: [
    { path: '/', redirect: '/task-list' },
    {
      path: '/task-list',
      name: 'TaskList',
      component: () => import('@/views/TaskList.vue')
    },
    {
      path: '/task-detail',
      name: 'TaskDetail',
      component: () => import('@/views/TaskDetail.vue')
    },
    {
      path: '/task-card',
      name: 'TaskCard',
      component: () => import('@/views/TaskCard.vue')
    }
  ]
})

export default router
