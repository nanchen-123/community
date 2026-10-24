<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'

const charts = ref({})

// 从父级主题 CSS 变量读取色值
function getTheme() {
  const root = document.querySelector('.home-wrap')
  const s = root ? getComputedStyle(root) : null
  const v = (k, def) => s ? (s.getPropertyValue(k) || def).trim() : def
  return {
    primary: v('--primary', '#006080'),
    primaryLight: v('--primary-light', '#0080A0'),
    text: v('--text-sub', '#507080'),
    title: v('--text-main', '#1E3A40'),
    ok: '#22C55E',
    warn: '#F59E0B',
    danger: '#EF4444',
    line: v('--border', '#D0E0E4'),
    grid: v('--border-deep', '#E0EAEB')
  }
}

function initCharts() {
  const c = getTheme()

  // ---------- 告警类型分布 (环形) ----------
  const alarmPie = echarts.init(document.getElementById('chart-alarm-pie'))
  alarmPie.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: c.text } },
    series: [{
      type: 'pie',
      radius: ['50%', '72%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: [
        { value: 12, name: '周界入侵', itemStyle: { color: c.primary } },
        { value: 8,  name: '车辆违停', itemStyle: { color: c.primaryLight } },
        { value: 5,  name: '消防隐患', itemStyle: { color: c.warn } },
        { value: 3,  name: '人员聚集', itemStyle: { color: c.danger } },
        { value: 4,  name: '其他', itemStyle: { color: c.text } }
      ]
    }]
  })
  charts.value.alarmPie = alarmPie

  // ---------- 近 7 天告警趋势 (柱 + 折线) ----------
  const trendBar = echarts.init(document.getElementById('chart-trend'))
  trendBar.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: c.text } },
    grid: { top: 36, right: 20, bottom: 28, left: 40 },
    xAxis: {
      type: 'category',
      data: ['10-16', '10-17', '10-18', '10-19', '10-20', '10-21', '10-22'],
      axisLine: { lineStyle: { color: c.line } },
      axisLabel: { color: c.text }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: c.grid } },
      axisLabel: { color: c.text }
    },
    series: [
      {
        name: '告警数', type: 'bar', barWidth: 18,
        data: [3, 5, 2, 6, 4, 8, 6],
        itemStyle: { color: c.primary, borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '处理数', type: 'line', smooth: true,
        data: [3, 4, 2, 5, 4, 7, 5],
        itemStyle: { color: c.ok }, lineStyle: { width: 2 },
        symbol: 'circle', symbolSize: 6
      }
    ]
  })
  charts.value.trend = trendBar

  // ---------- 设备状态 (环形) ----------
  const devicePie = echarts.init(document.getElementById('chart-device-pie'))
  devicePie.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}台 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: c.text } },
    series: [{
      type: 'pie',
      radius: ['50%', '72%'],
      center: ['50%', '45%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, position: 'center', formatter: '{c}\n台', color: c.title, fontSize: 14, fontWeight: 'bold' },
      data: [
        { value: 42, name: '在线', itemStyle: { color: c.ok } },
        { value: 5,  name: '离线', itemStyle: { color: c.danger } },
        { value: 3,  name: '维护中', itemStyle: { color: c.warn } }
      ]
    }]
  })
  charts.value.devicePie = devicePie

  // ---------- 任务完成率 (仪表盘) ----------
  const taskGauge = echarts.init(document.getElementById('chart-task-gauge'))
  taskGauge.setOption({
    series: [{
      type: 'gauge',
      startAngle: 210,
      endAngle: -30,
      min: 0, max: 100,
      radius: '85%',
      progress: { show: true, width: 14, itemStyle: { color: c.primary } },
      axisLine: { lineStyle: { width: 14, color: [[1, c.grid]] } },
      pointer: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      anchor: { show: false },
      title: { offsetCenter: [0, '20%'], color: c.text, fontSize: 13 },
      detail: { offsetCenter: [0, '-5%'], fontSize: 32, fontWeight: 'bold', color: c.title, formatter: '{value}%' },
      data: [{ value: 78, name: '巡检任务完成率' }]
    }]
  })
  charts.value.taskGauge = taskGauge
}

function resizeAll() {
  Object.values(charts.value).forEach(c => c && c.resize())
}

// 管理员/普通切换时重新取色并重绘
function refreshCharts() {
  resizeAll()
  initCharts()
}

onMounted(async () => {
  await nextTick()
  initCharts()
  window.addEventListener('resize', resizeAll)
  // 暴露到 window 方便 Home.vue 主题切换后调用
  window.__refreshAnalyticsCharts = refreshCharts
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAll)
  Object.values(charts.value).forEach(c => c && c.dispose())
  if (window.__refreshAnalyticsCharts === refreshCharts) delete window.__refreshAnalyticsCharts
})
</script>

<template>
  <div>
    <div class="page-title">数据分析</div>

    <!-- 四个小图 -->
    <div class="chart-grid">
      <div class="chart-card">
        <div class="chart-title">告警类型分布</div>
        <div id="chart-alarm-pie" class="chart-box"></div>
      </div>

      <div class="chart-card">
        <div class="chart-title">近 7 天告警趋势</div>
        <div id="chart-trend" class="chart-box"></div>
      </div>

      <div class="chart-card">
        <div class="chart-title">设备运行状态</div>
        <div id="chart-device-pie" class="chart-box"></div>
      </div>

      <div class="chart-card">
        <div class="chart-title">巡检任务完成率</div>
        <div id="chart-task-gauge" class="chart-box"></div>
      </div>
    </div>

    <!-- 底部：告警等级排行 -->
    <div class="panel">
      <div class="panel-title">近 30 天告警等级排行</div>
      <div class="panel-body">
        <el-table :data="[
          { level: '高', count: 6, pct: 15 },
          { level: '中', count: 18, pct: 45 },
          { level: '低', count: 16, pct: 40 }
        ]" stripe style="width: 100%;">
          <el-table-column prop="level" label="等级" width="100">
            <template #default="{ row }">
              <span :class="'level-tag level-' + (row.level === '高' ? 'high' : row.level === '中' ? 'mid' : 'low')">{{ row.level }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="count" label="次数" width="120" />
          <el-table-column label="占比">
            <template #default="{ row }">
              <div class="bar-wrap">
                <div class="bar" :style="{ width: row.pct + '%', background: row.level === '高' ? '#EF4444' : row.level === '中' ? '#F59E0B' : 'var(--primary)' }"></div>
                <span class="bar-text">{{ row.pct }}%</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-title { font-size: 18px; font-weight: 600; color: var(--text-main); margin-bottom: 20px; letter-spacing: 1px; }

.chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.chart-card {
  background: var(--card-bg);
  border-radius: 10px;
  box-shadow: 0 1px 3px var(--shadow);
  padding: 16px 20px;
}
.chart-title { font-size: 14px; font-weight: 600; color: var(--text-main); margin-bottom: 10px; }
.chart-box { width: 100%; height: 240px; }

.panel { background: var(--card-bg); border-radius: 10px; box-shadow: 0 1px 3px var(--shadow); overflow: hidden; }
.panel-title { padding: 14px 20px; font-size: 14px; font-weight: 600; color: var(--text-main); border-bottom: 1px solid var(--border-deep); }
.panel-body { padding: 16px 20px; }

.level-tag { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 12px; font-weight: 600; }
.level-high { background: #FEF2F2; color: #EF4444; }
.level-mid  { background: #FFFBEB; color: #D97706; }
.level-low  { background: var(--active-bg); color: var(--primary); }

.bar-wrap { display: flex; align-items: center; gap: 12px; height: 22px; }
.bar { height: 10px; border-radius: 5px; transition: width 0.5s ease; min-width: 2px; }
.bar-text { font-size: 12px; color: var(--text-sub); width: 40px; flex-shrink: 0; }

@media (max-width: 960px) { .chart-grid { grid-template-columns: 1fr; } }
</style>
