<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { loadCommunities } from '../../stores/community'

const table = reactive({
  data: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  loading: false
})

const searchForm = reactive({ communityName: '' })

async function loadData() {
  table.loading = true
  try {
    const params = { pageNum: table.pageNum, pageSize: table.pageSize }
    if (searchForm.communityName) params.communityName = searchForm.communityName
    const res = await request.get('/community/page', { params })
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
  searchForm.communityName = ''
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

/* ====== 新增/编辑 ====== */
const dialog = reactive({
  visible: false,
  mode: 'add',
  form: { communityId: null, communityName: '', address: '' },
  loading: false
})

function openAdd() {
  dialog.mode = 'add'
  dialog.form = { communityId: null, communityName: '', address: '' }
  dialog.visible = true
}
function openEdit(row) {
  dialog.mode = 'edit'
  dialog.form = {
    communityId: row.communityId,
    communityName: row.communityName,
    address: row.address
  }
  dialog.visible = true
}

async function submitForm() {
  const f = dialog.form
  if (!f.communityName || !f.communityName.trim()) {
    return ElMessage.warning('请输入小区名称')
  }
  dialog.loading = true
  try {
    if (dialog.mode === 'add') {
      await request.post('/community', f)
      ElMessage.success('新增小区成功')
    } else {
      await request.put('/community', f)
      ElMessage.success('修改小区成功')
    }
    dialog.visible = false
    loadData()
    loadCommunities(true) // 刷新顶栏社区筛选下拉
  } catch {}
  dialog.loading = false
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除小区 "${row.communityName}" 吗？该操作不可恢复。`,
      '危险操作',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete('/community/' + row.communityId)
    ElMessage.success('已删除')
    loadData()
    loadCommunities(true)
  } catch {}
}

onMounted(loadData)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-title">社区管理</div>
      <div class="page-sub">
        <svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 21h18"></path>
          <path d="M5 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16"></path>
          <path d="M15 9h2a2 2 0 0 1 2 2v10"></path>
          <line x1="9" y1="7" x2="9" y2="7.01"></line>
          <line x1="9" y1="11" x2="9" y2="11.01"></line>
          <line x1="9" y1="15" x2="9" y2="15.01"></line>
        </svg>
        <span>社区信息与数据范围管理</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchForm.communityName"
        placeholder="小区名称"
        clearable
        style="width: 200px;"
        @keyup.enter="onSearch"
      />
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button :icon="Refresh" @click="onReset">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" :icon="Plus" @click="openAdd">新增小区</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="table.data" v-loading="table.loading" stripe border empty-text="暂无小区">
        <el-table-column prop="communityId" label="ID" width="80" align="center" />
        <el-table-column prop="communityName" label="小区名称" min-width="180" />
        <el-table-column prop="address" label="地址" min-width="260">
          <template #default="{ row }">{{ row.address || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
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
      :title="dialog.mode === 'add' ? '新增小区' : '编辑小区'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="dialog.form" label-width="90px" :disabled="dialog.loading">
        <el-form-item label="小区名称" required>
          <el-input v-model="dialog.form.communityName" placeholder="请输入小区名称" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="dialog.form.address" type="textarea" :rows="3" placeholder="请输入小区地址" />
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
