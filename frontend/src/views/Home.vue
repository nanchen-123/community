<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  Odometer, Tickets, Warning, Cpu, Van, UserFilled,
  DataAnalysis, User, OfficeBuilding, Fold, Expand, ArrowDown, ChatDotRound
} from '@element-plus/icons-vue'
import { communityState, loadCommunities } from '../stores/community'
import request from '../api/request'

// 菜单图标（按需引入，替代全局注册全部图标）
const iconMap = {
  Odometer, Tickets, Warning, Cpu, Van,
  UserFilled, DataAnalysis, User, OfficeBuilding, ChatDotRound
}

const router = useRouter()
const route = useRoute()

// 菜单：roles 为空表示所有登录用户可见；roles / hideForRoles 用于按角色控制
const ADMIN_ROLES = ['管理员', '超级管理员']
const ALL_MENUS = [
  { path: '/home', title: '首页概览', icon: 'Odometer', hideForRoles: ADMIN_ROLES },
  { path: '/task', title: '巡检任务', icon: 'Tickets' },
  { path: '/alarm', title: '安防台账', icon: 'Warning' },
  { path: '/device', title: '设备管理', icon: 'Cpu' },
  { path: '/plate', title: '车牌识别', icon: 'Van', roles: ADMIN_ROLES },
  { path: '/resident', title: '居民管理', icon: 'UserFilled' },
  { path: '/analytics', title: '数据分析', icon: 'DataAnalysis', hideForRoles: ADMIN_ROLES },
  { path: '/admin/users', title: '用户管理', icon: 'User', roles: ADMIN_ROLES },
  { path: '/community', title: '社区管理', icon: 'OfficeBuilding', roles: ['超级管理员'] },
  { path: '/assistant', title: 'AI 助手', icon: 'ChatDotRound' }
]

// 当前登录用户
const user = ref({ username: '', role: '普通用户' })
function loadUser() {
  try {
    const raw = localStorage.getItem('security_auth') || sessionStorage.getItem('security_auth')
    if (raw) {
      const u = JSON.parse(raw)
      user.value.username = u.username || '用户'
      user.value.role = u.role || '普通用户'
    }
  } catch {}
}
onMounted(() => {
  loadUser()
  if (user.value.role === '超级管理员') loadCommunities()
  // 全局轮询待确认拍照请求，保证在任意页面都能及时提醒
  pollPhotoRequests()
  photoTimer = setInterval(pollPhotoRequests, 3000)
})

onBeforeUnmount(() => {
  if (photoTimer) clearInterval(photoTimer)
  photoTimer = null
})

// 是否超级管理员（社区筛选仅超管可见）
const isSuperAdmin = computed(() => user.value.role === '超级管理员')

// 监听 route 变化也刷一下（从登录后跳回 / 时 mounted 已过）
watch(() => route.path, () => {
  // 从登录跳转过来时，storage 刚写入但 Home.vue 的 onMounted 可能已执行过
  loadUser()
}, { immediate: true })

// 按角色过滤菜单
const menus = computed(() => {
  const role = user.value.role || '普通用户'
  return ALL_MENUS.filter(m => {
    if (m.roles && !m.roles.includes(role)) return false
    if (m.hideForRoles && m.hideForRoles.includes(role)) return false
    return true
  })
})

// 当前页面若已被当前角色隐藏（如管理员访问首页/数据分析），跳到第一个可见菜单
watch(menus, (list) => {
  if (!list.length) return
  if (!list.some(m => m.path === route.path)) {
    router.replace(list[0].path)
  }
}, { immediate: true })

// 角色 → 主题
const themeClass = computed(() => {
  const r = user.value.role || '普通用户'
  return (r === '管理员' || r === '超级管理员') ? 'admin-theme' : 'default-theme'
})

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    localStorage.removeItem('security_auth')
    sessionStorage.removeItem('security_auth')
    localStorage.removeItem('token')
    sessionStorage.removeItem('token')
    communityState.selectedId = null // 重置超管的社区筛选
    ElMessage.success('已退出')
    router.replace('/login')
  } catch {}
}

// 折叠侧边栏
const collapsed = ref(false)

// ===== 全局待确认拍照提醒（所有页面可见）=====
const PHOTO_TYPE_LABEL = { person: '人员识别', flame: '火焰识别', plate: '车牌识别' }
const photoPending = ref([])
const notifiedIds = new Set()
let photoTimer = null
let photoFailures = 0

const photoTipSub = computed(() => {
  const first = photoPending.value[0]
  if (!first) return ''
  return `${first.waypoint || '拍照点'} · ${PHOTO_TYPE_LABEL[first.detect_type] || '识别'}`
})

function notifyPhotoRequest(item) {
  ElNotification({
    title: '有新的拍照请求待确认',
    message: `${item.waypoint || '拍照点'} · ${PHOTO_TYPE_LABEL[item.detect_type] || '识别'}，点击前往首页处理`,
    type: 'warning',
    duration: 10000,
    position: 'bottom-right',
    onClick: () => router.push('/home'),
  })
}

async function pollPhotoRequests() {
  try {
    const res = await request.get('/photo/requests')
    const list = res?.records || []
    photoPending.value = list
    photoFailures = 0
    list.forEach(item => {
      const id = item.request_id
      if (!id || notifiedIds.has(id)) return
      notifiedIds.add(id)
      notifyPhotoRequest(item)
    })
  } catch (e) {
    // 接口异常时最多重试 3 次后停止轮询，避免持续弹错
    photoFailures++
    if (photoFailures >= 3 && photoTimer) {
      clearInterval(photoTimer)
      photoTimer = null
    }
  }
}

</script>

<template>
  <!-- 根元素根据角色挂不同主题 class -->
  <el-container :class="['home-wrap', themeClass]">
    <!-- 侧边栏 -->
    <el-aside class="aside" :width="collapsed ? '64px' : '210px'">
      <div class="logo">
        <img src="/src/assets/logo.png" class="logo-img" alt="logo" />
        <span v-if="!collapsed" class="logo-text">安防巡检</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        router
        background-color="transparent"
        text-color="var(--text-sub)"
        active-text-color="var(--primary)"
      >
        <el-menu-item
          v-for="m in menus"
          :key="m.path"
          :index="m.path"
        >
          <el-icon><component :is="iconMap[m.icon]" /></el-icon>
          <template #title>{{ m.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧 -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <span class="header-title">社区安防智能巡检系统</span>
        </div>
        <div class="header-right">
          <!-- 超级管理员：社区筛选（全部社区 / 单个社区） -->
          <el-select
            v-if="isSuperAdmin"
            v-model="communityState.selectedId"
            placeholder="全部社区"
            clearable
            class="community-select"
          >
            <el-option label="全部社区" :value="null" />
            <el-option
              v-for="c in communityState.list"
              :key="c.communityId"
              :label="c.communityName"
              :value="c.communityId"
            />
          </el-select>
          <span class="user-role">{{ user.role }}</span>
          <el-dropdown @command="cmd => cmd === 'logout' && onLogout()">
            <div class="user-info">
              <el-avatar :size="32" class="avatar" />
              <span class="username">{{ user.username }}</span>
              <el-icon :size="14"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 子页面路由出口 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 全局待确认拍照悬浮提示（首页已用卡片展示，其余页面悬浮提醒） -->
    <div
      v-if="photoPending.length && route.path !== '/home'"
      class="photo-float"
      @click="router.push('/home')"
    >
      <span class="photo-float-pulse"></span>
      <div class="photo-float-text">
        <span class="photo-float-title">待确认拍照 {{ photoPending.length }}</span>
        <span class="photo-float-sub">{{ photoTipSub }}</span>
      </div>
      <span class="photo-float-go">去处理 →</span>
    </div>
  </el-container>
</template>

<style scoped>
/* ====== 主题 CSS 变量（同色系深浅差异） ====== */

/* 普通用户 —— 明蓝青 */
.home-wrap.default-theme {
  --primary: #006080;
  --primary-light: #0080A0;
  --primary-soft: #00A0C0;
  --page-bg: #F0F6F7;
  --card-bg: #FFFFFF;
  --text-main: #1E3A40;
  --text-sub: #507080;
  --text-soft: #8090A0;
  --border: #D0E0E4;
  --border-deep: #E0EAEB;
  --active-bg: #E8F4F8;
  --hover-bg: #F4F9FA;
  --header-bg: #FFFFFF;
  --aside-bg: #FFFFFF;
  --stat-primary: #006080;
  --stat-secondary: #0080A0;
  --stat-tertiary: #00A0C0;
  --shadow: rgba(15, 30, 40, 0.05);
  --shadow-hover: rgba(15, 30, 40, 0.08);
}

/* 管理员 —— 深青蓝（同色系加深一档） */
.home-wrap.admin-theme {
  --primary: #004C66;
  --primary-light: #005C7A;
  --primary-soft: #007A99;
  --page-bg: #E4EDF1;
  --card-bg: #FFFFFF;
  --text-main: #102A35;
  --text-sub: #3E5F6C;
  --text-soft: #5C7F8C;
  --border: #9CC1CC;
  --border-deep: #B5CCD4;
  --active-bg: #C8DEE5;
  --hover-bg: #D8E7EC;
  --header-bg: #F2F7F9;
  --aside-bg: #FFFFFF;
  --stat-primary: #004C66;
  --stat-secondary: #005C7A;
  --stat-tertiary: #007A99;
  --shadow: rgba(0, 40, 60, 0.08);
  --shadow-hover: rgba(0, 40, 60, 0.12);
}

/* ====== 基础布局 ====== */
.home-wrap {
  height: 100vh;
  background: var(--page-bg);
}

/* 侧边栏 */
.aside {
  background: var(--aside-bg);
  border-right: 1px solid var(--border-deep);
  transition: width 0.25s;
  overflow: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid var(--border-deep);
  overflow: hidden;
}
.logo-img {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}
.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: var(--primary);
  white-space: nowrap;
  letter-spacing: 2px;
}
.aside :deep(.el-menu) {
  border-right: none;
  padding: 8px 10px;
}
.aside :deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 4px;
  height: 44px;
  line-height: 44px;
}
.aside :deep(.el-menu-item:hover) {
  background: var(--hover-bg);
}
.aside :deep(.el-menu-item.is-active) {
  background: var(--active-bg);
  color: var(--primary);
  font-weight: 600;
}

/* 顶栏 */
.header {
  height: 60px;
  background: var(--header-bg);
  border-bottom: 1px solid var(--border-deep);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.collapse-btn {
  cursor: pointer;
  color: var(--text-sub);
  transition: color 0.2s;
}
.collapse-btn:hover {
  color: var(--primary);
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
  letter-spacing: 1px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.community-select {
  width: 160px;
}
.community-select :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--border) inset;
}
.user-role {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  background: var(--active-bg);
  color: var(--primary);
  font-weight: 500;
}
.admin-theme .user-role {
  background: #E0EDF2;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 8px;
  transition: background 0.2s;
}
.user-info:hover {
  background: var(--hover-bg);
}
.avatar {
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
}
.username {
  font-size: 14px;
  color: var(--text-main);
  font-weight: 500;
}

/* 主内容 */
.main {
  padding: 24px;
  overflow-y: auto;
  background: var(--page-bg);
}

/* ====== 子页面里 Element Plus 组件主题覆盖 ====== */
:deep(.el-button--primary) {
  --el-button-bg-color: var(--primary);
  --el-button-border-color: var(--primary);
  --el-button-hover-bg-color: var(--primary-light);
  --el-button-hover-border-color: var(--primary-light);
  --el-button-active-bg-color: var(--primary);
  --el-button-active-border-color: var(--primary);
}
:deep(.el-button--primary.is-plain) {
  --el-button-bg-color: var(--active-bg);
  --el-button-border-color: var(--primary);
  --el-button-hover-bg-color: var(--primary);
  --el-button-text-color: var(--primary);
}
:deep(.el-pagination.is-background .el-pager li.is-active) {
  background: var(--primary);
}
:deep(.el-tag.el-tag--info) {
  --el-tag-bg-color: var(--active-bg);
  --el-tag-border-color: var(--active-bg);
  --el-tag-text-color: var(--primary);
}
:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--border) inset;
}
:deep(.el-input__wrapper.is-focus),
:deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--primary) inset;
}

/* ====== 全局待确认拍照悬浮提示 ====== */
.photo-float {
  position: fixed;
  right: 24px;
  bottom: 28px;
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 12px;
  background: #FFF9EF;
  border: 1px solid #F5C97A;
  box-shadow: 0 8px 24px rgba(180, 120, 20, 0.18);
  cursor: pointer;
  transition: transform .15s, box-shadow .15s;
}
.photo-float:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(180, 120, 20, 0.26);
}
.photo-float-pulse {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #F59E0B;
  flex-shrink: 0;
  animation: photoPulse 1.4s ease-in-out infinite;
}
@keyframes photoPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.6); }
  50% { box-shadow: 0 0 0 7px rgba(245, 158, 11, 0); }
}
.photo-float-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.photo-float-title {
  font-size: 13px;
  font-weight: 600;
  color: #8A5200;
}
.photo-float-sub {
  font-size: 11px;
  color: #A5711F;
}
.photo-float-go {
  font-size: 12px;
  color: #B26A00;
  font-weight: 600;
  padding-left: 6px;
  border-left: 1px solid #F5C97A;
}

/* 响应式 */
@media (max-width: 600px) {
  .username, .user-role { display: none; }
  .photo-float { right: 12px; bottom: 12px; padding: 10px 12px; }
  .photo-float-go { display: none; }
}
</style>
