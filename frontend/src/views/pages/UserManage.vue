<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { communityState, loadCommunities, communityName, getAuth } from '../../stores/community'

// 当前登录身份
const auth = getAuth()
const isSuperAdmin = auth.role === '超级管理员'
const myCommunityId = auth.communityId || null

/* ====== 表格数据 ====== */
const table = reactive({
  data: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  loading: false
})

// 搜索条件
const searchForm = reactive({ userName: '', role: '', accountStatus: '' })

async function loadData() {
  table.loading = true
  try {
    const params = { pageNum: table.pageNum, pageSize: table.pageSize }
    if (searchForm.userName) params.userName = searchForm.userName
    if (searchForm.role) params.role = searchForm.role
    if (searchForm.accountStatus) params.accountStatus = searchForm.accountStatus
    // 数据范围：超管看全部（可按顶栏社区筛选），管理员只看自己所属社区
    if (isSuperAdmin) {
      if (communityState.selectedId) params.communityId = communityState.selectedId
    } else if (myCommunityId) {
      params.communityId = myCommunityId
    }

    // request.js 自动解包 Result<T>，这里拿到的就是 IPage<SysUser>
    const res = await request.get('/sys-user/page', { params })
    table.data = res.records || []
    table.total = res.total || 0
  } catch {}
  table.loading = false
}

function onSearch() {
  table.pageNum = 1
  loadData()
}
function onReset() {
  searchForm.userName = ''
  searchForm.role = ''
  searchForm.accountStatus = ''
  onSearch()
}
function onPageChange(p) {
  table.pageNum = p
  loadData()
}
function onSizeChange(s) {
  table.pageSize = s
  table.pageNum = 1
  loadData()
}

/* ====== 新增/编辑弹窗 ====== */
const dialog = reactive({
  visible: false,
  mode: 'add',        // 'add' | 'edit'
  originalRole: '',   // 编辑前的角色（用于超管数量校验）
  form: {
    userId: null,
    userName: '',
    password: '',
    name: '',
    phone: '',
    role: '普通用户',
    accountStatus: '正常',
    communityId: null
  },
  loading: false
})

function openAdd() {
  dialog.mode = 'add'
  dialog.originalRole = ''
  dialog.form = {
    userId: null, userName: '', password: '', name: '',
    phone: '', role: '普通用户', accountStatus: '正常',
    // 管理员新增的用户固定归属自己所在社区；超管可选
    communityId: isSuperAdmin ? (communityState.selectedId || null) : myCommunityId
  }
  dialog.visible = true
}
function openEdit(row) {
  // 管理员不能编辑超级管理员账号
  if (!isSuperAdmin && row.role === '超级管理员') {
    return ElMessage.warning('管理员无权编辑超级管理员账号')
  }
  dialog.mode = 'edit'
  dialog.originalRole = row.role
  dialog.form = {
    userId: row.userId,
    userName: row.userName,
    password: '',   // 编辑时留空 = 不改密码
    name: row.name,
    phone: row.phone,
    role: row.role,
    accountStatus: row.accountStatus,
    communityId: row.communityId
  }
  dialog.visible = true
}
function closeDialog() { dialog.visible = false }

async function submitForm() {
  const f = dialog.form
  // 管理员不能把任何人（包括自己）设置为超级管理员
  if (!isSuperAdmin && f.role === '超级管理员') {
    f.role = '管理员'
    return ElMessage.warning('管理员无权设置超级管理员角色')
  }
  // 超级管理员新增/编辑用户必须指定所属社区
  if (isSuperAdmin && !f.communityId) {
    return ElMessage.warning('请选择所属社区')
  }
  // 基础校验
  if (!f.userName || f.userName.trim().length < 3) {
    return ElMessage.warning('账号至少 3 位')
  }
  if (dialog.mode === 'add' && (!f.password || f.password.length < 6)) {
    return ElMessage.warning('新用户密码至少 6 位')
  }
  if (dialog.mode === 'edit' && f.password && f.password.length < 6) {
    return ElMessage.warning('密码长度至少 6 位，留空则不修改')
  }
  // 最后一个超级管理员不能降级或禁用
  if (
    dialog.mode === 'edit' &&
    dialog.originalRole === '超级管理员' &&
    (f.role !== '超级管理员' || f.accountStatus === '禁用')
  ) {
    const n = await countSuperAdmins()
    if (n <= 1) {
      return ElMessage.warning('系统至少需要保留一个超级管理员，无法降级或禁用')
    }
  }

  dialog.loading = true
  try {
    if (dialog.mode === 'add') {
      await request.post('/sys-user', f)
      ElMessage.success('新增用户成功')
    } else {
      await request.put('/sys-user', f)
      ElMessage.success('修改用户成功')
    }
    dialog.visible = false
    loadData()
  } catch {}
  dialog.loading = false
}

/* ====== 删除 ====== */
async function doDelete(row) {
  if (!isSuperAdmin && row.role === '超级管理员') {
    return ElMessage.warning('管理员无权删除超级管理员账号')
  }
  if (row.role === '超级管理员') {
    const n = await countSuperAdmins()
    if (n <= 1) {
      return ElMessage.warning('系统至少需要保留一个超级管理员，无法删除')
    }
  }
  try {
    await ElMessageBox.confirm(
      `确认删除用户 "${row.userName}" 吗？该操作不可恢复。`,
      '危险操作',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete('/sys-user/' + row.userId)
    ElMessage.success('已删除')
    loadData()
  } catch {}
}

/* ====== 状态切换 ====== */
async function toggleStatus(row) {
  if (!isSuperAdmin && row.role === '超级管理员') {
    return ElMessage.warning('管理员无权操作超级管理员账号')
  }
  // 只剩一个超管时不允许禁用
  if (row.role === '超级管理员' && row.accountStatus === '正常') {
    const n = await countSuperAdmins()
    if (n <= 1) {
      return ElMessage.warning('系统至少需要保留一个超级管理员，无法禁用')
    }
  }
  const newStatus = row.accountStatus === '正常' ? '禁用' : '正常'
  try {
    await request.put('/sys-user', { userId: row.userId, accountStatus: newStatus })
    ElMessage.success('状态已更新')
    loadData()
  } catch {}
}

/* ====== 辅助 ====== */
// 统计系统内超级管理员数量（不受顶栏社区筛选影响）
async function countSuperAdmins() {
  try {
    const res = await request.get('/sys-user/page', {
      params: { pageNum: 1, pageSize: 1, role: '超级管理员' }
    })
    return res.total || 0
  } catch {
    return 0
  }
}

// 管理员不能选择超级管理员角色；超级管理员可选择全部角色
const roleOptions = computed(() =>
  isSuperAdmin
    ? ['普通用户', '管理员', '超级管理员']
    : ['普通用户', '管理员']
)
const statusOptions = ['正常', '禁用']

function roleTag(role) {
  if (role === '超级管理员') return { text: role, type: 'danger' }
  if (role === '管理员') return { text: role, type: 'warning' }
  return { text: role, type: 'info' }
}
function statusTag(s) {
  return s === '正常'
    ? { text: s, type: 'success' }
    : { text: s, type: 'danger' }
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
    <div class="page-title">用户管理</div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.userName" placeholder="账号关键字" clearable style="width: 180px;" @keyup.enter="onSearch" />
      <el-select v-model="searchForm.role" placeholder="角色" clearable style="width: 140px;">
        <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
      </el-select>
      <el-select v-model="searchForm.accountStatus" placeholder="状态" clearable style="width: 140px;">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增用户</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="table.data" v-loading="table.loading" stripe border empty-text="暂无用户">
        <el-table-column prop="userId" label="ID" width="70" align="center" />
        <el-table-column prop="userName" label="账号" min-width="140" />
        <el-table-column prop="name" label="姓名" min-width="120">
          <template #default="{ row }">{{ row.name || '--' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140">
          <template #default="{ row }">{{ row.phone || '--' }}</template>
        </el-table-column>
        <el-table-column v-if="isSuperAdmin" label="所属社区" min-width="140">
          <template #default="{ row }">{{ communityName(row.communityId) }}</template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role).type" size="small">{{ roleTag(row.role).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="accountStatus" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.accountStatus).type" size="small">{{ statusTag(row.accountStatus).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ row.createTime || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.accountStatus === '正常' ? 'warning' : 'success'"
              size="small"
              @click="toggleStatus(row)"
            >{{ row.accountStatus === '正常' ? '禁用' : '启用' }}</el-button>
            <el-button link type="danger" size="small" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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
      :title="dialog.mode === 'add' ? '新增用户' : '编辑用户'"
      width="520px"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <el-form :model="dialog.form" label-width="90px" :disabled="dialog.loading">
        <el-form-item label="账号" required>
          <el-input v-model="dialog.form.userName" :disabled="dialog.mode === 'edit'" placeholder="字母/数字/下划线，至少 3 位" />
        </el-form-item>
        <el-form-item :label="dialog.mode === 'add' ? '密码' : '新密码'">
          <el-input
            v-model="dialog.form.password"
            type="password"
            show-password
            :placeholder="dialog.mode === 'add' ? '至少 6 位' : '留空则不修改'"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="dialog.form.name" placeholder="可选" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="dialog.form.phone" placeholder="可选" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="dialog.form.role" style="width: 100%;">
            <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
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
        <el-form-item label="状态" required>
          <el-select v-model="dialog.form.accountStatus" style="width: 100%;">
            <el-option label="正常" value="正常" />
            <el-option label="禁用" value="禁用" />
          </el-select>
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
  color: #1E3A40;
  margin-bottom: 16px;
  letter-spacing: 1px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 10px;
  padding: 14px 18px;
  box-shadow: 0 1px 3px rgba(15, 30, 40, 0.05);
  margin-bottom: 14px;
}
.spacer { flex: 1; }

.table-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px;
  box-shadow: 0 1px 3px rgba(15, 30, 40, 0.05);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>

