import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: () => import('@/features/home/WorkbenchShell.vue') }],
})
