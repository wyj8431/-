import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/features/home/WorkbenchShell.vue') },
    { path: '/account', component: () => import('@/features/account/PersonalCenter.vue'), meta: { requiresAuth: true } },
    { path: '/admin', component: () => import('@/features/admin/AdminShell.vue'), meta: { requiresAuth: true } },
    { path: '/admin/members', component: () => import('@/features/admin/AdminMembersPage.vue'), meta: { requiresAuth: true } },
    { path: '/admin/templates', component: () => import('@/features/admin/AdminTemplatesPage.vue'), meta: { requiresAuth: true } },
    { path: '/admin/template-categories', component: () => import('@/features/admin/AdminTemplateCategoriesPage.vue'), meta: { requiresAuth: true } },
    { path: '/admin/template-tags', component: () => import('@/features/admin/AdminTemplateTagsPage.vue'), meta: { requiresAuth: true } },
  ],
})
