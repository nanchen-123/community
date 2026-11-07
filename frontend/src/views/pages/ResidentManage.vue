<script setup>
import { reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { communityState, loadCommunities, communityName, getAuth } from '../../stores/community'

// 当前登录身份
const auth = getAuth()
const isSuperAdmin = auth.role === '超级管理员'
// 管理端（管理员/超级管理员）才可新增居民，普通用户仅查看
const isAdminView = auth.role === '超级管理员' || auth.role === '管理员'
const myCommunityId = auth.communityId || null

/* ====== 表格数据 ====== */
const table = reactive({
  data: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  loading: false
})

const searchForm = reactive({ householdName: '', phone: '', residenceStatus: '' })

async function loadData() {
  table.loading = true
  try {
    const params = { pageNum: table.pageNum, pageSize: table.pageSize }
    if (searchForm.householdName) params.householdName = searchForm.householdName
    if (searchForm.phone) params.phone = searchForm.phone
    if (searchForm.residenceStatus) params.residenceStatus = searchForm.residenceStatus
    // 数据范围：超管看全部（可按顶栏社区筛选），管理员只看自己所属社区
    if (isSuperAdmin) {
      if (communityState.selectedId) params.communityId = communityState.selectedId
    } else if (myCommunityId) {
      params.communityId = myCommunityId
    }
    const res = await request.get('/resident/page', { params })
    table.data = res.records || []
    table.total = res.total || 0
  } catch {}
  table.loading = false
}

function onSearch() { table.pageNum = 1; loadData() }
function onReset() {
  searchForm.householdName = ''
  searchForm.phone = ''
  searchForm.residenceStatus = ''
  onSearch()
}
function onPageChange(p) { table.pageNum = p; loadData() }
function onSizeChange(s) { table.pageSize = s; table.pageNum = 1; loadData() }

/* ====== 新增/编辑弹窗 ====== */
const dialog = reactive({
  visible: false,
  mode: 'add',
  form: {
    householdId: null,
    communityId: null,
    householdName: '',
    phone: '',
    roomNo: '',
    plateNumber: '',
    familyNum: null,
    residenceStatus: '正常',
    deadline: null,
    remark: ''
  },
  loading: false
})

function openAdd() {
  dialog.mode = 'add'
  dialog.form = {
    householdId: null,
    communityId: isSuperAdmin ? (communityState.selectedId || null) : myCommunityId,
    householdName: '',
    phone: '',
    roomNo: '',
    plateNumber: '',
    familyNum: null,
    residenceStatus: '正常',
    deadline: null,
    remark: ''
  }
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.form = {
    householdId: row.householdId,
    communityId: row.communityId,
    householdName: row.householdName,
    phone: row.phone,
    roomNo: row.roomNo,
    plateNumber: row.plateNumber,
    familyNum: row.familyNum,
    residenceStatus: row.residenceStatus,
    deadline: row.deadline,
    remark: row.remark
  }
  dialog.visible = true
}

async function submitForm() {
  const f = dialog.form
  if (!f.householdName || !f.householdName.trim()) {
    return ElMessage.warning('请输入住户姓名')
  }
  if (isSuperAdmin && !f.communityId) {
    return ElMessage.warning('请选择所属社区')
  }
  if (f.phone && !/^1[3-9]\d{9}$/.test(f.phone)) {
    return ElMessage.warning('请输入正确的手机号')
  }
  dialog.loading = true
  try {
    if (dialog.mode === 'add') {
      await request.post('/resident', f)
      ElMessage.success('新增住户成功')
    } else {
      await request.put('/resident', f)
      ElMessage.success('修改住户成功')
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
      `确认删除住户 "${row.householdName}" 吗？该操作不可恢复。`,
      '危险操作',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete('/resident/' + row.householdId)
    ElMessage.success('已删除')
    loadData()
  } catch {}
}

/* ====== 辅助 ====== */
const statusOptions = ['正常', '居住', '迁出']

function statusTag(s) {
  if (s === '正常') return { text: s, type: 'success' }
  if (s === '居住') return { text: s, type: 'primary' }
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
    <div class="page-header">
      <div class="page-title">居民管理</div>
      <div class="page-sub">
        <svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
          <circle cx="9" cy="7" r="4"></circle>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
        </svg>
        <span>社区居民信息与居住状态管理</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.householdName"
        placeholder="住户姓名"
        clearable
        style="width: 160px;"
        @keyup.enter="onSearch"
      />
      <el-input
        v-model="searchForm.phone"
        placeholder="手机号"
        clearable
        style="width: 160px;"
        @keyup.enter="onSearch"
      />
      <el-select v-model="searchForm.residenceStatus" placeholder="居住状态" clearable style="width: 140px;">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <div class="spacer"></div>
      <el-button v-if="isAdminView" type="primary" @click="openAdd">新增居民</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="table.data" v-loading="table.loading" stripe border empty-text="暂无住户">
        <el-table-column prop="householdId" label="ID" width="70" align="center" />
        <el-table-column prop="householdName" label="姓名" min-width="110" />
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '--' }}</template>
        </el-table-column>
        <el-table-column prop="roomNo" label="房间号" min-width="110">
          <template #default="{ row }">{{ row.roomNo || '--' }}</template>
        </el-table-column>
        <el-table-column prop="plateNumber" label="车牌号" width="130">
          <template #default="{ row }">{{ row.plateNumber || '--' }}</template>
        </el-table-column>
        <el-table-column prop="familyNum" label="家庭人数" width="90" align="center">
          <template #default="{ row }">{{ row.familyNum ?? '--' }}</template>
        </el-table-column>
        <el-table-column prop="residenceStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.residenceStatus).type" size="small">
              {{ statusTag(row.residenceStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isSuperAdmin" label="所属社区" min-width="130">
          <template #default="{ row }">{{ communityName(row.communityId) }}</template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="160">
          <template #default="{ row }">{{ row.deadline || '--' }}</template>
        </el-table-column>
        <el-table-column v-if="isAdminView" label="操作" width="160" fixed="right" align="center">
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
      :title="dialog.mode === 'add' ? '新增住户' : '编辑住户'"
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
        <el-form-item label="姓名" required>
          <el-input v-model="dialog.form.householdName" placeholder="请输入住户姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="dialog.form.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="房间号">
          <el-input v-model="dialog.form.roomNo" placeholder="如 3 栋 2 单元 501" />
        </el-form-item>
        <el-form-item label="车牌号">
          <el-input v-model="dialog.form.plateNumber" placeholder="如 苏A12345" />
        </el-form-item>
        <el-form-item label="家庭人数">
          <el-input-number v-model="dialog.form.familyNum" :min="0" :max="99" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="居住状态">
          <el-select v-model="dialog.form.residenceStatus" style="width: 100%;">
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
