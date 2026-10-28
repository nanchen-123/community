<script setup>
import { reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { communityState, loadCommunities, communityName, getAuth } from '../../stores/community'

// 当前登录身份
const auth = getAuth()
const isSuperAdmin = auth.role === '超级管理员'
const myCommunityId = auth.communityId || null

const typeOptions = ['人员异常', '消防安全', '其他']
const levelOptions = ['高', '中', '低']
const handleOptions = ['未处理', '已处理', '已忽略']
const recognizeOptions = ['识别成功', '识别失败', '识别中']

/* ====== 表格数据 ====== */
const table = reactive({
  data: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  loading: false
})

const searchForm = reactive({ alarmType: '', level: '', handleStatus: '' })

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
    if (searchForm.alarmType) params.alarmType = searchForm.alarmType
    if (searchForm.level) params.level = searchForm.level
    if (searchForm.handleStatus) params.handleStatus = searchForm.handleStatus
    // 数据范围：超管看全部（可按顶栏社区筛选），管理员只看自己所属社区
    if (isSuperAdmin) {
      if (communityState.selectedId) params.communityId = communityState.selectedId
    } else if (myCommunityId) {
      params.communityId = myCommunityId
    }
    const res = await request.get('/alarm/page', { params })
    table.data = res.records || []
    table.total = res.total || 0
  } catch {}
  table.loading = false
}

function onSearch() { table.pageNum = 1; loadData() }
function onReset() {
  searchForm.alarmType = ''
  searchForm.level = ''
  searchForm.handleStatus = ''
  onSearch()
}
function onPageChange(p) { table.pageNum = p; loadData() }
function onSizeChange(s) { table.pageSize = s; table.pageNum = 1; loadData() }

/* ====== 新增/编辑弹窗 ====== */
const dialog = reactive({
  visible: false,
  mode: 'add',
  form: {
    alarmId: null,
    communityId: null,
    deviceId: null,
    taskId: null,
    alarmType: '',
    location: '',
    handleStatus: '未处理',
    findTime: null,
    handleTime: null,
    level: '中',
    recognizeStatus: '识别成功',
    imgUrl: '',
    remark: ''
  },
  loading: false
})

function openAdd() {
  dialog.mode = 'add'
  dialog.form = {
    alarmId: null,
    communityId: isSuperAdmin ? (communityState.selectedId || null) : myCommunityId,
    deviceId: null,
    taskId: null,
    alarmType: '',
    location: '',
    handleStatus: '未处理',
    findTime: null,
    handleTime: null,
    level: '中',
    recognizeStatus: '识别成功',
    imgUrl: '',
    remark: ''
  }
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.form = {
    alarmId: row.alarmId,
    communityId: row.communityId,
    deviceId: row.deviceId,
    taskId: row.taskId,
    alarmType: row.alarmType,
    location: row.location,
    handleStatus: row.handleStatus || '未处理',
    findTime: row.findTime,
    handleTime: row.handleTime,
    level: row.level || '中',
    recognizeStatus: row.recognizeStatus || '识别成功',
    imgUrl: row.imgUrl || '',
    remark: row.remark || ''
  }
  dialog.visible = true
}

function nowStr() {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function submitForm() {
  const f = dialog.form
  if (!f.alarmType) {
    return ElMessage.warning('请选择告警类型')
  }
  if (isSuperAdmin && !f.communityId) {
    return ElMessage.warning('请选择所属社区')
  }
  if (!f.findTime) {
    f.findTime = nowStr()
  }
  // 标记为已处理时自动补处理时间
  if (f.handleStatus === '已处理' && !f.handleTime) {
    f.handleTime = nowStr()
  }
  dialog.loading = true
  try {
    if (dialog.mode === 'add') {
      await request.post('/alarm', f)
      ElMessage.success('新增告警成功')
    } else {
      await request.put('/alarm', f)
      ElMessage.success('修改告警成功')
    }
    dialog.visible = false
    loadData()
  } catch {}
  dialog.loading = false
}

/* ====== 快速处理 ====== */
async function quickHandle(row) {
  try {
    await ElMessageBox.confirm(
      `确认将告警 #${row.alarmId}（${row.alarmType || '未分类'}）标记为已处理？`,
      '处理告警',
      { type: 'info', confirmButtonText: '确认处理', cancelButtonText: '取消' }
    )
    await request.put('/alarm', {
      alarmId: row.alarmId,
      handleStatus: '已处理',
      handleTime: nowStr()
    })
    ElMessage.success('已标记为处理完成')
    loadData()
  } catch {}
}

/* ====== 删除 ====== */
async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除告警 #${row.alarmId} 吗？该操作不可恢复。`,
      '危险操作',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete('/alarm/' + row.alarmId)
    ElMessage.success('已删除')
    loadData()
  } catch {}
}

/* ====== 辅助 ====== */
function levelTag(l) {
  if (l === '高') return { text: l, type: 'danger' }
  if (l === '中') return { text: l, type: 'warning' }
  if (l === '低') return { text: l, type: 'info' }
  return { text: l || '--', type: 'info' }
}
function handleTag(s) {
  if (s === '未处理') return { text: s, type: 'danger' }
  if (s === '已处理') return { text: s, type: 'success' }
  if (s === '已忽略') return { text: s, type: 'info' }
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
  // 所属社区列对所有身份展示，统一加载社区列表用于名称映射
  loadCommunities()
  loadDevices()
  loadData()
})
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-title">告警管理</div>
      <div class="page-sub">
        <svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
          <line x1="12" y1="9" x2="12" y2="13"></line>
          <line x1="12" y1="17" x2="12.01" y2="17"></line>
        </svg>
        <span>告警记录、级别与处置状态跟踪</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-select v-model="searchForm.alarmType" placeholder="告警类型" clearable style="width: 150px;">
        <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="searchForm.level" placeholder="告警级别" clearable style="width: 130px;">
        <el-option v-for="l in levelOptions" :key="l" :label="l" :value="l" />
      </el-select>
      <el-select v-model="searchForm.handleStatus" placeholder="处理状态" clearable style="width: 130px;">
        <el-option v-for="s in handleOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增告警</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="table.data" v-loading="table.loading" stripe border empty-text="暂无告警">
        <el-table-column prop="alarmId" label="编号" width="80" align="center" />
        <el-table-column prop="alarmType" label="告警类型" min-width="120">
          <template #default="{ row }">{{ row.alarmType || '--' }}</template>
        </el-table-column>
        <el-table-column label="抓拍图片" width="100" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.imgUrl"
              :src="row.imgUrl"
              :preview-src-list="[row.imgUrl]"
              preview-teleported
              fit="cover"
              style="width: 56px; height: 40px; border-radius: 4px;"
            />
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column label="抓拍设备" min-width="150">
          <template #default="{ row }">{{ deviceLabel(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column label="所属社区" min-width="120">
          <template #default="{ row }">{{ communityName(row.communityId) }}</template>
        </el-table-column>
        <el-table-column prop="location" label="机器人位置" min-width="150">
          <template #default="{ row }">{{ row.location || '--' }}</template>
        </el-table-column>
        <el-table-column prop="findTime" label="发现时间" width="160">
          <template #default="{ row }">{{ row.findTime || '--' }}</template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level).type" size="small" effect="dark">
              {{ levelTag(row.level).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleStatus" label="处理状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="handleTag(row.handleStatus).type" size="small">
              {{ handleTag(row.handleStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.handleStatus === '未处理'"
              link
              type="success"
              size="small"
              @click="quickHandle(row)"
            >处理</el-button>
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
      :title="dialog.mode === 'add' ? '新增告警' : '编辑告警'"
      width="580px"
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
        <el-form-item label="告警类型" required>
          <el-select v-model="dialog.form.alarmType" placeholder="请选择类型" style="width: 100%;">
            <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警级别">
          <el-select v-model="dialog.form.level" style="width: 100%;">
            <el-option v-for="l in levelOptions" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="dialog.form.location" placeholder="如 3 号楼东侧消防通道" />
        </el-form-item>
        <el-form-item label="执行设备">
          <el-select v-model="dialog.form.deviceId" placeholder="请选择设备" clearable style="width: 100%;">
            <el-option
              v-for="d in deviceList"
              :key="d.deviceId"
              :label="d.robotName ? `${d.deviceNum}（${d.robotName}）` : d.deviceNum"
              :value="d.deviceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="识别情况">
          <el-select v-model="dialog.form.recognizeStatus" style="width: 100%;">
            <el-option v-for="r in recognizeOptions" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="dialog.form.handleStatus" style="width: 100%;">
            <el-option v-for="s in handleOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="发现时间">
          <el-date-picker
            v-model="dialog.form.findTime"
            type="datetime"
            placeholder="默认当前时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="处理时间">
          <el-date-picker
            v-model="dialog.form.handleTime"
            type="datetime"
            placeholder="处理时自动填写"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="图片 URL">
          <el-input v-model="dialog.form.imgUrl" placeholder="如 /uploads/xxx.jpg" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" placeholder="可选" />
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
</style>
