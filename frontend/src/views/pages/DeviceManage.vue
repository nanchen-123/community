<script setup>
import { reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { communityState, loadCommunities, communityName, getAuth } from '../../stores/community'

// 当前登录身份
const auth = getAuth()
const isSuperAdmin = auth.role === '超级管理员'
const myCommunityId = auth.communityId || null

const statusOptions = ['在线', '离线', '维护']

/* ====== 表格数据 ====== */
const table = reactive({
  data: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  loading: false
})

const searchForm = reactive({ deviceNum: '', robotName: '', deviceStatus: '' })

async function loadData() {
  table.loading = true
  try {
    const params = { pageNum: table.pageNum, pageSize: table.pageSize }
    if (searchForm.deviceNum) params.deviceNum = searchForm.deviceNum
    if (searchForm.robotName) params.robotName = searchForm.robotName
    if (searchForm.deviceStatus) params.deviceStatus = searchForm.deviceStatus
    // 数据范围：超管看全部（可按顶栏社区筛选），管理员只看自己所属社区
    if (isSuperAdmin) {
      if (communityState.selectedId) params.communityId = communityState.selectedId
    } else if (myCommunityId) {
      params.communityId = myCommunityId
    }
    const res = await request.get('/device/page', { params })
    table.data = res.records || []
    table.total = res.total || 0
  } catch {}
  table.loading = false
}

function onSearch() { table.pageNum = 1; loadData() }
function onReset() {
  searchForm.deviceNum = ''
  searchForm.robotName = ''
  searchForm.deviceStatus = ''
  onSearch()
}
function onPageChange(p) { table.pageNum = p; loadData() }
function onSizeChange(s) { table.pageSize = s; table.pageNum = 1; loadData() }

/* ====== 新增/编辑弹窗 ====== */
const dialog = reactive({
  visible: false,
  mode: 'add',
  form: {
    deviceId: null,
    communityId: null,
    deviceNum: '',
    robotName: '',
    deviceStatus: '离线',
    deadline: null
  },
  loading: false
})

function openAdd() {
  dialog.mode = 'add'
  dialog.form = {
    deviceId: null,
    communityId: isSuperAdmin ? (communityState.selectedId || null) : myCommunityId,
    deviceNum: '',
    robotName: '',
    deviceStatus: '离线',
    deadline: null
  }
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.form = {
    deviceId: row.deviceId,
    communityId: row.communityId,
    deviceNum: row.deviceNum,
    robotName: row.robotName,
    deviceStatus: row.deviceStatus || '离线',
    deadline: row.deadline
  }
  dialog.visible = true
}

async function submitForm() {
  const f = dialog.form
  if (!f.deviceNum || !f.deviceNum.trim()) {
    return ElMessage.warning('请输入设备号')
  }
  if (isSuperAdmin && !f.communityId) {
    return ElMessage.warning('请选择所属社区')
  }
  dialog.loading = true
  try {
    if (dialog.mode === 'add') {
      await request.post('/device', f)
      ElMessage.success('新增设备成功')
    } else {
      await request.put('/device', f)
      ElMessage.success('修改设备成功')
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
      `确认删除设备 "${row.deviceNum}" 吗？该操作不可恢复。`,
      '危险操作',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete('/device/' + row.deviceId)
    ElMessage.success('已删除')
    loadData()
  } catch {}
}

/* ====== 辅助 ====== */
function statusTag(s) {
  if (s === '在线') return { text: s, type: 'success' }
  if (s === '离线') return { text: s, type: 'info' }
  if (s === '维护') return { text: s, type: 'warning' }
  return { text: s || '--', type: 'info' }
}

// 顶栏社区筛选变化时重新加载
watch(() => communityState.selectedId, () => {
  if (!isSuperAdmin) return
  table.pageNum = 1
  loadData()
})

onMounted(() => {
  if (isSuperAdmin) loadCommunities()
  loadData()
})
</script>

<template>
  <div>
    <div class="page-title">设备管理</div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.deviceNum"
        placeholder="设备号"
        clearable
        style="width: 160px;"
        @keyup.enter="onSearch"
      />
      <el-input
        v-model="searchForm.robotName"
        placeholder="机器人名称"
        clearable
        style="width: 160px;"
        @keyup.enter="onSearch"
      />
      <el-select v-model="searchForm.deviceStatus" placeholder="设备状态" clearable style="width: 140px;">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增设备</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="table.data" v-loading="table.loading" stripe border empty-text="暂无设备">
        <el-table-column prop="deviceId" label="ID" width="70" align="center" />
        <el-table-column prop="deviceNum" label="设备号" min-width="150" />
        <el-table-column prop="robotName" label="机器人名称" min-width="140">
          <template #default="{ row }">{{ row.robotName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="deviceStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.deviceStatus).type" size="small">
              {{ statusTag(row.deviceStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isSuperAdmin" label="所属社区" min-width="130">
          <template #default="{ row }">{{ communityName(row.communityId) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ row.createTime || '--' }}</template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="160">
          <template #default="{ row }">{{ row.deadline || '--' }}</template>
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
      :title="dialog.mode === 'add' ? '新增设备' : '编辑设备'"
      width="520px"
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
        <el-form-item label="设备号" required>
          <el-input v-model="dialog.form.deviceNum" placeholder="如 ROBOT-001" />
        </el-form-item>
        <el-form-item label="机器人名称">
          <el-input v-model="dialog.form.robotName" placeholder="如 1 号巡检机器人" />
        </el-form-item>
        <el-form-item label="设备状态">
          <el-select v-model="dialog.form.deviceStatus" style="width: 100%;">
            <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="dialog.form.deadline"
            type="datetime"
            placeholder="选择截止时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
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
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 16px;
  letter-spacing: 1px;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card-bg);
  border-radius: 10px;
  padding: 14px 18px;
  box-shadow: 0 1px 3px var(--shadow);
  margin-bottom: 14px;
}
.spacer { flex: 1; }
.table-card {
  background: var(--card-bg);
  border-radius: 10px;
  padding: 18px;
  box-shadow: 0 1px 3px var(--shadow);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
