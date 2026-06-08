import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import BasicLayout from '../layouts/BasicLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '首页仪表盘', permission: 'dashboard:view' } },
      { path: 'alerts', name: 'AlertList', component: () => import('../views/AlertList.vue'), meta: { title: '告警管理', permissionGroup: 'alert' } },
      { path: 'tickets', name: 'TicketList', component: () => import('../views/TicketList.vue'), meta: { title: '工单管理', permissionGroup: 'ticket' } },
      { path: 'tickets/:id', name: 'TicketDetail', component: () => import('../views/TicketDetail.vue'), meta: { title: '工单详情', permissionGroup: 'ticket' } },
      { path: 'diagnoses', name: 'DiagnosisList', component: () => import('../views/DiagnosisList.vue'), meta: { title: '大模型诊断', permissionGroup: 'diagnosis' } },
      { path: 'diagnoses/:id', name: 'DiagnosisDetail', component: () => import('../views/DiagnosisDetail.vue'), meta: { title: '大模型诊断详情', permissionGroup: 'diagnosis' } },
      { path: 'knowledge', name: 'KnowledgeList', component: () => import('../views/KnowledgeList.vue'), meta: { title: '知识库', permissionGroup: 'knowledge' } },
      { path: 'system/users', name: 'UserList', component: () => import('../views/UserList.vue'), meta: { title: '用户管理', permission: 'system:user' } },
      { path: 'system/roles', name: 'RoleList', component: () => import('../views/RoleList.vue'), meta: { title: '角色管理', permission: 'system:role' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

function canAccess(authStore, route) {
  const permissions = authStore.permissions
  if (route.meta.permission) {
    return permissions.includes(route.meta.permission)
  }
  if (route.meta.permissionGroup) {
    const group = route.meta.permissionGroup
    return permissions.includes(`${group}:manage`) || permissions.includes(`${group}:view`)
  }
  return true
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (!to.meta.public && !authStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.path === '/login' && authStore.isLogin) {
    return '/dashboard'
  }

  if (!to.meta.public && authStore.isLogin && !authStore.permissions.length) {
    await authStore.loadCurrentUser()
  }

  if (!to.meta.public && !canAccess(authStore, to)) {
    return '/dashboard'
  }

  return true
})

export default router
