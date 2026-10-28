<template>
	<div class="page-wrap">
		<!-- 页面标题 -->
		<div class="page-header">
			<div class="page-title">安防台账</div>
			<div class="page-sub">
				<svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<rect x="3" y="4" width="18" height="16" rx="2"></rect>
					<line x1="3" y1="9" x2="21" y2="9"></line>
					<line x1="9" y1="14" x2="15" y2="14"></line>
				</svg>
				<span>隐患告警与车牌台账一体化管理</span>
			</div>
		</div>

		<!-- 顶部筛选区 -->
		<div class="filter-card">
			<div class="filter-row">
				<div class="date-range">
					<label class="filter-label">日期</label>
					<input type="date" v-model="filter.dateStart" class="filter-input date-input" />
					<span class="date-sep">至</span>
					<input type="date" v-model="filter.dateEnd" class="filter-input date-input" />
				</div>
				<div class="filter-item">
					<label class="filter-label">告警类型</label>
					<select v-model="filter.alarmType" class="filter-select">
						<option value="">全部</option>
						<option v-for="t in alarmTypeOptions" :key="t" :value="t">{{ t }}</option>
					</select>
				</div>
				<div class="filter-item">
					<label class="filter-label">车牌</label>
					<input v-model="filter.plate" type="text" placeholder="搜索车牌号..." class="filter-input plate-input" />
				</div>
				<div class="filter-item">
					<label class="filter-label">处理状态</label>
					<select v-model="filter.handleStatus" class="filter-select">
						<option value="">全部</option>
						<option v-for="s in handleOptions" :key="s" :value="s">{{ s }}</option>
					</select>
				</div>
				<div class="filter-actions">
					<button class="btn btn-reset" @click="onReset">
						<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
							<polyline points="1 4 1 10 7 10"></polyline>
							<path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path>
						</svg>
						<span>重置</span>
					</button>
					<button class="btn btn-export" @click="onExportExcel">
						<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
							<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
							<polyline points="7 10 12 15 17 10"></polyline>
							<line x1="12" y1="15" x2="12" y2="3"></line>
						</svg>
						<span>导出 Excel</span>
					</button>
				</div>
			</div>
		</div>

		<!-- 隐患告警折叠面板 -->
		<div class="panel" :class="{ open: panelAlarmOpen }">
			<div class="panel-header" @click="panelAlarmOpen = !panelAlarmOpen">
				<div class="panel-title">
					<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
						<line x1="12" y1="9" x2="12" y2="13"></line>
						<line x1="12" y1="17" x2="12.01" y2="17"></line>
					</svg>
					<span>隐患告警</span>
					<span class="panel-count">{{ filteredAlarmList.length }} 条</span>
				</div>
				<svg class="panel-arrow" :class="{ rotate: panelAlarmOpen }" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
					<polyline points="6 9 12 15 18 9"></polyline>
				</svg>
			</div>
			<!-- 告警状态 Tab 筛选 -->
			<div v-show="panelAlarmOpen" class="alarm-status-tabs">
				<div
					class="ast-item"
					:class="{ active: alarmStatusTab === '' }"
					@click="alarmStatusTab = ''; alarmPage = 1"
				>全部<span class="ast-num">{{ alarmStatusCount.total }}</span></div>
				<div
					class="ast-item pending"
					:class="{ active: alarmStatusTab === '未处理' }"
					@click="alarmStatusTab = '未处理'; alarmPage = 1"
				><span class="ast-dot"></span>未处理<span class="ast-num">{{ alarmStatusCount.pending }}</span></div>
				<div
					class="ast-item done"
					:class="{ active: alarmStatusTab === '已处理' }"
					@click="alarmStatusTab = '已处理'; alarmPage = 1"
				><span class="ast-dot"></span>已处理<span class="ast-num">{{ alarmStatusCount.done }}</span></div>
				<div
					class="ast-item ignored"
					:class="{ active: alarmStatusTab === '已忽略' }"
					@click="alarmStatusTab = '已忽略'; alarmPage = 1"
				><span class="ast-dot"></span>已忽略<span class="ast-num">{{ alarmStatusCount.ignored }}</span></div>
			</div>
			<div class="panel-body" v-show="panelAlarmOpen">
				<div class="table-wrap">
					<table class="data-table">
						<thead>
							<tr>
								<th style="width:70px">序号</th>
								<th style="width:110px">告警类型</th>
								<th style="width:110px">抓拍图</th>
								<th style="width:180px">位置</th>
								<th style="width:150px">时间</th>
								<th style="width:100px">处理状态</th>
								<th style="width:230px">操作</th>
							</tr>
						</thead>
						<tbody>
							<tr v-for="(item, idx) in paginatedAlarmList" :key="item.id" :class="alarmRowClass(item.status)">
								<td class="mono">{{ (alarmPage - 1) * alarmPageSize + idx + 1 }}</td>
								<td>
									<span class="type-tag" :class="alarmTypeClass(item.type)">{{ item.type }}</span>
								</td>
								<td>
									<div class="thumb" @click="openImagePreview(item.image)">
										<img :src="item.image" :alt="item.type" />
										<div class="thumb-mask">查看</div>
									</div>
								</td>
								<td>{{ item.location }}</td>
								<td class="time-cell">{{ item.time }}</td>
								<td>
									<span class="status-tag" :class="statusTagClass(item.status)">
										<span class="status-dot"></span>{{ item.status }}
									</span>
								</td>
								<td>
									<div class="row-actions">
										<button class="row-btn view" @click="openImagePreview(item.image)">
											<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
												<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
												<circle cx="12" cy="12" r="3"></circle>
											</svg>
											查看大图
										</button>
										<button
											v-if="item.status === '未处理'"
											class="row-btn done warning"
											@click="openDisposeModal(item)"
										>
											<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
												<path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"></path>
											</svg>
											立即处置
										</button>
										<button class="row-btn note" @click="openNoteModal(item)">
											<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
												<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
												<path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
											</svg>
											备注
										</button>
									</div>
								</td>
							</tr>
							<tr v-if="paginatedAlarmList.length === 0">
								<td colspan="7" class="empty-row">
									<div class="empty-state">
										<div class="empty-icon">
											<svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
												<path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
											</svg>
										</div>
										<div class="empty-text">暂无隐患告警</div>
									</div>
								</td>
							</tr>
						</tbody>
					</table>
				</div>
				<div class="pagination" v-if="filteredAlarmList.length > 0">
					<div class="page-total">共 {{ filteredAlarmList.length }} 条，第 {{ alarmPage }}/{{ totalAlarmPages }} 页</div>
					<div class="page-btns">
						<button class="pg-btn" :disabled="alarmPage === 1" @click="alarmPage--">上一页</button>
						<button v-for="n in alarmPageNums" :key="'a'+n" class="pg-btn" :class="{active: n===alarmPage}" @click="alarmPage = n">{{ n }}</button>
						<button class="pg-btn" :disabled="alarmPage === totalAlarmPages" @click="alarmPage++">下一页</button>
					</div>
				</div>
			</div>
		</div>

		<!-- 车牌台账折叠面板 -->
		<div class="panel" :class="{ open: panelPlateOpen }">
			<div class="panel-header" @click="panelPlateOpen = !panelPlateOpen">
				<div class="panel-title">
					<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M14 16H9m10 0h3v-3.15a1 1 0 0 0-.84-.99L16 11l-2.7-3.6a1 1 0 0 0-.8-.4H5.24a2 2 0 0 0-1.8 1.1l-.8 1.63A6 6 0 0 0 2 12.42V16h2"></path>
						<circle cx="6.5" cy="16.5" r="2.5"></circle>
						<circle cx="16.5" cy="16.5" r="2.5"></circle>
					</svg>
					<span>车牌台账</span>
					<span class="panel-count">{{ filteredPlateList.length }} 条</span>
				</div>
				<svg class="panel-arrow" :class="{ rotate: panelPlateOpen }" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
					<polyline points="6 9 12 15 18 9"></polyline>
				</svg>
			</div>
			<div class="panel-body" v-show="panelPlateOpen">
				<div class="table-wrap">
					<table class="data-table plate-table">
						<thead>
							<tr>
								<th style="width:70px">序号</th>
								<th style="width:140px">车牌号</th>
								<th style="width:110px">抓拍图</th>
								<th style="width:180px">位置</th>
								<th style="width:150px">时间</th>
								<th style="width:100px">处理状态</th>
								<th style="width:220px">来源</th>
							</tr>
						</thead>
						<tbody>
							<tr v-for="(item, idx) in paginatedPlateList" :key="item.id">
								<td class="mono">{{ (platePage - 1) * platePageSize + idx + 1 }}</td>
								<td>
									<div class="plate-cell">
										<span class="plate-province">{{ item.plate.slice(0, 1) }}</span>
										<span class="plate-num">{{ item.plate.slice(1) }}</span>
									</div>
								</td>
								<td>
									<div class="thumb" @click="openImagePreview(item.image)">
										<img :src="item.image" :alt="item.plate" />
										<div class="thumb-mask">查看</div>
									</div>
								</td>
								<td>{{ item.location }}</td>
								<td class="time-cell">{{ item.time }}</td>
								<td>
									<span class="status-tag" :class="statusTagClass(item.status)">
										<span class="status-dot"></span>{{ item.status }}
									</span>
								</td>
								<td>
									<span v-if="item.taskId" class="source-tag patrol">
										<svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
											<circle cx="12" cy="12" r="3"></circle>
											<path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
										</svg>
										{{ item.source }}
									</span>
									<span v-else class="source-tag static">● {{ item.source }}</span>
								</td>
							</tr>
							<tr v-if="paginatedPlateList.length === 0">
								<td colspan="7" class="empty-row">
									<div class="empty-state">
										<div class="empty-icon">
											<svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
												<rect x="3" y="8" width="18" height="8" rx="2"></rect>
											</svg>
										</div>
										<div class="empty-text">暂无车牌记录</div>
									</div>
								</td>
							</tr>
						</tbody>
					</table>
				</div>
				<div class="pagination" v-if="filteredPlateList.length > 0">
					<div class="page-total">共 {{ filteredPlateList.length }} 条，第 {{ platePage }}/{{ totalPlatePages }} 页</div>
					<div class="page-btns">
						<button class="pg-btn" :disabled="platePage === 1" @click="platePage--">上一页</button>
						<button v-for="n in platePageNums" :key="'p'+n" class="pg-btn" :class="{active: n===platePage}" @click="platePage = n">{{ n }}</button>
						<button class="pg-btn" :disabled="platePage === totalPlatePages" @click="platePage++">下一页</button>
					</div>
				</div>
			</div>
		</div>

		<!-- 图片预览弹窗 -->
		<div class="modal-mask" v-if="imgPreview.visible" @click.self="imgPreview.visible = false">
			<div class="modal image-modal">
				<button class="modal-close" @click="imgPreview.visible = false">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
						<line x1="18" y1="6" x2="6" y2="18"></line>
						<line x1="6" y1="6" x2="18" y2="18"></line>
					</svg>
				</button>
				<div class="image-box">
					<img :src="imgPreview.url" alt="preview" />
				</div>
			</div>
		</div>

		<!-- 处置 / 备注弹窗 -->
		<div class="modal-mask" v-if="disposeModal.visible" @click.self="disposeModal.visible = false">
			<div class="modal form-modal">
				<div class="modal-header">
					<div class="modal-title">{{ disposeModalTitle() }}</div>
					<button class="modal-close" @click="disposeModal.visible = false">
						<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
							<line x1="18" y1="6" x2="6" y2="18"></line>
							<line x1="6" y1="6" x2="18" y2="18"></line>
						</svg>
					</button>
				</div>
				<div class="modal-body">
					<div class="info-line">
						<div class="info-label">告警类型</div>
						<div class="info-value">
							<span class="type-tag" :class="alarmTypeClass(disposeModal.item?.type)">{{ disposeModal.item?.type }}</span>
						</div>
					</div>
					<div class="info-line">
						<div class="info-label">当前状态</div>
						<div class="info-value">
							<span class="status-tag" :class="statusTagClass(disposeModal.item?.status)">
								<span class="status-dot"></span>{{ disposeModal.item?.status }}
							</span>
						</div>
					</div>
					<div class="info-line">
						<div class="info-label">位置</div>
						<div class="info-value">{{ disposeModal.item?.location }}</div>
					</div>
					<div class="info-line" v-if="disposeModal.item?.remark">
						<div class="info-label">已有备注</div>
						<div class="info-value note-text">{{ disposeModal.item?.remark }}</div>
					</div>
					<div class="form-item" v-if="disposeModal.mode === 'dispose'">
						<label class="form-label">处置结果<span class="req">*</span></label>
						<select v-model="disposeModal.handleStatus" class="form-select">
							<option value="已处理">已处理</option>
							<option value="已忽略">已忽略</option>
						</select>
					</div>
					<div class="form-item">
						<label class="form-label">{{ disposeModal.mode === 'dispose' ? '处置说明' : '备注内容' }}<span v-if="disposeModal.mode === 'dispose'" class="req">*</span></label>
						<textarea v-model="disposeModal.remark" :placeholder="disposeModal.mode === 'dispose' ? '请填写处置措施和处理结果...' : '请填写备注内容...'" class="form-textarea" rows="5"></textarea>
					</div>
				</div>
				<div class="modal-footer">
					<button class="btn btn-cancel" @click="disposeModal.visible = false">取消</button>
					<button
						class="btn btn-confirm"
						:disabled="disposeModal.saving || (disposeModal.mode === 'dispose' && !disposeModal.remark.trim())"
						@click="submitDispose"
					>{{ disposeModal.mode === 'dispose' ? '确认处置' : '保存备注' }}</button>
				</div>
			</div>
		</div>
	</div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import request from '../../api/request'
import { getAuth } from '../../stores/community'

/* ============ 当前登录身份：普通用户仅看本社区 ============ */
const auth = getAuth()
const myCommunityId = auth.communityId || null

/* ============ 时间工具 ============ */
function pad(n) {
	return String(n).padStart(2, '0')
}
function toDate(v) {
	if (!v) return null
	const d = new Date(String(v).replace(' ', 'T'))
	return isNaN(d.getTime()) ? null : d
}
function fmtDateTime(v) {
	const d = toDate(v)
	if (!d) return v ? String(v) : '--'
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function nowStr() {
	const d = new Date()
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/* ============ 枚举 ============ */
const alarmTypeOptions = ['人员异常', '消防安全', '其他']
const handleOptions = ['未处理', '已处理', '已忽略']

/* ============ 折叠面板状态 ============ */
const panelAlarmOpen = ref(true)
const panelPlateOpen = ref(true)

/* ============ 筛选条件 ============ */
const filter = reactive({
	dateStart: '',
	dateEnd: '',
	alarmType: '',
	plate: '',
	handleStatus: ''
})

/* ============ 抓拍图占位（无图片时的兜底） ============ */
const pic = () => {
	const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='220' height='140'>
		<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>
			<stop offset='0%' stop-color='#E8F1F4'/><stop offset='100%' stop-color='#D5E7EC'/>
		</linearGradient></defs>
		<rect width='220' height='140' fill='url(#g)'/>
		<text x='50%' y='50%' text-anchor='middle' dy='.3em' font-family='ui-monospace, monospace' font-size='13' fill='#7A98A3'>暂无抓拍图</text>
	</svg>`
	return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
}
function imageOf(url) {
	const u = url ? String(url).trim() : ''
	return u || pic()
}

/* ============ 社区数据范围 ============ */
function baseParams(pageNum, pageSize) {
	const params = { pageNum, pageSize }
	if (myCommunityId) params.communityId = myCommunityId
	return params
}

/* ============ 从后端 API 获取告警数据 ============ */
async function fetchAlarmsFromAPI() {
	try {
		const res = await request.get('/alarm/page', { params: baseParams(1, 500) })
		return (res.records || []).map(item => ({
			id: item.alarmId,
			type: item.alarmType || '其他',
			image: imageOf(item.imgUrl, item.alarmType || '告警'),
			location: item.location || '--',
			time: fmtDateTime(item.findTime),
			rawTime: item.findTime || '',
			status: handleOptions.includes(item.handleStatus) ? item.handleStatus : '未处理',
			level: item.level || '',
			remark: item.remark || '',
			taskId: item.taskId || null
		})).sort((a, b) => (toDate(b.rawTime)?.getTime() || 0) - (toDate(a.rawTime)?.getTime() || 0))
	} catch (e) {
		console.error('获取隐患数据失败:', e)
	}
	return []
}

/* ============ 从后端 API 获取车牌数据 ============ */
async function fetchPlatesFromAPI() {
	try {
		const res = await request.get('/license-plate/page', { params: baseParams(1, 500) })
		return (res.records || []).map(item => ({
			id: item.plateId,
			plate: item.plateNumber || '--',
			image: imageOf(item.imgUrl, item.plateNumber || '车牌'),
			location: item.location || '--',
			time: fmtDateTime(item.createTime),
			rawTime: item.createTime || '',
			status: handleOptions.includes(item.handleStatus) ? item.handleStatus : '未处理',
			taskId: item.taskId || null,
			source: item.taskId ? '巡检任务 #' + item.taskId : '系统识别'
		})).sort((a, b) => (toDate(b.rawTime)?.getTime() || 0) - (toDate(a.rawTime)?.getTime() || 0))
	} catch (e) {
		console.error('获取车牌数据失败:', e)
	}
	return []
}

/* ============ 台账数据 ============ */
const plateList = ref([])
const alarmList = ref([])

const POLL_INTERVAL_MS = 30000
let pollTimer = null

async function refreshPlateList() {
	plateList.value = await fetchPlatesFromAPI()
}
async function refreshAlarmList() {
	alarmList.value = await fetchAlarmsFromAPI()
}
async function refreshAll() {
	await Promise.all([refreshPlateList(), refreshAlarmList()])
}

onMounted(() => {
	refreshAll()
	pollTimer = setInterval(refreshAll, POLL_INTERVAL_MS)
})
onBeforeUnmount(() => {
	if (pollTimer) clearInterval(pollTimer)
})

/* ============ 隐患告警：状态快捷 Tab ============ */
const alarmStatusTab = ref('')
const alarmStatusCount = computed(() => {
	const c = { total: alarmList.value.length, pending: 0, done: 0, ignored: 0 }
	alarmList.value.forEach(r => {
		if (r.status === '未处理') c.pending++
		else if (r.status === '已处理') c.done++
		else if (r.status === '已忽略') c.ignored++
	})
	return c
})

/* ============ 筛选计算（顶部快捷 Tab + 下拉筛选交集） ============ */
const filteredAlarmList = computed(() => {
	return alarmList.value.filter(r => {
		if (alarmStatusTab.value && r.status !== alarmStatusTab.value) return false
		if (filter.alarmType && r.type !== filter.alarmType) return false
		if (filter.handleStatus && r.status !== filter.handleStatus) return false
		if (filter.dateStart && r.time.slice(0, 10) < filter.dateStart) return false
		if (filter.dateEnd && r.time.slice(0, 10) > filter.dateEnd) return false
		return true
	})
})
const filteredPlateList = computed(() => {
	return plateList.value.filter(r => {
		if (filter.plate && !r.plate.toLowerCase().includes(filter.plate.toLowerCase())) return false
		if (filter.dateStart && r.time.slice(0, 10) < filter.dateStart) return false
		if (filter.dateEnd && r.time.slice(0, 10) > filter.dateEnd) return false
		if (filter.handleStatus && r.status !== filter.handleStatus) return false
		return true
	})
})

/* ============ 分页 ============ */
const alarmPage = ref(1)
const alarmPageSize = ref(5)
const totalAlarmPages = computed(() => Math.max(1, Math.ceil(filteredAlarmList.value.length / alarmPageSize.value)))
const paginatedAlarmList = computed(() => {
	const start = (alarmPage.value - 1) * alarmPageSize.value
	return filteredAlarmList.value.slice(start, start + alarmPageSize.value)
})
const alarmPageNums = computed(() => {
	const total = totalAlarmPages.value
	const cur = alarmPage.value
	const arr = []
	for (let i = Math.max(1, cur - 2); i <= Math.min(total, cur + 2); i++) arr.push(i)
	return arr
})

const platePage = ref(1)
const platePageSize = ref(5)
const totalPlatePages = computed(() => Math.max(1, Math.ceil(filteredPlateList.value.length / platePageSize.value)))
const paginatedPlateList = computed(() => {
	const start = (platePage.value - 1) * platePageSize.value
	return filteredPlateList.value.slice(start, start + platePageSize.value)
})
const platePageNums = computed(() => {
	const total = totalPlatePages.value
	const cur = platePage.value
	const arr = []
	for (let i = Math.max(1, cur - 2); i <= Math.min(total, cur + 2); i++) arr.push(i)
	return arr
})

/* ============ 行色 ============ */
function alarmRowClass(status) {
	return {
		'row-pending': status === '未处理',
		'row-done': status === '已处理',
		'row-ignored': status === '已忽略'
	}
}

/* ============ 告警类型 / 状态样式 ============ */
function alarmTypeClass(t) {
	return {
		'人员异常': 'danger',
		'消防安全': 'warn',
		'其他': 'normal'
	}[t] || 'normal'
}
function statusTagClass(s) {
	if (s === '已处理') return 'done'
	if (s === '已忽略') return 'ignored'
	return 'pending'
}

/* ============ 筛选操作 ============ */
function onReset() {
	filter.dateStart = ''
	filter.dateEnd = ''
	filter.alarmType = ''
	filter.plate = ''
	filter.handleStatus = ''
	alarmStatusTab.value = ''
	alarmPage.value = 1
	platePage.value = 1
}
function onExportExcel() {
	const rows = [
		['安防台账 - 隐患告警'],
		['序号', '告警类型', '位置', '时间', '处理状态', '备注']
	]
	filteredAlarmList.value.forEach((r, i) => rows.push([i + 1, r.type, r.location, r.time, r.status, r.remark || '']))
	rows.push([])
	rows.push(['安防台账 - 车牌记录'])
	rows.push(['序号', '车牌号', '位置', '时间', '处理状态', '来源'])
	filteredPlateList.value.forEach((r, i) => rows.push([i + 1, r.plate, r.location, r.time, r.status, r.source || '系统识别']))
	const csv = rows.map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
	const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
	const a = document.createElement('a')
	a.href = URL.createObjectURL(blob)
	a.download = `安防台账_${new Date().toISOString().slice(0, 10)}.csv`
	a.click()
	URL.revokeObjectURL(a.href)
}

/* ============ 弹窗：图片预览 ============ */
const imgPreview = reactive({ visible: false, url: '' })
function openImagePreview(url) {
	imgPreview.url = url
	imgPreview.visible = true
}

/* ============ 弹窗：处置 / 备注 ============ */
const disposeModal = reactive({
	visible: false,
	mode: 'dispose',
	item: null,
	remark: '',
	handleStatus: '已处理',
	saving: false
})
function openDisposeModal(item) {
	disposeModal.mode = 'dispose'
	disposeModal.item = item
	disposeModal.remark = item.remark || ''
	disposeModal.handleStatus = '已处理'
	disposeModal.visible = true
}
function openNoteModal(item) {
	disposeModal.mode = 'note'
	disposeModal.item = item
	disposeModal.remark = item.remark || ''
	disposeModal.visible = true
}
function disposeModalTitle() {
	if (disposeModal.mode === 'note') return '备注信息'
	return '处置告警'
}

/* ============ 提交处置 / 备注 ============ */
async function submitDispose() {
	const item = disposeModal.item
	if (!item) return
	disposeModal.saving = true
	try {
		const payload = { alarmId: item.id, remark: disposeModal.remark.trim() }
		if (disposeModal.mode === 'dispose') {
			payload.handleStatus = disposeModal.handleStatus
			payload.handleTime = disposeModal.handleStatus === '已处理' ? nowStr() : null
		}
		await request.put('/alarm', payload)
		disposeModal.visible = false
		await refreshAlarmList()
	} catch (e) {
		console.error('提交处置失败:', e)
	}
	disposeModal.saving = false
}
</script>

<style scoped>
/* ============ 页面容器 ============ */
.page-wrap {
	min-height: 100%;
	height: auto;
	box-sizing: border-box;
	overflow-x: hidden;
	flex: 0 0 auto;
}


/* ============ 筛选卡片 ============ */
.filter-card {
	background: #FFFFFF;
	border: 1px solid #E8EDEF;
	border-radius: 10px;
	padding: 14px;
	box-shadow: var(--shadow-card);
	margin-bottom: 12px;
	animation: cardIn 0.4s ease-out both;
}
.filter-row {
	display: flex;
	gap: 12px;
	flex-wrap: wrap;
	align-items: flex-end;
}
.filter-label {
	display: block;
	font-size: 12px;
	color: var(--text-sub);
	font-weight: 500;
	margin-bottom: 6px;
	letter-spacing: 0.5px;
}
.filter-input, .filter-select {
	width: 100%;
	padding: 8px 12px;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-size: 13px;
	color: var(--text-main);
	background: #F4F9FA;
	outline: none;
	transition: all 0.2s;
	box-sizing: border-box;
	appearance: none;
	-webkit-appearance: none;
}
.filter-select {
	padding-right: 30px;
	cursor: pointer;
	background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23507080' stroke-width='2.5'><polyline points='6 9 12 15 18 9'/></svg>");
	background-repeat: no-repeat;
	background-position: right 10px center;
}
.filter-input:hover, .filter-select:hover { border-color: var(--primary); background: #fff; }
.filter-input:focus, .filter-select:focus {
	border-color: var(--primary);
	background: #fff;
	box-shadow: 0 0 0 3px rgba(0, 96, 128, 0.12);
}
.filter-item {
	flex: 0 0 auto;
	min-width: 140px;
}
.date-range {
	display: flex;
	align-items: flex-end;
	gap: 8px;
	flex: 0 0 auto;
}
.date-range .filter-label { margin-bottom: 6px; }
.date-input { width: 145px; }
.plate-input { width: 170px; }
.date-sep {
	font-size: 12px;
	color: var(--text-soft);
	padding-bottom: 8px;
	flex-shrink: 0;
}
.filter-actions {
	display: flex;
	gap: 10px;
	margin-left: auto;
	flex-shrink: 0;
}
.btn {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 8px 16px;
	border-radius: 8px;
	font-size: 13px;
	font-weight: 500;
	border: none;
	cursor: pointer;
	transition: all 0.25s;
	letter-spacing: 0.5px;
	box-sizing: border-box;
}
.btn-reset {
	background: #F4F9FA;
	color: var(--text-sub);
	border: 1px solid var(--border);
}
.btn-reset:hover { background: #fff; border-color: var(--primary); color: var(--primary); }
.btn-export {
	background: linear-gradient(135deg, var(--primary), var(--primary-light));
	color: #fff;
	box-shadow: 0 4px 10px rgba(0, 96, 128, 0.25);
}
.btn-export:hover { transform: translateY(-1px); box-shadow: 0 6px 14px rgba(0, 96, 128, 0.35); }

/* ============ 折叠面板 ============ */
.panel {
	background: #FFFFFF;
	border: 1px solid #E8EDEF;
	border-radius: 10px;
	box-shadow: var(--shadow-card);
	margin-bottom: 12px;
	overflow: hidden;
	animation: cardIn 0.4s ease-out both;
}
.panel:nth-child(2) { animation-delay: 0.05s; }
.panel-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 14px 16px;
	cursor: pointer;
	background: linear-gradient(90deg, rgba(0, 96, 128, 0.04), transparent 70%);
	transition: background 0.25s;
	user-select: none;
}
.panel-header:hover { background: linear-gradient(90deg, rgba(0, 96, 128, 0.08), transparent 70%); }
.panel-title {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 14px;
	font-weight: 600;
	color: var(--text-main);
	letter-spacing: 0.5px;
}
.panel-title svg { color: var(--primary); flex-shrink: 0; }
.panel-count {
	font-size: 12px;
	font-weight: 500;
	color: var(--primary);
	background: rgba(0, 96, 128, 0.1);
	padding: 3px 10px;
	border-radius: 12px;
	letter-spacing: 0.5px;
}
.panel-arrow {
	color: var(--text-soft);
	transition: transform 0.3s ease;
}
.panel-arrow.rotate {
	transform: rotate(180deg);
	color: var(--primary);
}
.panel-body {
	border-top: 1px solid #F0F6F7;
	padding: 14px 16px 16px;
}

/* ====== 隐患告警 状态快捷 Tab ====== */
.alarm-status-tabs {
	display: flex;
	gap: 10px;
	padding: 12px 16px 0;
	flex-wrap: wrap;
	background: #fff;
	border-bottom: 1px dashed var(--border);
}
.ast-item {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 5px 14px;
	background: #F4F9FA;
	border: 1px solid var(--border);
	border-radius: 16px;
	cursor: pointer;
	font-size: 12px;
	color: var(--text-sub);
	font-weight: 500;
	transition: all 0.2s;
}
.ast-item:hover {
	background: #fff;
	border-color: var(--primary);
	color: var(--primary);
	transform: translateY(-1px);
}
.ast-item.active {
	color: #fff;
	border-color: transparent;
	font-weight: 600;
	box-shadow: 0 3px 8px rgba(0, 96, 128, 0.25);
}
.ast-item:not(.pending):not(.done):not(.ignored).active {
	background: linear-gradient(135deg, #4A6070, #2A4048);
}
.ast-item.pending.active { background: linear-gradient(135deg, #F59E0B, #D97706); box-shadow: 0 3px 8px rgba(217,119,6,0.3); }
.ast-item.done.active { background: linear-gradient(135deg, #10B981, #059669); box-shadow: 0 3px 8px rgba(5,150,105,0.3); }
.ast-item.ignored.active { background: linear-gradient(135deg, #6B7280, #4B5563); box-shadow: 0 3px 8px rgba(75,85,99,0.3); }
.ast-item.pending { color: #D97706; }
.ast-item.done { color: #059669; }
.ast-item.ignored { color: #6B7280; }
.ast-dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: currentColor;
}
.ast-num {
	min-width: 22px;
	text-align: center;
	padding: 1px 7px;
	border-radius: 10px;
	font-size: 11px;
	font-weight: 700;
	background: rgba(255, 255, 255, 0.2);
}
.ast-item:not(.active) .ast-num {
	background: rgba(0, 96, 128, 0.08);
}
.ast-item.pending:not(.active) .ast-num { background: rgba(217,119,6,0.12); }
.ast-item.done:not(.active) .ast-num { background: rgba(5,150,105,0.12); }
.ast-item.ignored:not(.active) .ast-num { background: rgba(107,114,128,0.12); }

/* ============ 表格 ============ */
.table-wrap {
	border: 1px solid var(--border);
	border-radius: 10px;
	overflow-x: auto;
	overflow-y: scroll;
	height: 450px;
	min-height: 280px;
	scroll-behavior: smooth;
}
.table-wrap::-webkit-scrollbar { width: 8px; height: 0; }
.table-wrap::-webkit-scrollbar:horizontal { display: none; }
.table-wrap::-webkit-scrollbar-thumb { background: #B0C8D0; border-radius: 4px; }
.table-wrap::-webkit-scrollbar-thumb:hover { background: var(--text-soft); }
.table-wrap::-webkit-scrollbar-track { background: #F0F6F7; }

.data-table {
	width: 100%;
	border-collapse: collapse;
	font-size: 13px;
	min-width: 780px;
}
.plate-table { min-width: 880px; }
.data-table thead th {
	position: sticky;
	top: 0;
	z-index: 2;
	text-align: left;
	padding: 13px 16px;
	background: linear-gradient(180deg, #F4F9FA, #F0F6F7);
	color: #406070;
	font-weight: 600;
	font-size: 13px;
	border-bottom: 1px solid var(--border);
	white-space: nowrap;
	letter-spacing: 0.5px;
}
.data-table tbody td {
	padding: 12px 16px;
	border-bottom: 1px solid #F0F6F7;
	color: #2A4048;
	white-space: nowrap;
	transition: background 0.2s;
}
.data-table tbody tr:nth-child(even):not(.row-pending):not(.row-done):not(.row-ignored) {
	background: #FAFCFD;
}
.data-table tbody tr:last-child td { border-bottom: none; }
.data-table tbody tr:hover td { filter: brightness(0.98); }

/* 告警行色区分 */
.row-pending td { background: #FFF7E6 !important; }
.row-pending:hover td { background: #FFEFD2 !important; }
.row-done td { background: #ECFDF5 !important; color: var(--text-sub) !important; }
.row-done:hover td { background: #D9F7E7 !important; }
.row-ignored td { background: #F3F4F6 !important; color: #6B7280 !important; }
.row-ignored:hover td { background: #E5E7EB !important; }

.mono {
	font-family: ui-monospace, SFMono-Regular, monospace;
	font-size: 12px;
	color: var(--text-soft);
}
.time-cell {
	font-size: 12px;
	color: var(--text-sub);
	font-family: ui-monospace, SFMono-Regular, monospace;
}

/* 抓拍图 */
.thumb {
	width: 88px;
	height: 54px;
	border-radius: 6px;
	overflow: hidden;
	position: relative;
	cursor: zoom-in;
	border: 1px solid var(--border);
	box-sizing: border-box;
	transition: all 0.2s;
}
.thumb img {
	width: 100%;
	height: 100%;
	object-fit: cover;
	display: block;
}
.thumb-mask {
	position: absolute;
	inset: 0;
	background: rgba(0, 30, 40, 0.55);
	color: #fff;
	font-size: 12px;
	display: flex;
	align-items: center;
	justify-content: center;
	opacity: 0;
	transition: opacity 0.2s;
	letter-spacing: 1px;
}
.thumb:hover .thumb-mask { opacity: 1; }
.thumb:hover { border-color: var(--primary); box-shadow: 0 2px 8px rgba(0, 96, 128, 0.2); }

/* 告警类型标签 */
.type-tag {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	font-size: 12px;
	font-weight: 600;
	padding: 3px 10px;
	border-radius: 12px;
	letter-spacing: 0.5px;
}
.type-tag.danger { background: #FEE2E2; color: #DC2626; }
.type-tag.warn { background: #FEF3C7; color: #D97706; }
.type-tag.primary { background: #C0E0E8; color: var(--primary); }
.type-tag.normal { background: #E8F4F8; color: #406070; }

/* 状态标签 */
.status-tag {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	font-size: 12px;
	font-weight: 500;
	padding: 3px 10px;
	border-radius: 12px;
	letter-spacing: 0.5px;
}
.status-dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: currentColor;
}
.status-tag.pending { background: #FFF2D8; color: #D97706; }
.status-tag.done { background: #D1FAE5; color: #059669; }
.status-tag.ignored { background: #E5E7EB; color: #6B7280; }
.status-tag.pending .status-dot { animation: pulse 1.5s ease-in-out infinite; }
@keyframes pulse {
	0%, 100% { opacity: 1; transform: scale(1); }
	50% { opacity: 0.4; transform: scale(1.4); }
}

/* 行内操作按钮 */
.row-actions {
	display: flex;
	gap: 6px;
	flex-wrap: wrap;
}
.row-btn {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 5px 10px;
	border-radius: 6px;
	font-size: 12px;
	font-weight: 500;
	border: 1px solid transparent;
	cursor: pointer;
	transition: all 0.2s;
	box-sizing: border-box;
	background: #fff;
}
.row-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.row-btn.view { color: var(--primary); border-color: var(--border); }
.row-btn.view:hover:not(:disabled) { background: #E8F4F8; border-color: var(--primary); }
.row-btn.done { color: #10B981; border-color: #A7F3D0; }
.row-btn.done:hover:not(:disabled) { background: #ECFDF5; border-color: #10B981; }
.row-btn.done.warning { color: #D97706; border-color: #FDE68A; }
.row-btn.done.warning:hover:not(:disabled) { background: #FFFBEB; border-color: #D97706; }
.row-btn.note { color: #D97706; border-color: #FDE68A; }
.row-btn.note:hover { background: #FFFBEB; border-color: #D97706; }

/* 车牌单元格 */
.plate-cell {
	display: inline-flex;
	align-items: center;
	border-radius: 6px;
	overflow: hidden;
	border: 1.5px solid var(--primary);
	font-family: ui-monospace, SFMono-Regular, monospace;
	font-weight: 700;
	letter-spacing: 1px;
}
.plate-province {
	background: var(--primary);
	color: #fff;
	padding: 4px 8px;
	font-size: 13px;
}
.plate-num {
	background: #fff;
	color: #004060;
	padding: 4px 8px;
	font-size: 13px;
}

/* 来源标签 */
.source-tag {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	padding: 3px 10px;
	border-radius: 999px;
	font-size: 12px;
	font-weight: 500;
	letter-spacing: 0.3px;
	line-height: 1.5;
	max-width: 100%;
	word-break: break-all;
	white-space: normal;
}
.source-tag.patrol {
	background: linear-gradient(135deg, rgba(0, 96, 128, 0.08), rgba(0, 128, 160, 0.14));
	color: var(--primary);
	border: 1px solid rgba(0, 96, 128, 0.2);
}
.source-tag.static {
	background: #F0F4F5;
	color: #607880;
	border: 1px solid #D0D8DC;
}

/* 分页 */
.pagination {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding-top: 16px;
	gap: 12px;
	flex-wrap: wrap;
}
.page-total {
	font-size: 12px;
	color: var(--text-sub);
	letter-spacing: 0.5px;
}
.page-btns {
	display: flex;
	gap: 6px;
}
.pg-btn {
	min-width: 34px;
	height: 32px;
	padding: 0 10px;
	border-radius: 6px;
	border: 1px solid var(--border);
	background: #fff;
	color: #406070;
	font-size: 12px;
	font-weight: 500;
	cursor: pointer;
	transition: all 0.2s;
}
.pg-btn:hover:not(:disabled):not(.active) { border-color: var(--primary); color: var(--primary); }
.pg-btn.active {
	background: linear-gradient(135deg, var(--primary), var(--primary-light));
	color: #fff;
	border-color: transparent;
	box-shadow: 0 3px 8px rgba(0, 96, 128, 0.25);
}
.pg-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* 空状态 */
.empty-row {
	text-align: center !important;
	padding: 56px 16px !important;
}
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8px;
}
.empty-icon {
	width: 60px;
	height: 60px;
	border-radius: 50%;
	background: linear-gradient(135deg, #F0F6F7, var(--border));
	display: flex;
	align-items: center;
	justify-content: center;
	color: var(--text-soft);
	margin-bottom: 6px;
}
.empty-text {
	font-size: 14px;
	color: var(--text-sub);
	font-weight: 500;
}

/* ============ 弹窗 ============ */
.modal-mask {
	position: fixed;
	inset: 0;
	background: rgba(15, 30, 40, 0.45);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 999;
	animation: fadeIn 0.2s ease-out;
	padding: 20px;
	box-sizing: border-box;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
.modal {
	background: #fff;
	border-radius: 12px;
	box-shadow: 0 10px 40px rgba(15, 30, 40, 0.25);
	animation: cardIn 0.28s ease-out;
	position: relative;
}
.modal-close {
	position: absolute;
	top: 14px;
	right: 14px;
	width: 28px;
	height: 28px;
	border-radius: 50%;
	border: none;
	background: #F0F6F7;
	color: var(--text-sub);
	display: flex;
	align-items: center;
	justify-content: center;
	cursor: pointer;
	transition: all 0.2s;
	z-index: 2;
}
.modal-close:hover { background: #EF4444; color: #fff; transform: rotate(90deg); }

.image-modal {
	max-width: 90vw;
	max-height: 90vh;
	overflow: hidden;
}
.image-box {
	display: flex;
	align-items: center;
	justify-content: center;
	background: #000;
}
.image-box img {
	max-width: 90vw;
	max-height: 85vh;
	display: block;
}

.form-modal {
	width: 100%;
	max-width: 520px;
	display: flex;
	flex-direction: column;
}
.modal-header {
	padding: 18px 24px;
	border-bottom: 1px solid #F0F6F7;
	display: flex;
	justify-content: space-between;
	align-items: center;
}
.modal-title {
	font-size: 16px;
	font-weight: 700;
	color: var(--text-main);
	letter-spacing: 0.5px;
}
.modal-body {
	padding: 20px 24px;
}
.info-line {
	display: flex;
	gap: 14px;
	padding: 8px 0;
	border-bottom: 1px dashed #F0F6F7;
	font-size: 13px;
}
.info-line:last-of-type { margin-bottom: 14px; }
.info-label {
	color: var(--text-soft);
	flex-shrink: 0;
	width: 72px;
}
.info-value {
	color: var(--text-main);
	flex: 1;
	min-width: 0;
}
.note-text {
	color: #406070;
	background: #F4F9FA;
	padding: 8px 10px;
	border-radius: 6px;
	line-height: 1.5;
	word-break: break-all;
}
.form-item { margin-top: 14px; }
.form-label {
	display: block;
	font-size: 13px;
	font-weight: 500;
	color: #406070;
	margin-bottom: 8px;
	letter-spacing: 0.5px;
}
.form-label .req {
	color: #EF4444;
	margin-left: 2px;
}
.form-select {
	width: 100%;
	padding: 9px 12px;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-size: 13px;
	color: var(--text-main);
	background: #F4F9FA;
	outline: none;
	box-sizing: border-box;
	cursor: pointer;
	transition: all 0.2s;
}
.form-select:hover, .form-select:focus { border-color: var(--primary); background: #fff; }
.form-textarea {
	width: 100%;
	padding: 10px 12px;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-size: 13px;
	color: var(--text-main);
	background: #F4F9FA;
	outline: none;
	resize: vertical;
	box-sizing: border-box;
	font-family: inherit;
	line-height: 1.5;
	transition: all 0.2s;
}
.form-textarea:hover { border-color: var(--primary); background: #fff; }
.form-textarea:focus {
	border-color: var(--primary);
	background: #fff;
	box-shadow: 0 0 0 3px rgba(0, 96, 128, 0.12);
}
.form-textarea::placeholder { color: var(--text-soft); }
.modal-footer {
	padding: 16px 24px;
	border-top: 1px solid #F0F6F7;
	display: flex;
	justify-content: flex-end;
	gap: 10px;
}
.btn-cancel {
	background: #F4F9FA;
	color: var(--text-sub);
	border: 1px solid var(--border);
}
.btn-cancel:hover { background: #fff; border-color: var(--primary); color: var(--primary); }
.btn-confirm {
	background: linear-gradient(135deg, var(--primary), var(--primary-light));
	color: #fff;
	box-shadow: 0 4px 10px rgba(0, 96, 128, 0.25);
}
.btn-confirm:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 14px rgba(0, 96, 128, 0.35); }
.btn-confirm:disabled { opacity: 0.5; cursor: not-allowed; }

/* ============ 动画 ============ */
@keyframes cardIn {
	from { opacity: 0; transform: translateY(10px); }
	to { opacity: 1; transform: translateY(0); }
}
@keyframes fadeDown {
	from { opacity: 0; transform: translateY(-8px); }
	to { opacity: 1; transform: translateY(0); }
}

/* ============ 响应式 ============ */
@media (max-width: 900px) {
	.filter-item { min-width: calc(50% - 14px); }
	.date-range { width: 100%; }
	.date-input { flex: 1; min-width: 0; }
	.filter-actions { margin-left: 0; width: 100%; justify-content: flex-end; }
}
@media (max-width: 600px) {
	.filter-item { min-width: 100%; }
	.data-table { font-size: 12px; }
	.data-table th, .data-table td { padding: 10px 10px; }
	.thumb { width: 72px; height: 44px; }
	.pagination { flex-direction: column; align-items: flex-start; }
}
</style>
