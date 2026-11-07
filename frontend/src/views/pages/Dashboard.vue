<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import request from '../../api/request'
import { getAuth, communityState, loadCommunities } from '../../stores/community'

echarts.use([BarChart, PieChart, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const chartRef = ref(null)
let chartInstance = null

const auth = getAuth()
const isSuperAdmin = auth.role === '超级管理员'
const myCommunityId = auth.communityId || null

const robot = reactive({
  online: false,
  location: '',
  task: ''
})

const stats = reactive({
  alarmTotal: 0,
  plateTotal: 0,
  inspect: 0
})
const statusStats = reactive({ 未处理: 0, 已处理: 0, 已忽略: 0 })
const chartDim = ref('type')
const chartType = ref('bar')

const captureImage = ref('')
const captureMeta = reactive({ title: '', subtitle: '', time: '' })
const captureDisplayMeta = computed(() => ({
  title: captureMeta.title,
  subtitle: captureMeta.subtitle,
  time: captureMeta.time
}))

// 告警类型与级别
const TYPE_LABEL = { 人员异常: '人员异常', 消防安全: '消防安全', 其他: '其他' }
const TYPE_LEVEL = { 消防安全: '严重', 人员异常: '警告', 其他: '一般' }
const levelClassMap = { 严重: 'danger', 警告: 'warn', 一般: 'normal', 高: 'danger', 中: 'warn', 低: 'normal' }

const alarmList = ref([])

// 会话级已读记录：按用户隔离，关闭浏览器后自动清空，
// 未处理告警在新会话中重新提醒，且不会像 localStorage 一样跨会话永久累积
function _readKey() {
  const a = getAuth()
  const id = a?.username || a?.userName || a?.userId || 'anonymous'
  return 'security_alarm_read_' + String(id).replace(/[^\w-]/g, '_')
}
function _loadReadIds() {
  try {
    return new Set(JSON.parse(sessionStorage.getItem(_readKey()) || '[]'))
  } catch {
    return new Set()
  }
}
function _saveReadIds(set) {
  try { sessionStorage.setItem(_readKey(), JSON.stringify([...set])) } catch {}
}
const readIds = ref(_loadReadIds())

const visibleAlarmList = computed(() => {
  const set = readIds.value
  return alarmList.value
    .filter(a => !set.has(Number(a.id)) && !set.has(String(a.id)))
    .slice(0, 10)
})

const unreadCount = computed(() => visibleAlarmList.value.length)

// ===== 待确认拍照请求 =====
const photoRequests = ref([])
const photoBusy = ref('')
const PHOTO_TYPE_LABEL = { person: '人员识别', flame: '火焰识别', plate: '车牌识别' }
let photoPollFailures = 0

function photoTypeLabel(type) {
  return PHOTO_TYPE_LABEL[type] || type || '识别'
}

function fmtPhotoTime(value) {
  if (!value) return ''
  const d = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function loadPhotoRequests() {
  try {
    const res = await request.get('/photo/requests')
    photoRequests.value = res?.records || []
    photoPollFailures = 0
  } catch (e) {
    // 接口异常（如后端未更新）时最多重试 3 次后停止轮询，避免持续弹错
    photoPollFailures++
    if (photoPollFailures >= 3 && photoPollTimer) {
      clearInterval(photoPollTimer)
      photoPollTimer = null
    }
    console.warn('待确认拍照请求加载失败:', e?.message || e)
  }
}

async function decidePhoto(item, confirm) {
  if (photoBusy.value) return
  photoBusy.value = item.request_id
  try {
    await request.post(`/photo/${item.request_id}/${confirm ? 'confirm' : 'skip'}`)
  } catch (e) {
    // 409 表示已被其他人处理、404 表示已失效，统一刷新列表即可
    console.warn('拍照请求处理失败:', e)
  } finally {
    photoBusy.value = ''
    await loadPhotoRequests()
    loadData()
  }
}

function levelClass(level) {
  return levelClassMap[level] || 'normal'
}

// 重新赋值 Set 以触发 computed 响应式更新
function _commitReadIds(set) {
  readIds.value = new Set(set)
  _saveReadIds(readIds.value)
}

function markAllRead() {
  const set = readIds.value
  visibleAlarmList.value.forEach(a => {
    set.add(Number(a.id))
    set.add(String(a.id))
  })
  _commitReadIds(set)
}

function goRecord(item) {
  const set = readIds.value
  set.add(Number(item.id))
  set.add(String(item.id))
  _commitReadIds(set)
  router.push('/alarm')
}

// 数据范围：超管看全部（跟随顶栏社区筛选），管理员/普通用户只看所属社区
function scopeParams() {
  if (isSuperAdmin) {
    return communityState.selectedId ? { communityId: communityState.selectedId } : {}
  }
  return myCommunityId ? { communityId: myCommunityId } : {}
}

// 本周范围：周一 00:00:00 ~ 周日 23:59:59
function weekRange() {
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const fmtDate = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  // getDay(): 0=周日，转换为「距离本周一的天数」
  const offset = (now.getDay() + 6) % 7
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset)
  const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6)
  return {
    startTime: `${fmtDate(monday)} 00:00:00`,
    endTime: `${fmtDate(sunday)} 23:59:59`,
    startDate: fmtDate(monday),
    endDate: fmtDate(sunday)
  }
}

function fmtTime(value) {
  if (!value) return ''
  const s = String(value).replace('T', ' ')
  return s.length >= 16 ? s.slice(5, 16) : s
}

const categoryData = ['人员异常', '消防安全', '其他']
const statusCategory = [
  { key: '未处理', label: '未处理', color: '#F59E0B' },
  { key: '已处理', label: '已处理', color: '#0EA5E9' },
  { key: '已忽略', label: '已忽略', color: '#80A0A8' }
]
const barData = ref([0, 0, 0])
const statusData = ref([0, 0, 0])

async function loadData() {
  const scope = scopeParams()
  const { startTime, endTime, startDate, endDate } = weekRange()
  try {
    const [weekAlarms, pendingAlarms, recentAlarms, plates, tasks, robotOverview] = await Promise.all([
      request.get('/alarm/page', { params: { pageNum: 1, pageSize: 200, ...scope, startTime, endTime } }),
      request.get('/alarm/page', { params: { pageNum: 1, pageSize: 10, handleStatus: '未处理', ...scope } }),
      request.get('/alarm/page', { params: { pageNum: 1, pageSize: 20, ...scope } }),
      request.get('/license-plate/page', { params: { pageNum: 1, pageSize: 200, ...scope } }),
      request.get('/task/page', { params: { pageNum: 1, pageSize: 200, taskStatus: '已完成', ...scope } }),
      request.get('/robot-status/overview')
    ])

    const weekRecords = weekAlarms?.records || []
    stats.alarmTotal = weekAlarms?.total ?? weekRecords.length

    const inWeek = value => {
      const d = String(value || '').slice(0, 10)
      return d >= startDate && d <= endDate
    }

    const plateRecords = plates?.records || []
    stats.plateTotal = plateRecords.filter(p => inWeek(p.createTime)).length

    const taskRecords = tasks?.records || []
    stats.inspect = taskRecords.filter(t => inWeek(t.endTime)).length

    // 处理状态分布
    statusStats.未处理 = 0
    statusStats.已处理 = 0
    statusStats.已忽略 = 0
    weekRecords.forEach(a => {
      const key = a.handleStatus || '未处理'
      if (statusStats[key] !== undefined) statusStats[key]++
    })
    statusData.value = statusCategory.map(s => statusStats[s.key] || 0)

    // 类型分布
    const typeCount = {}
    weekRecords.forEach(a => {
      const t = TYPE_LABEL[a.alarmType] ? a.alarmType : '其他'
      typeCount[t] = (typeCount[t] || 0) + 1
    })
    barData.value = categoryData.map(t => typeCount[t] || 0)

    // 实时告警列表（未处理）
    alarmList.value = (pendingAlarms?.records || []).map(a => ({
      id: a.alarmId,
      level: a.level || TYPE_LEVEL[a.alarmType] || '一般',
      type: TYPE_LABEL[a.alarmType] || a.alarmType || '其他',
      desc: a.remark || a.location || '暂无描述',
      time: fmtTime(a.findTime)
    }))

    // 抓拍画面：最近一条带图片的告警
    const withImg = (recentAlarms?.records || []).find(a => a.imgUrl)
    if (withImg) {
      captureImage.value = withImg.imgUrl
      captureMeta.title = TYPE_LABEL[withImg.alarmType] || withImg.alarmType || '抓拍快照'
      captureMeta.subtitle = withImg.location || ''
      captureMeta.time = fmtTime(withImg.findTime)
    } else {
      captureImage.value = ''
      captureMeta.title = ''
      captureMeta.subtitle = ''
      captureMeta.time = ''
    }

    // 机器人状态
    const robots = robotOverview?.robots || []
    const r = robots.find(item => item.online) || robots[0]
    robot.online = !!r?.online
    if (r) {
      const pos = r.current_pose?.position
      robot.location = r.current_waypoint
        || (pos ? `${Number(pos.x).toFixed(2)}, ${Number(pos.y).toFixed(2)}` : '--')
      robot.task = r.current_task_name || r.current_task_id || '待命'
    }

    if (chartInstance) renderChart()
  } catch (e) {
    console.error('首页数据加载失败:', e)
  }
}

function initChart() {
  if (!chartRef.value) return
  const rect = chartRef.value.getBoundingClientRect()
  if (rect.width < 10 || rect.height < 10) return
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
  chartInstance = echarts.init(chartRef.value)
  renderChart()
}

function renderChart() {
  if (!chartInstance) return
  const isType = chartDim.value === 'type'
  const labels = isType ? categoryData : statusCategory.map(s => s.label)
  const dataArr = isType ? barData.value : statusData.value
  const colors = isType
    ? ['#00A0C0', '#EF4444', '#80A0A8']
    : statusCategory.map(s => s.color)

  let option
  if (chartType.value === 'bar') {
    option = {
      grid: { top: 30, left: 40, right: 20, bottom: 40 },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'category',
        data: labels,
        axisLabel: { color: 'var(--text-sub)', fontSize: 12, interval: 0 }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: 'var(--text-sub)' },
        splitLine: { lineStyle: { color: '#E8EDEF' } }
      },
      series: [{
        type: 'bar',
        barWidth: '45%',
        data: dataArr.map((v, i) => ({
          value: v,
          itemStyle: { color: colors[i], borderRadius: [4, 4, 0, 0] }
        })),
        label: { show: true, position: 'top', color: '#406070', fontSize: 12, fontWeight: 600 }
      }]
    }
  } else {
    const pieData = labels.map((name, i) => ({
      name,
      value: dataArr[i],
      itemStyle: { color: colors[i] }
    }))
    option = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: {
        bottom: 5,
        left: 'center',
        textStyle: { color: 'var(--text-sub)', fontSize: 12 },
        itemWidth: 10,
        itemHeight: 10
      },
      series: [{
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, color: '#406070', fontSize: 12, formatter: '{b}\n{c} ({d}%)' },
        emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } },
        data: pieData
      }]
    }
  }
  chartInstance.setOption(option, true)
}

function resizeChart() {
  chartInstance && chartInstance.resize()
}

let chartRetryTimer = null
let pollTimer = null
let photoPollTimer = null

onMounted(async () => {
  if (isSuperAdmin) {
    await loadCommunities()
  }
  await loadData()
  await nextTick()
  const tryInit = (count = 0) => {
    initChart()
    if (!chartInstance && count < 10) {
      chartRetryTimer = setTimeout(() => tryInit(count + 1), 100)
    }
  }
  tryInit()
  window.addEventListener('resize', resizeChart)
  pollTimer = setInterval(loadData, 30000)
  // 拍照请求需要快速响应，单独 3 秒轮询
  await loadPhotoRequests()
  photoPollTimer = setInterval(loadPhotoRequests, 3000)
})

onBeforeUnmount(() => {
  if (chartRetryTimer) clearTimeout(chartRetryTimer)
  if (pollTimer) clearInterval(pollTimer)
  if (photoPollTimer) clearInterval(photoPollTimer)
  window.removeEventListener('resize', resizeChart)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

// 超级管理员切换顶栏社区时刷新
watch(() => communityState.selectedId, () => {
  loadData()
})
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-title">首页概览</div>
      <div class="page-sub">
        <svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="7" height="9"></rect>
          <rect x="14" y="3" width="7" height="5"></rect>
          <rect x="14" y="12" width="7" height="9"></rect>
          <rect x="3" y="16" width="7" height="5"></rect>
        </svg>
        <span>社区运行状态与关键指标一览</span>
      </div>
    </div>

    <div class="home-page">
      <div class="home-main">
        <div class="row row-top">
          <div class="card status-card">
            <div class="card-title">
              <span class="title-dot"></span>
              <span>机器人状态</span>
            </div>
            <div class="robot-status">
              <div class="status-line">
                <span class="status-dot" :class="robot.online ? 'online' : 'offline'"></span>
                <span class="status-text">{{ robot.online ? '在线' : '离线' }}</span>
                <span class="status-tag" :class="robot.online ? 'online' : 'offline'">
                  {{ robot.online ? '运行中' : '已断开' }}
                </span>
              </div>
              <div class="info-line">
                <span class="info-label">当前位置</span>
                <span class="info-value">{{ robot.location || '--' }}</span>
              </div>
              <div class="info-line">
                <span class="info-label">执行任务</span>
                <span class="info-value">{{ robot.task || '待命' }}</span>
              </div>
            </div>
          </div>

          <div class="card stat-card">
            <div class="card-title">
              <span class="title-dot"></span>
              <span>本周数据统计</span>
            </div>
            <div class="stat-list">
              <div class="stat-item">
                <div class="stat-icon warn">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                    <line x1="12" y1="9" x2="12" y2="13"/>
                    <line x1="12" y1="17" x2="12.01" y2="17"/>
                  </svg>
                </div>
                <div class="stat-num warn">{{ stats.alarmTotal }}</div>
                <div class="stat-label">本周告警</div>
              </div>
              <div class="stat-item">
                <div class="stat-icon danger">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="1" y="3" width="15" height="13"/>
                    <path d="M16 8h4l3 3v5h-7V8z"/>
                    <circle cx="5.5" cy="18.5" r="2.5"/>
                    <circle cx="18.5" cy="18.5" r="2.5"/>
                  </svg>
                </div>
                <div class="stat-num danger">{{ stats.plateTotal }}</div>
                <div class="stat-label">车牌识别</div>
              </div>
              <div class="stat-item">
                <div class="stat-icon success">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                    <polyline points="22 4 12 14.01 9 11.01"/>
                  </svg>
                </div>
                <div class="stat-num success">{{ stats.inspect }}</div>
                <div class="stat-label">巡检完成</div>
              </div>
            </div>
            <div class="status-capsules">
              <div class="capsule pending">
                <div class="capsule-dot"></div>
                <div class="capsule-body">
                  <div class="capsule-num">{{ statusStats.未处理 }}</div>
                  <div class="capsule-label">未处理</div>
                </div>
              </div>
              <div class="capsule resolved">
                <div class="capsule-dot"></div>
                <div class="capsule-body">
                  <div class="capsule-num">{{ statusStats.已处理 }}</div>
                  <div class="capsule-label">已处理</div>
                </div>
              </div>
              <div class="capsule closed">
                <div class="capsule-dot"></div>
                <div class="capsule-body">
                  <div class="capsule-num">{{ statusStats.已忽略 }}</div>
                  <div class="capsule-label">已忽略</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="row row-bottom">
          <div class="card image-card">
            <div class="card-title">
              <span class="title-dot"></span>
              <span>机器人抓拍画面</span>
              <span class="live-tag" :class="{ offline: !robot.online }">
                <span class="live-dot"></span>
                {{ robot.online ? 'LIVE' : '离线' }}
              </span>
            </div>
            <div class="image-wrap">
              <img v-if="captureImage" :src="captureImage" alt="抓拍画面" />
              <div v-else class="placeholder">
                <div class="ph-icon">📷</div>
                <div class="ph-text">{{ robot.online ? '等待画面推送...' : '机器人离线，暂无画面' }}</div>
              </div>
            </div>
            <div v-if="captureDisplayMeta.title || captureDisplayMeta.subtitle" class="capture-meta">
              <div class="cm-title" :class="{ unknown: !captureDisplayMeta.title }">
                {{ captureDisplayMeta.title || '抓拍快照' }}
                <span v-if="captureDisplayMeta.time" class="cm-time">{{ captureDisplayMeta.time }}</span>
              </div>
              <div v-if="captureDisplayMeta.subtitle" class="cm-sub">{{ captureDisplayMeta.subtitle }}</div>
            </div>
          </div>

          <div class="card chart-card">
            <div class="card-title">
              <span class="title-dot"></span>
              <span>告警分布统计</span>
              <div class="chart-switches">
                <div class="seg">
                  <button :class="{ active: chartDim === 'type' }" @click="chartDim = 'type'; renderChart()">按类型</button>
                  <button :class="{ active: chartDim === 'status' }" @click="chartDim = 'status'; renderChart()">按状态</button>
                </div>
                <div class="seg">
                  <button :class="{ active: chartType === 'bar' }" @click="chartType = 'bar'; renderChart()">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="12" width="4" height="9"/><rect x="10" y="6" width="4" height="15"/><rect x="17" y="3" width="4" height="18"/></svg>
                  </button>
                  <button :class="{ active: chartType === 'pie' }" @click="chartType = 'pie'; renderChart()">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21.21 15.89A10 10 0 1 1 8 2.83"/><path d="M22 12A10 10 0 0 0 12 2v10z"/></svg>
                  </button>
                </div>
              </div>
            </div>
            <div ref="chartRef" class="chart-wrap"></div>
          </div>
        </div>
      </div>

      <div class="home-side">
        <div class="card photo-card">
          <div class="card-title">
            <span class="title-dot"></span>
            <span>待确认拍照</span>
            <span v-if="photoRequests.length" class="photo-badge">{{ photoRequests.length }}</span>
          </div>
          <div class="photo-list">
            <div v-for="item in photoRequests" :key="item.request_id" class="photo-item">
              <div class="photo-head">
                <span class="photo-waypoint">{{ item.waypoint || '未知航点' }}</span>
                <span class="photo-type">{{ photoTypeLabel(item.detect_type) }}</span>
              </div>
              <div class="photo-sub">任务 {{ item.task_id || '--' }} · 小车已到位</div>
              <div class="photo-time">{{ fmtPhotoTime(item.created_at) }}</div>
              <div class="photo-actions">
                <button class="btn-photo" :disabled="!!photoBusy" @click="decidePhoto(item, true)">
                  {{ photoBusy === item.request_id ? '处理中…' : '立即拍照' }}
                </button>
                <button class="btn-skip" :disabled="!!photoBusy" @click="decidePhoto(item, false)">跳过</button>
              </div>
            </div>
            <div v-if="photoRequests.length === 0" class="photo-empty">暂无待确认拍照</div>
          </div>
        </div>

        <div class="card alarm-card">
          <div class="card-title">
            <span class="title-dot"></span>
            <span>实时告警</span>
            <div class="alarm-actions">
              <router-link v-if="unreadCount > 0" to="/alarm" class="unread-badge">
                {{ unreadCount > 99 ? '99+' : unreadCount }}
              </router-link>
              <button class="btn-read" @click="markAllRead">一键已读</button>
            </div>
          </div>
          <div class="alarm-list">
            <div
              v-for="item in visibleAlarmList"
              :key="item.id"
              class="alarm-item unread"
              @click="goRecord(item)"
            >
              <div class="alarm-level" :class="levelClass(item.level)">{{ item.level }}</div>
              <div class="alarm-body">
                <div class="alarm-type">{{ item.type }}</div>
                <div class="alarm-desc">{{ item.desc }}</div>
                <div class="alarm-time">{{ item.time }}</div>
              </div>
              <span class="red-dot"></span>
            </div>
            <div v-if="visibleAlarmList.length === 0" class="empty-tip">暂无未读告警</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  gap: 12px;
  min-height: 100%;
  box-sizing: border-box;
}

.home-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home-side {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card {
  display: block;
  background: #FFFFFF;
  border-radius: 10px;
  border: 1px solid #E8EDEF;
  padding: 14px;
  box-sizing: border-box;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0;
}

.stat-card:hover {
  transform: none;
}

.row {
  display: flex;
  gap: 12px;
}
.row > .card {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.title-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  flex-shrink: 0;
}

.status-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.online { background: #22C55E; }
.status-dot.offline { background: #80A0A8; }
.status-text { font-weight: 600; color: var(--text-main); font-size: 14px; }
.status-tag {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 8px;
}
.status-tag.online { color: #16A34A; background: rgba(34, 197, 94, 0.1); }
.status-tag.offline { color: var(--text-sub); background: #F0F4F5; }

.info-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 5px 0;
  border-top: 1px solid #F0F4F5;
  font-size: 13px;
}
.info-label { color: var(--text-sub); }
.info-value { color: var(--text-main); font-weight: 500; text-align: right; }

.stat-list {
  display: flex;
  gap: 10px;
  width: 100%;
  margin-bottom: 10px;
}
.stat-item {
  flex: 1 1 0;
  min-width: 0;
  text-align: center;
  padding: 10px 6px;
  border-radius: 8px;
  background: #F4F9FA;
}
.stat-icon {
  width: 32px;
  height: 32px;
  margin: 0 auto 6px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.stat-icon.warn { background: #F59E0B; }
.stat-icon.danger { background: #EF4444; }
.stat-icon.success { background: #10B981; }
.stat-num { font-size: 22px; font-weight: 700; line-height: 1.1; }
.stat-num.warn { color: #F59E0B; }
.stat-num.danger { color: #EF4444; }
.stat-num.success { color: #10B981; }
.stat-label { font-size: 12px; color: var(--text-sub); margin-top: 3px; }

.status-capsules {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid #F0F4F5;
}
.capsule {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 8px;
  background: #F4F9FA;
}
.capsule-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
.capsule.pending { color: #F59E0B; }
.capsule.resolved { color: #0EA5E9; }
.capsule.closed { color: #80A0A8; }
.capsule-body { flex: 1; min-width: 0; }
.capsule-num { font-size: 16px; font-weight: 700; color: var(--text-main); line-height: 1.1; }
.capsule-label { font-size: 11px; color: var(--text-sub); margin-top: 2px; }

.chart-switches {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.seg {
  display: inline-flex;
  background: #F4F9FA;
  border-radius: 6px;
  overflow: hidden;
}
.seg button {
  background: transparent;
  border: none;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--text-sub);
  cursor: pointer;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.seg button.active {
  background: #006080;
  color: #fff;
  font-weight: 600;
}

.live-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  background: #22C55E;
  color: #fff;
  font-weight: 600;
  margin-left: auto;
}
.live-tag.offline { background: #80A0A8; }
.live-dot { width: 5px; height: 5px; border-radius: 50%; background: #fff; }

.image-wrap {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #F1F7F8;
  border: 1px solid #E8EDEF;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.image-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }

.capture-meta {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #F4F9FA;
}
.cm-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #006080;
}
.cm-title.unknown { color: #335B66; }
.cm-time { font-weight: 400; font-size: 11px; color: #80A0A8; }
.cm-sub { margin-top: 4px; font-size: 12px; color: #4A626B; line-height: 1.5; }

.placeholder { color: #80A0A8; text-align: center; }
.ph-icon { font-size: 40px; opacity: 0.6; margin-bottom: 8px; }
.ph-text { font-size: 13px; }

.chart-wrap { width: 100%; height: 260px; min-height: 220px; }

.alarm-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  /* 与告警分布统计卡片最低高度一致（标题 + 260px 内容区 + 内边距） */
  min-height: 320px;
}

/* ===== 待确认拍照 ===== */
.photo-card {
  flex-shrink: 0;
}
.photo-badge {
  margin-left: auto;
  background: #F59E0B;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}
.photo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.photo-item {
  border: 1px solid #F0E6D2;
  background: #FFFCF5;
  border-radius: 8px;
  padding: 9px 10px;
}
.photo-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.photo-waypoint {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}
.photo-type {
  margin-left: auto;
  font-size: 11px;
  color: #B26A00;
  background: #FDF0DA;
  border-radius: 4px;
  padding: 1px 6px;
}
.photo-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #6B7A80;
}
.photo-time {
  margin-top: 2px;
  font-size: 11px;
  color: #9AA9AF;
}
.photo-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.photo-actions button {
  flex: 1;
  font-size: 12px;
  padding: 5px 0;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
}
.photo-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-photo {
  background: #00A0C0;
  color: #fff;
}
.btn-photo:hover:not(:disabled) {
  background: #008FAD;
}
.btn-skip {
  background: #fff;
  color: #406070;
  border-color: #D0E0E4 !important;
}
.btn-skip:hover:not(:disabled) {
  background: #F4F9FA;
}
.photo-empty {
  padding: 18px 0;
  text-align: center;
  color: #9AA9AF;
  font-size: 12px;
}
.alarm-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.unread-badge {
  background: #EF4444;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  text-decoration: none;
  min-width: 18px;
  text-align: center;
}
.btn-read {
  border: 1px solid #D0E0E4;
  background: #fff;
  color: #406070;
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 6px;
  cursor: pointer;
}
.btn-read:hover { background: #F4F9FA; color: #006080; }

.alarm-list {
  flex: 1 1 0;
  min-height: 0; /* 允许列表在固定卡片高度内收缩，超出部分滚动 */
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  margin: 0 -4px;
  padding: 0 4px;
}
.alarm-list::-webkit-scrollbar { width: 5px; }
.alarm-list::-webkit-scrollbar-thumb { background: #C0D0D8; border-radius: 3px; }

.alarm-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #F0F4F5;
  cursor: pointer;
  margin-bottom: 6px;
  background: #fff;
}
.alarm-item.unread {
  background: #F4F9FA;
  border-color: #C0E0E8;
}
.alarm-item:hover { background: #E6F4F8; }

.red-dot { width: 6px; height: 6px; background: #EF4444; border-radius: 50%; margin-top: 5px; margin-left: auto; flex-shrink: 0; }

.alarm-level { flex-shrink: 0; font-size: 11px; font-weight: 600; padding: 2px 7px; border-radius: 5px; white-space: nowrap; }
.alarm-level.danger { background: #FEE2E2; color: #DC2626; }
.alarm-level.warn { background: #FEF3C7; color: #D97706; }
.alarm-level.normal { background: #E0F2FE; color: #006080; }

.alarm-body { flex: 1; min-width: 0; }
.alarm-type { font-size: 13px; font-weight: 600; color: var(--text-main); margin-bottom: 2px; }
.alarm-desc { font-size: 12px; color: var(--text-sub); line-height: 1.4; margin-bottom: 3px; }
.alarm-time { font-size: 11px; color: #80A0A8; }

.empty-tip { margin: auto; padding: 40px 0; text-align: center; color: #80A0A8; font-size: 13px; }

@media (max-width: 1100px) {
  .home-side { width: 260px; }
}
@media (max-width: 900px) {
  .home-page { flex-direction: column; }
  .home-side { width: 100%; }
  .row { flex-direction: column; }
  .status-capsules { grid-template-columns: repeat(3, 1fr); }
}
</style>
