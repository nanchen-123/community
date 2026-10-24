<script setup>
import { reactive, ref } from 'vue'

// Dashboard 统计
const stats = reactive({
  community: '--',
  device: '--',
  task: '--',
  alarm: '--'
})

// 实时告警（模拟，后续接 WS）
const recentAlarms = ref([])
</script>

<template>
  <div>
    <div class="page-title">首页概览</div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card" v-for="(s, i) in [
        { label: '小区数量', value: stats.community, statKey: 'primary', icon: 'OfficeBuilding' },
        { label: '设备总数', value: stats.device, statKey: 'secondary', icon: 'Cpu' },
        { label: '巡检任务', value: stats.task, statKey: 'tertiary', icon: 'Tickets' },
        { label: '今日告警', value: stats.alarm, statKey: 'danger', icon: 'Warning' }
      ]" :key="s.label" :style="{ ['--stat-color']:
        s.statKey === 'primary' ? 'var(--stat-primary)' :
        s.statKey === 'secondary' ? 'var(--stat-secondary)' :
        s.statKey === 'tertiary' ? 'var(--stat-tertiary)' :
        '#EF4444'
      }">
        <div class="stat-icon">
          <el-icon :size="24"><component :is="s.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 下方两栏 -->
    <div class="panel-row">
      <div class="panel">
        <div class="panel-title">实时告警</div>
        <div class="panel-body">
          <el-empty v-if="!recentAlarms.length" description="暂无告警" :image-size="80" />
          <div v-for="a in recentAlarms" :key="a.id" class="alarm-item">
            <span class="alarm-dot" :class="a.level === '高' ? 'danger' : 'warn'"></span>
            <span class="alarm-msg">{{ a.message }}</span>
            <span class="alarm-time">{{ a.time }}</span>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-title">系统概览</div>
        <div class="panel-body overview">
          <p>系统运行正常</p>
          <p>机器人在线：--</p>
          <p>今日巡检完成：--</p>
          <p>待处理告警：--</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 20px;
  letter-spacing: 1px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--card-bg);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px var(--shadow);
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--shadow-hover);
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  background: var(--stat-color);
}
.stat-info { flex: 1; }
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  color: var(--text-soft);
  margin-top: 4px;
}

.panel-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}
.panel {
  background: var(--card-bg);
  border-radius: 10px;
  box-shadow: 0 1px 3px var(--shadow);
  overflow: hidden;
}
.panel-title {
  padding: 14px 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  border-bottom: 1px solid var(--border-deep);
}
.panel-body { padding: 16px 20px; min-height: 260px; }

.alarm-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border-deep);
  font-size: 13px;
}
.alarm-item:last-child { border-bottom: none; }
.alarm-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.alarm-dot.danger { background: #EF4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.4); }
.alarm-dot.warn   { background: #F59E0B; box-shadow: 0 0 6px rgba(245, 158, 11, 0.4); }
.alarm-msg { flex: 1; color: var(--text-sub); }
.alarm-time { color: var(--text-soft); font-size: 12px; }

.overview p {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--text-sub);
  display: flex;
  justify-content: space-between;
}

@media (max-width: 960px) {
  .stat-row { grid-template-columns: repeat(2, 1fr); }
  .panel-row { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .stat-row { grid-template-columns: 1fr; }
}
</style>
