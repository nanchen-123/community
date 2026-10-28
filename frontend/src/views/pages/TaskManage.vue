<script setup>
import { reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { communityState, loadCommunities, communityName, getAuth } from '../../stores/community'

// 当前登录身份
const auth = getAuth()
const isSuperAdmin = auth.role === '超级管理员'
const myCommunityId = auth.communityId || null

const statusOptions = ['待执行', '执行中', '已完成', '已取消']

/* ====== 表格数据 ====== */
const table = reactive({
  data: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  loading: false
})

const searchForm = reactive({ taskName: '', taskStatus: '' })

// 设备下拉（按当前身份的社区范围加载）
const deviceList = reactive([])

async function loadDevices() {
  try {
    const params = { pageNum: 1, pageSize: 500 }
    if (isSuperAdmin) {
      if (communityState.selectedId) params.communityId = communityState.selectedId
    } else if (myCommunityId) {
      params.communityId = myCommunityId
    }
    const res = await request.get('/device/page', { params })
    deviceList.length = 0
    deviceList.push(...(res.records || []))
  } catch {}
}

async function loadData() {
  table.loading = true
  try {
    const params = { pageNum: table.pageNum, pageSize: table.pageSize }
    if (searchForm.taskName) params.taskName = searchForm.taskName
    if (searchForm.taskStatus) params.taskStatus = searchForm.taskStatus
    // 数据范围：超管看全部（可按顶栏社区筛选），管理员只看自己所属社区
    if (isSuperAdmin) {
      if (communityState.selectedId) params.communityId = communityState.selectedId
    } else if (myCommunityId) {
      params.communityId = myCommunityId
    }
    const res = await request.get('/task/page', { params })
    table.data = res.records || []
    table.total = res.total || 0
  } catch {}
  table.loading = false
}

function onSearch() { table.pageNum = 1; loadData() }
function onReset() {
  searchForm.taskName = ''
  searchForm.taskStatus = ''
  onSearch()
}
function onPageChange(p) { table.pageNum = p; loadData() }
function onSizeChange(s) { table.pageSize = s; table.pageNum = 1; loadData() }

/* ====== 新增/编辑弹窗 ====== */
const dialog = reactive({
  visible: false,
  mode: 'add',
  form: {
    taskId: null,
    communityId: null,
    deviceId: null,
    userId: null,
    taskName: '',
    taskStatus: '待执行',
    loops: 1,
    endTime: null,
    waypoints: []
  },
  loading: false
})

/* 预设航点（与机器人端 Point_1~Point_11 坐标映射一致） */
const PRESET_POINTS = Array.from({ length: 11 }, (_, i) => `Point_${i + 1}`)
const PHOTO_TYPES = [
  { value: 'person', label: '人员识别' },
  { value: 'flame', label: '消防识别' },
  { value: 'plate', label: '车牌识别' }
]

function emptyWaypoint(order) {
  return {
    order,
    name: PRESET_POINTS[(order - 1) % PRESET_POINTS.length],
    x: null, y: null, z: null,
    qx: null, qy: null, qz: null, qw: null,
    longitude: null, latitude: null,
    autoPhoto: false,
    photoType: 'person',
    remark: ''
  }
}

function openAdd() {
  dialog.mode = 'add'
  dialog.form = {
    taskId: null,
    communityId: isSuperAdmin ? (communityState.selectedId || null) : myCommunityId,
    deviceId: null,
    userId: auth.userId || null,
    taskName: '',
    taskStatus: '待执行',
    loops: 1,
    endTime: null,
    waypoints: [emptyWaypoint(1)]
  }
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.form = {
    taskId: row.taskId,
    communityId: row.communityId,
    deviceId: row.deviceId,
    userId: row.userId,
    taskName: row.taskName,
    taskStatus: row.taskStatus || '待执行',
    loops: row.loops ?? 1,
    endTime: row.endTime,
    waypoints: (row.waypoints || []).map((w, i) => ({
      order: w.order ?? i + 1,
      name: w.name || PRESET_POINTS[i % PRESET_POINTS.length],
      x: w.x ?? null,
      y: w.y ?? null,
      z: w.z ?? null,
      qx: w.qx ?? null,
      qy: w.qy ?? null,
      qz: w.qz ?? null,
      qw: w.qw ?? null,
      longitude: w.longitude ?? null,
      latitude: w.latitude ?? null,
      autoPhoto: !!w.autoPhoto,
      photoType: w.photoType || 'person',
      remark: w.remark || ''
    }))
  }
  dialog.visible = true
}

function addWaypoint() {
  dialog.form.waypoints.push(emptyWaypoint(dialog.form.waypoints.length + 1))
}
function removeWaypoint(index) {
  dialog.form.waypoints.splice(index, 1)
  dialog.form.waypoints.forEach((w, i) => { w.order = i + 1 })
}

async function submitForm() {
  const f = dialog.form
  if (!f.taskName || !f.taskName.trim()) {
    return ElMessage.warning('请输入任务名称')
  }
  if (isSuperAdmin && !f.communityId) {
    return ElMessage.warning('请选择所属社区')
  }
  if (!f.deviceId) {
    return ElMessage.warning('请选择执行设备')
  }
  // 路径点校验：必须选择预设航点
  const points = f.waypoints
    .filter(w => w.name)
    .map((w, i) => ({
      order: i + 1,
      name: w.name,
      x: w.x ?? null,
      y: w.y ?? null,
      z: w.z ?? null,
      qx: w.qx ?? null,
      qy: w.qy ?? null,
      qz: w.qz ?? null,
      qw: w.qw ?? null,
      longitude: w.longitude ?? null,
      latitude: w.latitude ?? null,
      autoPhoto: !!w.autoPhoto,
      photoType: w.autoPhoto ? w.photoType : null,
      remark: w.remark || ''
    }))
  if (!points.length) {
    return ElMessage.warning('请至少选择一个巡检航点')
  }
  if (new Set(points.map(p => p.name)).size !== points.length) {
    return ElMessage.warning('巡检航点不能重复')
  }
  const payload = { ...f, waypoints: points }

  dialog.loading = true
  try {
    if (dialog.mode === 'add') {
      await request.post('/task', payload)
      ElMessage.success('新增任务成功')
    } else {
      await request.put('/task', payload)
      ElMessage.success('修改任务成功')
    }
    dialog.visible = false
    loadData()
  } catch {}
  dialog.loading = false
}

/* ====== 删除 ====== */
async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除任务 "${row.taskName}" 吗？该操作不可恢复。`,
      '危险操作',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete('/task/' + row.taskId)
    ElMessage.success('已删除')
    loadData()
  } catch {}
}

/* ====== 辅助 ====== */
function statusTag(s) {
  if (s === '待执行') return { text: s, type: 'info' }
  if (s === '执行中') return { text: s, type: 'warning' }
  if (s === '已完成') return { text: s, type: 'success' }
  if (s === '已取消') return { text: s, type: 'danger' }
  return { text: s || '--', type: 'info' }
}

function deviceLabel(id) {
  const d = deviceList.find(item => item.deviceId === id)
  if (!d) return id ? '#' + id : '--'
  return d.robotName ? `${d.deviceNum}（${d.robotName}）` : d.deviceNum
}

// 顶栏社区筛选变化时重新加载
watch(() => communityState.selectedId, () => {
  if (!isSuperAdmin) return
  table.pageNum = 1
  loadData()
  loadDevices()
})

onMounted(() => {
  if (isSuperAdmin) loadCommunities()
  loadDevices()
  loadData()
})
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-title">巡检任务</div>
      <div class="page-sub">
        <svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="4" width="18" height="16" rx="2"></rect>
          <line x1="3" y1="9" x2="21" y2="9"></line>
          <line x1="9" y1="14" x2="15" y2="14"></line>
        </svg>
        <span>巡检任务编排、下发与进度跟踪</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.taskName"
        placeholder="任务名称"
        clearable
        style="width: 180px;"
        @keyup.enter="onSearch"
      />
      <el-select v-model="searchForm.taskStatus" placeholder="任务状态" clearable style="width: 140px;">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增任务</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="table.data" v-loading="table.loading" stripe border empty-text="暂无任务">
        <el-table-column prop="taskId" label="ID" width="70" align="center" />
        <el-table-column prop="taskName" label="任务名称" min-width="180" />
        <el-table-column label="执行设备" min-width="160">
          <template #default="{ row }">{{ deviceLabel(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column prop="taskStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.taskStatus).type" size="small">
              {{ statusTag(row.taskStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="巡检点数" width="90" align="center">
          <template #default="{ row }">{{ (row.waypoints || []).length }}</template>
        </el-table-column>
        <el-table-column prop="loops" label="循环次数" width="90" align="center">
          <template #default="{ row }">{{ row.loops ?? '--' }}</template>
        </el-table-column>
        <el-table-column v-if="isSuperAdmin" label="所属社区" min-width="120">
          <template #default="{ row }">{{ communityName(row.communityId) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ row.createTime || '--' }}</template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" width="160">
          <template #default="{ row }">{{ row.endTime || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="table.total"
          :page-sizes="[10, 20, 50]"
          :page-size="table.pageSize"
          :current-page="table.pageNum"
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'add' ? '新增巡检任务' : '编辑巡检任务'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form :model="dialog.form" label-width="100px" :disabled="dialog.loading">
        <el-form-item v-if="isSuperAdmin" label="所属社区" required>
          <el-select v-model="dialog.form.communityId" placeholder="请选择社区" style="width: 100%;">
            <el-option
              v-for="c in communityState.list"
              :key="c.communityId"
              :label="c.communityName"
              :value="c.communityId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称" required>
          <el-input v-model="dialog.form.taskName" placeholder="如 夜间园区巡逻" />
        </el-form-item>
        <el-form-item label="执行设备" required>
          <el-select v-model="dialog.form.deviceId" placeholder="请选择设备" style="width: 100%;">
            <el-option
              v-for="d in deviceList"
              :key="d.deviceId"
              :label="d.robotName ? `${d.deviceNum}（${d.robotName}）` : d.deviceNum"
              :value="d.deviceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="dialog.form.taskStatus" style="width: 100%;">
            <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="循环次数">
          <el-input-number v-model="dialog.form.loops" :min="1" :max="99" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="dialog.form.endTime"
            type="datetime"
            placeholder="选择结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>

        <!-- 巡检路径点 -->
        <el-form-item label="巡检路径点">
          <div class="waypoint-box">
            <div v-for="(w, i) in dialog.form.waypoints" :key="i" class="waypoint-row">
              <span class="wp-order">{{ i + 1 }}</span>
              <el-select v-model="w.name" placeholder="选择航点" style="width: 130px;">
                <el-option v-for="p in PRESET_POINTS" :key="p" :label="p" :value="p" />
              </el-select>
              <el-checkbox v-model="w.autoPhoto">到达拍照</el-checkbox>
              <el-select v-model="w.photoType" :disabled="!w.autoPhoto" style="width: 118px;">
                <el-option v-for="t in PHOTO_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
              <el-input v-model="w.remark" placeholder="备注（可选）" style="flex: 1;" />
              <el-button link type="danger" :disabled="dialog.form.waypoints.length <= 1" @click="removeWaypoint(i)">
                删除
              </el-button>
            </div>
            <el-button link type="primary" :icon="Plus" @click="addWaypoint">添加路径点</el-button>
            <div class="wp-tip">按顺序执行；勾选「到达拍照」后，机器人到达该航点会通知 T5AI-Board 拍照并自动识别</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.loading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.waypoint-box {
  width: 100%;
}
.waypoint-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.wp-order {
  width: 22px;
  text-align: center;
  color: var(--text-sub);
  font-size: 13px;
}
.wp-tip {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
}
</style>
