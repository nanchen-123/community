<script setup>
import { reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { communityState, loadCommunities, communityName, getAuth } from '../../stores/community'

// 当前登录身份
const auth = getAuth()
const isSuperAdmin = auth.role === '超级管理员'
const myCommunityId = auth.communityId || null

const handleOptions = ['未处理', '已处理', '已忽略']

/* ====== 表格数据 ====== */
const table = reactive({
  data: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  loading: false
})

const searchForm = reactive({ plateNumber: '', handleStatus: '' })

// 任务下拉（按当前身份的社区范围加载）
const taskList = reactive([])

function communityParams() {
  const params = { pageNum: 1, pageSize: 500 }
  if (isSuperAdmin) {
    if (communityState.selectedId) params.communityId = communityState.selectedId
  } else if (myCommunityId) {
    params.communityId = myCommunityId
  }
  return params
}

async function loadOptions() {
  try {
    const t = await request.get('/task/page', { params: communityParams() })
    taskList.length = 0
    taskList.push(...(t.records || []))
  } catch {}
}

async function loadData() {
  table.loading = true
  try {
    const params = { pageNum: table.pageNum, pageSize: table.pageSize }
    if (searchForm.plateNumber) params.plateNumber = searchForm.plateNumber
    if (searchForm.handleStatus) params.handleStatus = searchForm.handleStatus
    // 数据范围：超管看全部（可按顶栏社区筛选），管理员只看自己所属社区
    if (isSuperAdmin) {
      if (communityState.selectedId) params.communityId = communityState.selectedId
    } else if (myCommunityId) {
      params.communityId = myCommunityId
    }
    const res = await request.get('/license-plate/page', { params })
    table.data = res.records || []
    table.total = res.total || 0
  } catch {}
  table.loading = false
}

function onSearch() { table.pageNum = 1; loadData() }
function onReset() {
  searchForm.plateNumber = ''
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
    plateId: null,
    communityId: null,
    taskId: null,
    plateNumber: '',
    imgUrl: '',
    location: '',
    handleStatus: '未处理',
    remark: ''
  },
  loading: false
})

function openAdd() {
  dialog.mode = 'add'
  dialog.form = {
    plateId: null,
    communityId: isSuperAdmin ? (communityState.selectedId || null) : myCommunityId,
    taskId: null,
    plateNumber: '',
    imgUrl: '',
    location: '',
    handleStatus: '未处理',
    remark: ''
  }
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.form = {
    plateId: row.plateId,
    communityId: row.communityId,
    taskId: row.taskId,
    plateNumber: row.plateNumber,
    imgUrl: row.imgUrl || '',
    location: row.location || '',
    handleStatus: row.handleStatus || '未处理',
    remark: row.remark || ''
  }
  dialog.visible = true
}

async function submitForm() {
  const f = dialog.form
  if (!f.plateNumber || !f.plateNumber.trim()) {
    return ElMessage.warning('请输入车牌号')
  }
  if (isSuperAdmin && !f.communityId) {
    return ElMessage.warning('请选择所属社区')
  }
  dialog.loading = true
  try {
    if (dialog.mode === 'add') {
      await request.post('/license-plate', f)
      ElMessage.success('新增识别记录成功')
    } else {
      await request.put('/license-plate', f)
      ElMessage.success('修改识别记录成功')
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
      `确认将车牌 "${row.plateNumber}" 的识别记录标记为已处理？`,
      '处理识别记录',
      { type: 'info', confirmButtonText: '确认处理', cancelButtonText: '取消' }
    )
    await request.put('/license-plate', { plateId: row.plateId, handleStatus: '已处理' })
    ElMessage.success('已标记为处理完成')
    loadData()
  } catch {}
}

/* ====== 删除 ====== */
async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除车牌 "${row.plateNumber}" 的识别记录吗？该操作不可恢复。`,
      '危险操作',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete('/license-plate/' + row.plateId)
    ElMessage.success('已删除')
    loadData()
  } catch {}
}

/* ====== 辅助 ====== */
function handleTag(s) {
  if (s === '未处理') return { text: s, type: 'danger' }
  if (s === '已处理') return { text: s, type: 'success' }
  if (s === '已忽略') return { text: s, type: 'info' }
  return { text: s || '--', type: 'info' }
}
function taskLabel(id) {
  if (!id) return '--'
  const t = taskList.find(item => item.taskId === id)
  return t ? t.taskName : '#' + id
}

// 顶栏社区筛选变化时重新加载
watch(() => communityState.selectedId, () => {
  if (!isSuperAdmin) return
  table.pageNum = 1
  loadData()
  loadOptions()
})

onMounted(() => {
  if (isSuperAdmin) loadCommunities()
  loadOptions()
  loadData()
})
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-title">车牌识别</div>
      <div class="page-sub">
        <svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 16H9m10 0h3v-3.15a1 1 0 0 0-.84-.99L16 11l-2.7-3.6a1 1 0 0 0-.8-.4H5.24a2 2 0 0 0-1.8 1.1l-.8 1.63A6 6 0 0 0 2 12.42V16h2"></path>
          <circle cx="6.5" cy="16.5" r="2.5"></circle>
          <circle cx="16.5" cy="16.5" r="2.5"></circle>
        </svg>
        <span>车牌抓拍记录与识别结果管理</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.plateNumber"
        placeholder="车牌号"
        clearable
        style="width: 180px;"
        @keyup.enter="onSearch"
      />
      <el-select v-model="searchForm.handleStatus" placeholder="处理状态" clearable style="width: 140px;">
        <el-option v-for="s in handleOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增记录</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="table.data" v-loading="table.loading" stripe border empty-text="暂无识别记录">
        <el-table-column prop="plateId" label="ID" width="70" align="center" />
        <el-table-column prop="plateNumber" label="车牌号" width="130">
          <template #default="{ row }">
            <span class="plate-num">{{ row.plateNumber || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="抓拍图片" width="90" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.imgUrl"
              :src="row.imgUrl"
              :preview-src-list="[row.imgUrl]"
              preview-teleported
              fit="cover"
              style="width: 48px; height: 36px; border-radius: 4px;"
            />
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" min-width="150">
          <template #default="{ row }">{{ row.location || '--' }}</template>
        </el-table-column>
        <el-table-column label="关联任务" min-width="140">
          <template #default="{ row }">{{ taskLabel(row.taskId) }}</template>
        </el-table-column>
        <el-table-column prop="handleStatus" label="处理状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="handleTag(row.handleStatus).type" size="small">
              {{ handleTag(row.handleStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isSuperAdmin" label="所属社区" min-width="120">
          <template #default="{ row }">{{ communityName(row.communityId) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="识别时间" width="160">
          <template #default="{ row }">{{ row.createTime || '--' }}</template>
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
      :title="dialog.mode === 'add' ? '新增识别记录' : '编辑识别记录'"
      width="560px"
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
        <el-form-item label="车牌号" required>
          <el-input v-model="dialog.form.plateNumber" placeholder="如 苏A12345" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="dialog.form.location" placeholder="如 南门入口" />
        </el-form-item>
        <el-form-item label="关联任务">
          <el-select v-model="dialog.form.taskId" placeholder="请选择任务" clearable style="width: 100%;">
            <el-option v-for="t in taskList" :key="t.taskId" :label="t.taskName" :value="t.taskId" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="dialog.form.handleStatus" style="width: 100%;">
            <el-option v-for="s in handleOptions" :key="s" :label="s" :value="s" />
          </el-select>
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
.plate-num {
  font-weight: 600;
  color: var(--primary);
  letter-spacing: 1px;
}
</style>
