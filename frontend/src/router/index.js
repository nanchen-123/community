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
      { path: 'task', component: () => import('../views/pages/TaskEntry.vue') },
      { path: 'alarm', component: () => import('../views/pages/AlarmEntry.vue') },
      {
        path: 'plate',
        component: () => import('../views/pages/PlateManage.vue'),
        meta: { roles: ['管理员', '超级管理员'] }
      },
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

// 读取登录态（localStorage 优先，其次 sessionStorage）
function readAuth() {
  try {
    const raw = localStorage.getItem('security_auth') || sessionStorage.getItem('security_auth')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// 解析 JWT 的 exp 判断是否过期，非标准 token 交给后端 401 兜底
function isTokenExpired(token) {
  if (!token) return true
  try {
    const part = token.split('.')[1]
    if (!part) return false
    const payload = JSON.parse(atob(part.replace(/-/g, '+').replace(/_/g, '/')))
    return payload.exp ? payload.exp * 1000 <= Date.now() : false
  } catch {
    return false
  }
}

function clearAuth() {
  localStorage.removeItem('security_auth')
  sessionStorage.removeItem('security_auth')
  localStorage.removeItem('token')
  sessionStorage.removeItem('token')
}

// 已登录时按角色返回默认首页
function defaultHome(role) {
  return role === '普通用户' ? '/home' : '/admin/users'
}

// 路由守卫：除登录页外，未登录/登录失效一律回到登录页
router.beforeEach((to, from, next) => {
  const auth = readAuth()
  const loggedIn = !!auth && !!auth.token && !isTokenExpired(auth.token)

  if (to.path === '/login') {
    // 已登录时不再重复展示登录页
    if (loggedIn) {
      next(defaultHome(auth.role))
      return
    }
    next()
    return
  }

  if (to.meta.public) {
    next()
    return
  }

  if (!loggedIn) {
    clearAuth()
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 角色校验
  if (to.meta.roles && to.meta.roles.length && !to.meta.roles.includes(auth.role || '')) {
    next(defaultHome(auth.role))
    return
  }

  next()
})

export default router
