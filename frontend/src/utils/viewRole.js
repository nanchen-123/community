// 统一登录态与"视角角色"工具
// security_auth = { token, userId, username, name, role, communityId, viewRole?, loginAt }
// - role：后端返回的真实角色，永不被前端篡改语义
// - viewRole：管理员专属的"当前视角"，仅管理员账号生效；普通账号一律忽略

const ADMIN_ROLES = ['管理员', '超级管理员']

export function isAdminRole(role) {
  return ADMIN_ROLES.includes(role)
}

export function getAuth() {
  try {
    const raw = localStorage.getItem('security_auth') || sessionStorage.getItem('security_auth')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function getRealRole() {
  return getAuth()?.role || ''
}

export function isAdmin() {
  return isAdminRole(getRealRole())
}

// 生效角色：管理员且设置了视角时返回视角角色，否则返回真实角色
export function getEffectiveRole() {
  const auth = getAuth()
  if (!auth) return ''
  if (isAdminRole(auth.role) && auth.viewRole) return auth.viewRole
  return auth.role || ''
}

// 切换视角（仅管理员账号可写）。传 '' 表示恢复管理员视角
export function setViewRole(role) {
  const auth = getAuth()
  if (!auth || !isAdminRole(auth.role)) return
  auth.viewRole = role || ''
  writeAuth(auth)
}

function writeAuth(auth) {
  const raw = JSON.stringify(auth)
  if (localStorage.getItem('security_auth')) localStorage.setItem('security_auth', raw)
  else sessionStorage.setItem('security_auth', raw)
}

// 角色 → 默认首页（按生效角色）
export function defaultPathFor(role) {
  return isAdminRole(role) ? '/admin/users' : '/home'
}
