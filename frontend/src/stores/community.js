import { reactive } from 'vue'
import request from '../api/request'

// 超级管理员的社区筛选状态（null = 全部社区）
export const communityState = reactive({
  list: [],
  selectedId: null,
  loaded: false
})

// 加载社区列表（超级管理员顶栏筛选、用户管理、社区管理共用）
export async function loadCommunities(force = false) {
  if (communityState.loaded && !force) return communityState.list
  try {
    const res = await request.get('/community/page', { params: { pageNum: 1, pageSize: 500 } })
    communityState.list = res.records || []
    communityState.loaded = true
  } catch {}
  return communityState.list
}

// 社区 ID → 名称
export function communityName(id) {
  if (!id) return '--'
  const c = communityState.list.find(item => item.communityId === id)
  return c ? c.communityName : '#' + id
}

// 读取当前登录信息（role 为生效身份，dbRole 为账号真实角色）
export function getAuth() {
  try {
    const raw = localStorage.getItem('security_auth') || sessionStorage.getItem('security_auth')
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}
