import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../views/Home.vue'),
    redirect: '/home',
    children: [
      { path: 'home', component: () => import('../views/pages/Dashboard.vue') },
      { path: 'analytics', component: () => import('../views/pages/Analytics.vue') },
      { path: 'device', component: () => import('../views/pages/DeviceManage.vue') },
      { path: 'task', component: () => import('../views/pages/Placeholder.vue'), props: { title: '巡检任务' } },
      { path: 'alarm', component: () => import('../views/pages/Placeholder.vue'), props: { title: '告警管理' } },
      { path: 'plate', component: () => import('../views/pages/Placeholder.vue'), props: { title: '车牌识别' } },
      { path: 'resident', component: () => import('../views/pages/ResidentManage.vue') },
      {
        path: 'admin/users',
        component: () => import('../views/pages/UserManage.vue'),
        meta: { roles: ['管理员', '超级管理员'] }
      },
      {
        path: 'community',
        component: () => import('../views/pages/CommunityManage.vue'),
        meta: { roles: ['超级管理员'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 从 storage 取当前用户角色
function getCurrentRole() {
  try {
    const raw = localStorage.getItem('security_auth') || sessionStorage.getItem('security_auth')
    if (raw) return JSON.parse(raw).role || ''
  } catch {}
  return ''
}

// 路由守卫
router.beforeEach((to, from, next) => {
  if (to.meta.public || to.path === '/login') {
    next()
    return
  }
  const auth = localStorage.getItem('security_auth') || sessionStorage.getItem('security_auth')
  if (!auth) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  // 角色校验
  if (to.meta.roles && to.meta.roles.length) {
    const role = getCurrentRole()
    if (!to.meta.roles.includes(role)) {
      next('/home') // 没权限踢回首页
      return
    }
  }
  next()
})

export default router
