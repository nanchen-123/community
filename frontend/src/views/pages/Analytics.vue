<template>
	<div class="page-wrap">
		<!-- 页面标题 -->
		<div class="page-header">
			<div class="page-title">数据分析</div>
			<div class="page-sub">
				<svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<rect x="3" y="4" width="18" height="16" rx="2"></rect>
					<line x1="3" y1="9" x2="21" y2="9"></line>
					<line x1="9" y1="14" x2="15" y2="14"></line>
				</svg>
				<span>全周期巡检数据可视化分析与报表导出</span>
			</div>
		</div>

		<!-- 数据指标卡片 -->
		<div class="stat-row">
			<div class="stat-card">
				<div class="stat-icon stat-blue">
					<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline>
					</svg>
				</div>
				<div class="stat-body">
					<div class="stat-num">{{ totalHazards }}</div>
					<div class="stat-label">隐患总数</div>
					<div class="stat-trend" :class="weekDelta >= 0 ? 'bad' : 'good'">
						<span class="trend-arrow">{{ weekDelta >= 0 ? '↑' : '↓' }}</span>
						<span>较上周 {{ weekDelta >= 0 ? '+' : '' }}{{ weekDelta }}</span>
					</div>
				</div>
			</div>

			<div class="stat-card">
				<div class="stat-icon stat-green">
					<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
						<polyline points="22 4 12 14.01 9 11.01"></polyline>
					</svg>
				</div>
				<div class="stat-body">
					<div class="stat-num">{{ inspectRate }}%<span v-if="inspectLowData" class="stat-warn" title="本周巡检任务不足 3 条，数据可能有波动">样本少</span></div>
					<div class="stat-label">巡检完成率</div>
					<div class="stat-trend" :class="inspectRateDelta >= 0 ? 'good' : 'bad'">
						<span class="trend-arrow">{{ inspectRateDelta >= 0 ? '↑' : '↓' }}</span>
						<span>较上周 {{ inspectRateDelta >= 0 ? '+' : '' }}{{ inspectRateDelta }}%</span>
					</div>
				</div>
			</div>

			<div class="stat-card">
				<div class="stat-icon stat-amber">
					<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<circle cx="12" cy="12" r="10"></circle>
						<polyline points="12 6 12 12 16 14"></polyline>
					</svg>
				</div>
				<div class="stat-body">
					<div class="stat-num">{{ avgDisposeTime }}<span class="unit">分钟</span></div>
					<div class="stat-label">平均告警处置时长</div>
					<div class="stat-trend" :class="disposeTimeDelta <= 0 ? 'good' : 'bad'">
						<span class="trend-arrow">{{ disposeTimeDelta <= 0 ? '↓' : '↑' }}</span>
						<span>较上周 {{ disposeTimeDelta >= 0 ? '+' : '' }}{{ disposeTimeDelta }} 分钟</span>
					</div>
				</div>
			</div>

			<div class="stat-card">
				<div class="stat-icon stat-indigo">
					<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
						<line x1="12" y1="9" x2="12" y2="13"></line>
						<line x1="12" y1="17" x2="12.01" y2="17"></line>
					</svg>
				</div>
				<div class="stat-body">
					<div class="stat-num">{{ disposeRate }}%</div>
					<div class="stat-label">告警处置率</div>
					<div class="stat-trend" :class="disposeRateDelta >= 0 ? 'good' : 'bad'">
						<span class="trend-arrow">{{ disposeRateDelta >= 0 ? '↑' : '↓' }}</span>
						<span>较上周 {{ disposeRateDelta >= 0 ? '+' : '' }}{{ disposeRateDelta }}%</span>
					</div>
				</div>
			</div>
		</div>

		<!-- 图表区：折线图 + 饼图 -->
		<div class="chart-row">
			<!-- 折线图：隐患变化趋势 -->
			<div class="card chart-card line-card">
				<div class="card-title">
					<svg class="title-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline>
					</svg>
					<span>隐患变化趋势</span>
					<div class="range-switch">
						<button
							class="range-btn"
							:class="{ active: trendRange === 7 }"
							@click="switchRange(7)"
						>近 7 天</button>
						<button
							class="range-btn"
							:class="{ active: trendRange === 30 }"
							@click="switchRange(30)"
						>近 30 天</button>
					</div>
				</div>
				<div ref="lineRef" class="chart-container"></div>
			</div>

			<!-- 饼图：三类隐患占比 -->
			<div class="card chart-card pie-card">
				<div class="card-title">
					<svg class="title-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M21.21 15.89A10 10 0 1 1 8 2.83"></path>
						<path d="M22 12A10 10 0 0 0 12 2v10z"></path>
					</svg>
					<span>隐患类型占比</span>
				</div>
				<div ref="pieRef" class="chart-container"></div>
				<div class="pie-legend">
					<div class="legend-item" v-for="(item, idx) in pieData" :key="idx">
						<span class="legend-dot" :style="{ background: item.color }"></span>
						<span class="legend-name">{{ item.name }}</span>
						<span class="legend-value">{{ item.value }} 起</span>
						<span class="legend-pct">{{ item.pct }}%</span>
					</div>
				</div>
			</div>
		</div>

		<!-- 明细数据表 + 报表导出 -->
		<div class="card detail-card">
			<div class="card-title">
				<svg class="title-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<line x1="8" y1="6" x2="21" y2="6"></line>
					<line x1="8" y1="12" x2="21" y2="12"></line>
					<line x1="8" y1="18" x2="21" y2="18"></line>
					<line x1="3" y1="6" x2="3.01" y2="6"></line>
					<line x1="3" y1="12" x2="3.01" y2="12"></line>
					<line x1="3" y1="18" x2="3.01" y2="18"></line>
				</svg>
				<span>每日明细数据</span>
				<button class="btn-export" @click="exportReport">
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
						<polyline points="7 10 12 15 17 10"></polyline>
						<line x1="12" y1="15" x2="12" y2="3"></line>
					</svg>
					导出统计报表
				</button>
			</div>
			<div class="table-wrap">
				<table class="data-table">
					<thead>
						<tr>
							<th style="width:60px">序号</th>
							<th style="width:130px">日期</th>
							<th style="width:100px">消防安全</th>
							<th style="width:100px">人员异常</th>
							<th style="width:100px">其他</th>
							<th style="width:100px">隐患总数</th>
							<th style="width:110px">已处置</th>
							<th style="width:110px">处置率</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="(item, idx) in detailList" :key="item.date" :class="{ 'row-warn': item.total > 15 }">
							<td class="mono">{{ idx + 1 }}</td>
							<td class="time-cell">{{ item.date }}</td>
							<td>{{ item.xiaofang }}</td>
							<td>{{ item.renyuan }}</td>
							<td>{{ item.qita }}</td>
							<td>
								<span class="num-tag" :class="item.total > 15 ? 'high' : 'normal'">{{ item.total }}</span>
							</td>
							<td>{{ item.disposed }}</td>
							<td>
								<span class="rate-tag" :class="rateClass(item.rate)">{{ item.rate }}%</span>
							</td>
						</tr>
					</tbody>
				</table>
			</div>
		</div>

		<!-- Toast 提示 -->
		<transition name="toast">
			<div v-if="toast.visible" class="toast" :class="toast.type">
				<svg v-if="toast.type === 'success'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
					<polyline points="20 6 9 17 4 12"></polyline>
				</svg>
				<span>{{ toast.msg }}</span>
			</div>
		</transition>
	</div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import request from '../../api/request'
import { getAuth } from '../../stores/community'

echarts.use([LineChart, PieChart, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])

/* ============ 数据指标 ============ */
const totalHazards = ref(0)
const weekDelta = ref(0)
const inspectRate = ref(0)
const inspectRateDelta = ref(0)
const inspectLowData = ref(false)
const avgDisposeTime = ref(0)
const disposeTimeDelta = ref(0)
const disposeRate = ref(0)
const disposeRateDelta = ref(0)

/* ============ 告警类型（与后端 alarm_type 一致） ============ */
const ALARM_TYPES = [
	{ key: '消防安全', name: '消防安全', color: '#EF4444' },
	{ key: '人员异常', name: '人员异常', color: '#8B5CF6' },
	{ key: '其他', name: '其他', color: '#F59E0B' }
]

/* ========== 时间工具 ========== */
function startOfDay(d) {
	return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}
function formatKey(t) {
	const d = new Date(t)
	return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
function formatYYYYMMDD(t) {
	const d = new Date(t)
	return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
/** 兼容后端返回的 "yyyy-MM-dd HH:mm:ss" 与 ISO 格式 */
function parseTime(v) {
	if (!v) return 0
	if (Array.isArray(v)) {
		return new Date(v[0], (v[1] || 1) - 1, v[2] || 1, v[3] || 0, v[4] || 0, v[5] || 0).getTime()
	}
	const t = new Date(String(v).replace(' ', 'T')).getTime()
	return Number.isNaN(t) ? 0 : t
}

/* ========== 真实数据聚合 ========== */
const alarmsRaw = ref([])
const tasksRaw = ref([])

const alarmTime = a => startOfDay(new Date(parseTime(a.findTime)))
const taskTime = t => startOfDay(new Date(parseTime(t.startTime || t.endTime || t.createTime)))

async function fetchAnalyticsData() {
	const auth = getAuth()
	const baseParams = { pageNum: 1, pageSize: 1000 }
	if (auth && auth.communityId) baseParams.communityId = auth.communityId
	try {
		const [aRes, tRes] = await Promise.all([
			request.get('/alarm/page', { params: { ...baseParams } }),
			request.get('/task/page', { params: { ...baseParams } })
		])
		alarmsRaw.value = aRes.records || []
		tasksRaw.value = tRes.records || []

		const now = Date.now()
		const todayStart = startOfDay(new Date())
		const weekStart = todayStart - 6 * 86400000 // 近7天（含今天）
		const lastWeekStart = weekStart - 7 * 86400000
		const lastWeekEnd = weekStart - 1

		const inTime = (t, s, e) => t >= s && t <= e

		/* ---- 本周 vs 上周 ---- */
		const alarmsThis = alarmsRaw.value.filter(a => inTime(alarmTime(a), weekStart, now))
		const alarmsLast = alarmsRaw.value.filter(a => inTime(alarmTime(a), lastWeekStart, lastWeekEnd))
		const tasksThis = tasksRaw.value.filter(t => inTime(taskTime(t), weekStart, now))
		const tasksLast = tasksRaw.value.filter(t => inTime(taskTime(t), lastWeekStart, lastWeekEnd))

		// 1) 隐患总数
		totalHazards.value = alarmsThis.length
		weekDelta.value = alarmsThis.length - alarmsLast.length

		// 2) 巡检完成率 = 已完成 / 总数（本周 vs 上周）
		const tasksThisTotal = tasksThis.length
		const tasksThisDone = tasksThis.filter(t => t.taskStatus === '已完成').length
		const rateThis = tasksThisTotal === 0 ? 0 : Math.round(tasksThisDone / tasksThisTotal * 100)
		const tasksLastTotal = tasksLast.length
		const tasksLastDone = tasksLast.filter(t => t.taskStatus === '已完成').length
		const rateLast = tasksLastTotal === 0 ? 0 : Math.round(tasksLastDone / tasksLastTotal * 100)
		inspectRate.value = rateThis
		inspectRateDelta.value = rateThis - rateLast
		inspectLowData.value = tasksThisTotal < 3

		// 3) 告警处置率 = 已处理 / 总数（本周 vs 上周）
		const dispThisTotal = alarmsThis.length
		const dispThisDone = alarmsThis.filter(a => a.handleStatus === '已处理').length
		const dispRateThis = dispThisTotal === 0 ? 0 : Math.round(dispThisDone / dispThisTotal * 100)
		const dispLastTotal = alarmsLast.length
		const dispLastDone = alarmsLast.filter(a => a.handleStatus === '已处理').length
		const dispRateLast = dispLastTotal === 0 ? 0 : Math.round(dispLastDone / dispLastTotal * 100)
		disposeRate.value = dispRateThis
		disposeRateDelta.value = dispRateThis - dispRateLast

		// 4) 平均告警处置时长（分钟）= handleTime - findTime
		function avgDispose(arr) {
			const disposed = arr.filter(a => a.handleStatus === '已处理' && a.findTime && a.handleTime)
			if (disposed.length === 0) return 0
			const total = disposed.reduce((s, a) => {
				const diff = (parseTime(a.handleTime) - parseTime(a.findTime)) / 60000
				// 不合理数据保护：处置时长 < 0 按 0 算，> 7 天按 7 天封顶
				return s + Math.max(0, Math.min(diff, 60 * 24 * 7))
			}, 0)
			return Math.round(total / disposed.length * 10) / 10
		}
		const avgThis = avgDispose(alarmsThis)
		const avgLast = avgDispose(alarmsLast)
		avgDisposeTime.value = avgThis
		disposeTimeDelta.value = Math.round((avgThis - avgLast) * 10) / 10

		/* ---- 重建折线图数据 ---- */
		function buildTrend(days) {
			const dates = []
			const series = { 消防安全: [], 人员异常: [], 其他: [] }
			const today = new Date()
			for (let i = days - 1; i >= 0; i--) {
				const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i)
				const t = startOfDay(d)
				dates.push(formatKey(t))
				const group = { 消防安全: 0, 人员异常: 0, 其他: 0 }
				alarmsRaw.value.forEach(a => {
					if (alarmTime(a) === t) group[a.alarmType] = (group[a.alarmType] || 0) + 1
				})
				series['消防安全'].push(group['消防安全'])
				series['人员异常'].push(group['人员异常'])
				series['其他'].push(group['其他'])
			}
			return { dates, series }
		}
		trend7Data.value = buildTrend(7)
		trend30Data.value = buildTrend(30)

		// 若图表已初始化，则刷新数据
		refreshLineChart()

		/* ---- 重建明细表 ---- */
		buildDetailList()
	} catch (e) {
		console.error('拉取统计数据失败:', e)
	}
}

/* ============ 折线图：隐患变化趋势 ============ */
const lineRef = ref(null)
let lineChart = null
const trendRange = ref(7)
const emptyTrend = () => ({ dates: [], series: { 消防安全: [], 人员异常: [], 其他: [] } })
const trend7Data = ref(emptyTrend())
const trend30Data = ref(emptyTrend())

function getTrendData() {
	return trendRange.value === 7 ? trend7Data.value : trend30Data.value
}

function refreshLineChart() {
	if (!lineChart) return
	const data = getTrendData()
	lineChart.setOption({
		xAxis: { data: data.dates },
		series: ALARM_TYPES.map(t => ({ name: t.name, data: data.series[t.key] }))
	})
}

function buildLineSeries(data) {
	return ALARM_TYPES.map(t => ({
		name: t.name,
		type: 'line',
		smooth: true,
		symbol: 'circle',
		symbolSize: 6,
		data: data.series[t.key],
		itemStyle: { color: t.color },
		lineStyle: { width: 2.5 },
		areaStyle: {
			color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
				{ offset: 0, color: hexToRgba(t.color, 0.22) },
				{ offset: 1, color: hexToRgba(t.color, 0.02) }
			])
		}
	}))
}

function hexToRgba(hex, alpha) {
	const h = hex.replace('#', '')
	const r = parseInt(h.substring(0, 2), 16)
	const g = parseInt(h.substring(2, 4), 16)
	const b = parseInt(h.substring(4, 6), 16)
	return `rgba(${r},${g},${b},${alpha})`
}

function initLineChart() {
	if (!lineRef.value) return
	const rect = lineRef.value.getBoundingClientRect()
	if (rect.width < 10 || rect.height < 10) return
	if (lineChart) lineChart.dispose()
	lineChart = echarts.init(lineRef.value)
	const data = getTrendData()
	lineChart.setOption({
		tooltip: {
			trigger: 'axis',
			backgroundColor: 'rgba(30,58,64,0.92)',
			borderColor: 'transparent',
			textStyle: { color: '#fff', fontSize: 12 }
		},
		legend: {
			data: ALARM_TYPES.map(t => t.name),
			right: 10,
			top: 0,
			textStyle: { color: 'var(--text-sub)', fontSize: 12 },
			itemWidth: 12,
			itemHeight: 8
		},
		grid: { top: 40, left: 40, right: 20, bottom: 30 },
		xAxis: {
			type: 'category',
			data: data.dates,
			boundaryGap: false,
			axisLine: { lineStyle: { color: '#D0E0E4' } },
			axisLabel: { color: 'var(--text-sub)', fontSize: 11 }
		},
		yAxis: {
			type: 'value',
			minInterval: 1,
			axisLabel: { color: 'var(--text-sub)', fontSize: 11 },
			splitLine: { lineStyle: { color: '#E8F0F2', type: 'dashed' } }
		},
		series: buildLineSeries(data)
	})
}

function switchRange(days) {
	if (trendRange.value === days) return
	trendRange.value = days
	initLineChart()
	buildDetailList()
}

/* ============ 饼图：三类隐患占比 ============ */
const pieRef = ref(null)
let pieChart = null

const pieData = computed(() => {
	const group = { 消防安全: 0, 人员异常: 0, 其他: 0 }
	const weekStart = startOfDay(new Date()) - 6 * 86400000
	alarmsRaw.value.forEach(a => {
		if (alarmTime(a) >= weekStart) group[a.alarmType] = (group[a.alarmType] || 0) + 1
	})
	const total = group['消防安全'] + group['人员异常'] + group['其他']
	return ALARM_TYPES.map(t => ({
		name: t.name,
		value: group[t.key] || 0,
		color: t.color,
		pct: total === 0 ? '0.0' : (group[t.key] / total * 100).toFixed(1)
	}))
})

function initPieChart() {
	if (!pieRef.value) return
	const rect = pieRef.value.getBoundingClientRect()
	if (rect.width < 10 || rect.height < 10) return
	if (pieChart) pieChart.dispose()
	pieChart = echarts.init(pieRef.value)
	pieChart.setOption({
		tooltip: {
			trigger: 'item',
			formatter: '{b}: {c} 起 ({d}%)',
			backgroundColor: 'rgba(30,58,64,0.92)',
			borderColor: 'transparent',
			textStyle: { color: '#fff', fontSize: 12 }
		},
		legend: { show: false },
		series: [
			{
				name: '隐患占比',
				type: 'pie',
				radius: ['45%', '72%'],
				center: ['50%', '50%'],
				avoidLabelOverlap: true,
				itemStyle: {
					borderRadius: 6,
					borderColor: '#fff',
					borderWidth: 2
				},
				label: {
					show: true,
					formatter: '{b}\n{d}%',
					color: '#406070',
					fontSize: 12,
					fontWeight: 600
				},
				labelLine: {
					length: 12,
					length2: 10,
					lineStyle: { color: '#B0C8D0' }
				},
				data: pieData.value.map(d => ({
					name: d.name,
					value: d.value,
					itemStyle: { color: d.color }
				}))
			}
		]
	})
}

watch(pieData, () => {
	if (!pieChart) return
	pieChart.setOption({
		series: [{
			data: pieData.value.map(d => ({
				name: d.name,
				value: d.value,
				itemStyle: { color: d.color }
			}))
		}]
	})
}, { deep: true })

watch(trendRange, () => {
	nextTick(() => {
		initLineChart()
		buildDetailList()
	})
})

/* ============ 生命周期 ============ */
let lineRetryTimer = null
let pieRetryTimer = null
let pollTimer = null

function resizeAll() {
	lineChart && lineChart.resize()
	pieChart && pieChart.resize()
}

onMounted(async () => {
	await fetchAnalyticsData()
	await nextTick()
	const tryLine = (count = 0) => {
		initLineChart()
		if (!lineChart && count < 10) {
			lineRetryTimer = setTimeout(() => tryLine(count + 1), 100)
		}
	}
	const tryPie = (count = 0) => {
		initPieChart()
		if (!pieChart && count < 10) {
			pieRetryTimer = setTimeout(() => tryPie(count + 1), 100)
		}
	}
	tryLine()
	tryPie()
	window.addEventListener('resize', resizeAll)
	pollTimer = setInterval(fetchAnalyticsData, 60000) // 1 分钟刷新
})

onBeforeUnmount(() => {
	if (lineRetryTimer) clearTimeout(lineRetryTimer)
	if (pieRetryTimer) clearTimeout(pieRetryTimer)
	if (pollTimer) clearInterval(pollTimer)
	window.removeEventListener('resize', resizeAll)
	if (lineChart) {
		lineChart.dispose()
		lineChart = null
	}
	if (pieChart) {
		pieChart.dispose()
		pieChart = null
	}
})

/* ============ 明细数据表 ============ */
const detailList = ref([])
function buildDetailList() {
	const days = trendRange.value
	const list = []
	const today = new Date()
	for (let i = 0; i < days; i++) {
		const dayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i).getTime()
		const dayHazards = alarmsRaw.value.filter(a => alarmTime(a) === dayStart)
		const xiaofang = dayHazards.filter(a => a.alarmType === '消防安全').length
		const renyuan = dayHazards.filter(a => a.alarmType === '人员异常').length
		const qita = dayHazards.filter(a => a.alarmType === '其他').length
		const total = dayHazards.length
		const disposed = dayHazards.filter(a => a.handleStatus === '已处理').length
		const rate = total === 0 ? 0 : Math.round(disposed / total * 100)
		list.push({
			date: formatYYYYMMDD(dayStart),
			xiaofang, renyuan, qita,
			total, disposed, rate
		})
	}
	detailList.value = list
}
function rateClass(rate) {
	if (rate >= 95) return 'high'
	if (rate >= 85) return 'normal'
	return 'low'
}

/* ============ 报表导出 ============ */
function exportReport() {
	const rows = []
	rows.push(['社区安防智能巡检系统 - 统计分析报表'])
	rows.push(['导出时间：', new Date().toLocaleString('zh-CN')])
	rows.push([])
	rows.push(['一、核心指标（本周 vs 上周）'])
	rows.push(['指标名称', '数值', '环比变化'])
	rows.push(['隐患总数', `${totalHazards.value} 起`, `${weekDelta.value >= 0 ? '+' : ''}${weekDelta.value} 起`])
	rows.push(['巡检完成率', `${inspectRate.value}%`, `${inspectRateDelta.value >= 0 ? '+' : ''}${inspectRateDelta.value}%`])
	rows.push(['平均告警处置时长', `${avgDisposeTime.value} 分钟`, `${disposeTimeDelta.value >= 0 ? '+' : ''}${disposeTimeDelta.value} 分钟`])
	rows.push(['告警处置率', `${disposeRate.value}%`, `${disposeRateDelta.value >= 0 ? '+' : ''}${disposeRateDelta.value}%`])
	rows.push([])
	rows.push(['二、隐患类型占比（本周）'])
	rows.push(['隐患类型', '数量(起)', '占比(%)'])
	pieData.value.forEach(d => rows.push([d.name, d.value, d.pct]))
	rows.push([])
	rows.push(['三、每日明细数据（' + (trendRange.value === 7 ? '近7天' : '近30天') + '）'])
	rows.push(['序号', '日期', '消防安全', '人员异常', '其他', '隐患总数', '已处置', '处置率(%)'])
	detailList.value.forEach((r, i) =>
		rows.push([i + 1, r.date, r.xiaofang, r.renyuan, r.qita, r.total, r.disposed, r.rate])
	)
	rows.push([])
	rows.push(['四、结题说明'])
	rows.push(['本报表用于项目结题报告，数据由系统自动汇总生成，可盖章后归档。'])

	const csv = rows.map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
	const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
	const a = document.createElement('a')
	a.href = URL.createObjectURL(blob)
	a.download = `巡检统计报表_${new Date().toISOString().slice(0, 10)}.csv`
	a.click()
	URL.revokeObjectURL(a.href)
	showToast('报表已导出，可用于结题报告', 'success')
}

/* ============ Toast ============ */
const toast = reactive({ visible: false, type: 'success', msg: '' })
let toastTimer = null
function showToast(msg, type = 'success') {
	toast.msg = msg
	toast.type = type
	toast.visible = true
	if (toastTimer) clearTimeout(toastTimer)
	toastTimer = setTimeout(() => (toast.visible = false), 2600)
}
</script>

<style scoped>
/* ============ 整体容器 ============ */
.page-wrap {
	min-height: 100%;
	height: auto;
	box-sizing: border-box;
	display: flex;
	flex-direction: column;
	gap: 12px;
	overflow-x: hidden;
	flex: 0 0 auto;
}


/* ============ 指标卡片 ============ */
.stat-row {
	display: grid;
	grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
	gap: 12px;
}
.stat-card {
	background: #FFFFFF;
	border: 1px solid #E8EDEF;
	border-radius: 10px;
	padding: 14px;
	display: flex;
	align-items: center;
	gap: 12px;
	box-shadow: var(--shadow-card);
	transition: box-shadow 0.25s ease;
	animation: cardIn 0.4s ease-out backwards;
}
.stat-card:nth-child(1) { animation-delay: 0.02s; }
.stat-card:nth-child(2) { animation-delay: 0.06s; }
.stat-card:nth-child(3) { animation-delay: 0.10s; }
.stat-card:nth-child(4) { animation-delay: 0.14s; }
.stat-card:hover {
	transform: none;
	box-shadow: var(--shadow-float);
}
.stat-icon {
	width: 32px;
	height: 32px;
	border-radius: 8px;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 18px;
	flex-shrink: 0;
	color: #fff;
}
.stat-icon.stat-blue {
	background: var(--primary);
}
.stat-icon.stat-green {
	background: #10B981;
}
.stat-icon.stat-amber {
	background: #F59E0B;
}
.stat-icon.stat-indigo {
	background: #0EA5E9;
}
.stat-body {
	flex: 1;
	min-width: 0;
}
.stat-num {
	font-size: 22px;
	font-weight: 700;
	color: var(--text-main);
	line-height: 1.2;
	letter-spacing: 0.4px;
}
.stat-num .stat-warn {
	font-size: 11px;
	font-weight: 500;
	color: #F59E0B;
	margin-left: 6px;
	vertical-align: middle;
	background: #FEF3C7;
	padding: 1px 6px;
	border-radius: 8px;
	cursor: help;
}
.stat-num .unit {
	font-size: 13px;
	font-weight: 500;
	color: var(--text-soft);
	margin-left: 4px;
}
.stat-label {
	font-size: 12px;
	color: var(--text-sub);
	margin-top: 4px;
	letter-spacing: 0.5px;
}
.stat-trend {
	font-size: 11px;
	margin-top: 6px;
	display: flex;
	align-items: center;
	gap: 3px;
}
.stat-trend.good {
	color: #10B981;
}
.stat-trend.bad {
	color: #EF4444;
}
.trend-arrow {
	font-weight: 700;
}

/* ============ 卡片通用 ============ */
.card {
	background: #FFFFFF;
	border: 1px solid #E8EDEF;
	border-radius: 10px;
	padding: 14px;
	box-shadow: var(--shadow-card);
	animation: cardIn 0.45s ease-out backwards;
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
	letter-spacing: 0.3px;
}
.title-icon {
	color: var(--primary);
	flex-shrink: 0;
}

/* ============ 图表区 ============ */
.chart-row {
	display: grid;
	grid-template-columns: 1.4fr 1fr;
	gap: 12px;
}
.chart-card {
	display: flex;
	flex-direction: column;
}
.chart-container {
	flex: 1;
	width: 100%;
	min-height: 320px;
}
.line-card .chart-container {
	min-height: 360px;
}

/* 时间范围切换 */
.range-switch {
	display: inline-flex;
	background: #F0F6F7;
	padding: 3px;
	border-radius: 999px;
	margin-left: auto;
}
.range-btn {
	padding: 6px 14px;
	background: transparent;
	border: none;
	border-radius: 999px;
	font-size: 12px;
	font-weight: 500;
	color: var(--text-sub);
	cursor: pointer;
	transition: all 0.25s ease;
	letter-spacing: 0.3px;
}
.range-btn:hover {
	color: var(--text-main);
}
.range-btn.active {
	background: #FFFFFF;
	color: var(--primary);
	font-weight: 600;
	box-shadow: 0 1px 3px rgba(0, 96, 128, 0.15);
}

/* 饼图图例 */
.pie-legend {
	display: flex;
	flex-direction: column;
	gap: 8px;
	padding: 12px 16px 4px;
	background: #F8FCFD;
	border-radius: 8px;
	margin-top: 8px;
}
.legend-item {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 13px;
	padding: 6px 10px;
	background: #FFFFFF;
	border-radius: 6px;
	border: 1px solid #E8F0F2;
}
.legend-dot {
	width: 10px;
	height: 10px;
	border-radius: 50%;
	flex-shrink: 0;
}
.legend-name {
	color: #406070;
	font-weight: 600;
	flex: 1;
}
.legend-value {
	color: var(--text-sub);
	font-size: 12px;
}
.legend-pct {
	color: var(--primary);
	font-weight: 700;
	font-size: 13px;
	min-width: 50px;
	text-align: right;
}

/* ============ 明细数据表 ============ */
.btn-export {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 8px 16px;
	background: linear-gradient(135deg, var(--primary), var(--primary-light));
	color: #fff;
	border: none;
	border-radius: 8px;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
	transition: all 0.25s ease;
	margin-left: auto;
	box-shadow: 0 3px 10px rgba(0, 96, 128, 0.3);
	letter-spacing: 0.5px;
}
.btn-export:hover {
	transform: translateY(-1px);
	box-shadow: 0 5px 14px rgba(0, 96, 128, 0.4);
}

.table-wrap {
	overflow-x: auto;
	border-radius: 8px;
	border: 1px solid #E0EAEC;
	max-height: 420px;
	overflow-y: auto;
}
.data-table {
	width: 100%;
	border-collapse: collapse;
	font-size: 13px;
	min-width: 780px;
}
.data-table thead {
	background: #F4F9FA;
	position: sticky;
	top: 0;
	z-index: 1;
}
.data-table th {
	padding: 12px 14px;
	text-align: left;
	font-weight: 600;
	color: #406070;
	font-size: 12px;
	letter-spacing: 0.5px;
	border-bottom: 1px solid #E0EAEC;
	white-space: nowrap;
}
.data-table td {
	padding: 11px 14px;
	color: #406070;
	border-bottom: 1px solid #F0F4F5;
	vertical-align: middle;
}
.data-table tbody tr {
	transition: background 0.2s ease;
}
.data-table tbody tr:hover {
	background: #F8FCFD;
}
.data-table tbody tr.row-warn {
	background: #FFF9F0;
}
.data-table tbody tr.row-warn:hover {
	background: #FFF4E8;
}
.data-table tbody tr:last-child td {
	border-bottom: none;
}
.mono {
	font-family: ui-monospace, SFMono-Regular, monospace;
	font-size: 12px;
	color: var(--text-sub);
}
.time-cell {
	font-size: 12px;
	color: var(--text-sub);
	white-space: nowrap;
}
.num-tag {
	display: inline-block;
	padding: 2px 10px;
	border-radius: 999px;
	font-size: 12px;
	font-weight: 600;
	min-width: 30px;
	text-align: center;
}
.num-tag.normal {
	background: #E6F4F8;
	color: var(--primary);
}
.num-tag.high {
	background: #FEF3C7;
	color: #D97706;
}
.rate-tag {
	display: inline-block;
	padding: 2px 10px;
	border-radius: 999px;
	font-size: 12px;
	font-weight: 600;
}
.rate-tag.high {
	background: #ECFDF5;
	color: #10B981;
}
.rate-tag.normal {
	background: #E6F4F8;
	color: var(--primary);
}
.rate-tag.low {
	background: #FEF2F2;
	color: #EF4444;
}

/* ============ Toast ============ */
.toast {
	position: fixed;
	top: 20px;
	left: 50%;
	transform: translateX(-50%);
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 10px 20px;
	border-radius: 999px;
	font-size: 13px;
	font-weight: 600;
	color: #fff;
	box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
	z-index: 9999;
	letter-spacing: 0.3px;
}
.toast.success {
	background: linear-gradient(135deg, #059669, #10B981);
}
.toast-enter-active,
.toast-leave-active {
	transition: all 0.3s ease;
}
.toast-enter-from,
.toast-leave-to {
	opacity: 0;
	transform: translateX(-50%) translateY(-10px);
}

/* ============ 入场动画 ============ */
@keyframes cardIn {
	from { opacity: 0; transform: translateY(10px); }
	to { opacity: 1; transform: translateY(0); }
}

/* ============ 响应式 ============ */
@media (max-width: 1100px) {
	.chart-row {
		grid-template-columns: 1fr;
	}
}
@media (max-width: 768px) {
	.stat-row {
		grid-template-columns: repeat(2, 1fr);
	}
	.range-switch {
		margin-left: 0;
		width: 100%;
		justify-content: space-between;
	}
	.range-btn {
		flex: 1;
		text-align: center;
	}
}
@media (max-width: 480px) {
	.stat-row {
		grid-template-columns: 1fr;
	}
}
</style>
