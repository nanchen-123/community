<template>
	<div class="page-wrap">
		<!-- 页面标题 -->
		<div class="page-header">
			<div class="page-title">巡检任务</div>
			<div class="page-sub">
				<svg class="sub-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<rect x="3" y="4" width="18" height="16" rx="2"></rect>
					<line x1="3" y1="9" x2="21" y2="9"></line>
					<line x1="9" y1="14" x2="15" y2="14"></line>
				</svg>
				<span>巡检任务编排、下发与历史记录</span>
			</div>
		</div>

		<!-- 顶部统计卡片 -->
		<div class="task-stats">
			<div class="stat-card">
				<div class="stat-icon stat-blue">📋</div>
				<div class="stat-body">
					<div class="stat-num">{{ allTasks.length }}</div>
					<div class="stat-label">总任务</div>
				</div>
			</div>
			<div class="stat-card">
				<div class="stat-icon stat-amber">⏳</div>
				<div class="stat-body">
					<div class="stat-num">{{ pendingCount }}</div>
					<div class="stat-label">待执行</div>
				</div>
			</div>
			<div class="stat-card">
				<div class="stat-icon stat-indigo">🔄</div>
				<div class="stat-body">
					<div class="stat-num">{{ runningCount }}</div>
					<div class="stat-label">执行中</div>
				</div>
			</div>
			<div class="stat-card">
				<div class="stat-icon stat-green">✅</div>
				<div class="stat-body">
					<div class="stat-num">{{ doneCount }}</div>
					<div class="stat-label">已完成</div>
				</div>
			</div>
		</div>

		<!-- 新建任务区 -->
		<div class="card build-card">
			<div class="card-title">
				<svg class="title-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<line x1="12" y1="5" x2="12" y2="19"></line>
					<line x1="5" y1="12" x2="19" y2="12"></line>
				</svg>
				<span>新建巡检任务</span>
			</div>
			<div class="build-body">
				<div class="build-item">
					<label class="build-label">巡逻航点</label>
					<div class="waypoint-box">
						<div class="waypoint-list">
						<div v-if="newTask.waypoints.length === 0" class="wp-empty">暂无航点，请在下方输入航点名称添加</div>
						<span
							v-for="(p, idx) in newTask.waypoints"
								:key="idx"
								class="waypoint-tag"
								:class="{ 'has-xy': p.x != null, 'has-photo': p.autoPhoto }"
								title="x:{{ p.x }} y:{{ p.y }} z:{{ p.z }} qx:{{ p.qx }} qy:{{ p.qy }} qz:{{ p.qz }} qw:{{ p.qw }}"
							>
								<span class="wp-idx">{{ idx + 1 }}</span>
								<span class="wp-name">{{ p.name }}</span>
								<span v-if="p.autoPhoto" class="wp-photo" title="到达该航点自动拍照识别">📷</span>
								<span v-if="p.x != null" class="wp-coords">x{{ fmtNum(p.x) }} y{{ fmtNum(p.y) }}</span>
								<button class="wp-edit" @click="openEditCoords(idx)" title="编辑坐标">
									<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
										<path d="M12 20h9"></path>
										<path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
									</svg>
								</button>
								<button class="wp-del" @click="removeWaypoint(idx)" title="移除">
									<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
										<line x1="18" y1="6" x2="6" y2="18"></line>
										<line x1="6" y1="6" x2="18" y2="18"></line>
									</svg>
								</button>
							</span>
						</div>
						<div class="wp-input-wrap">
							<input
								v-model="waypointInput"
								@keydown.enter.prevent="addWaypoint"
								placeholder="输入航点名称后回车，如：A栋入口"
								class="wp-input"
							/>
							<button class="wp-add" @click="addWaypoint">
								<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
									<line x1="12" y1="5" x2="12" y2="19"></line>
									<line x1="5" y1="12" x2="19" y2="12"></line>
								</svg>
								添加
							</button>
							<button class="wp-import" @click="importDocxWaypoints" title="从默认路径模板导入坐标（含ROS四元数朝向）">
								<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
									<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
									<polyline points="7 10 12 15 17 10"></polyline>
									<line x1="12" y1="15" x2="12" y2="3"></line>
								</svg>
								默认路径
							</button>
							<button class="wp-upload" @click="triggerJsonUpload" title="上传JSON坐标文件">
								<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
									<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
									<polyline points="14 2 14 8 20 8"></polyline>
									<line x1="12" y1="18" x2="12" y2="12"></line>
									<line x1="9" y1="15" x2="15" y2="15"></line>
								</svg>
								上传JSON
							</button>
							<input
								ref="jsonFileInput"
								type="file"
								accept=".json"
								style="display:none"
								@change="handleJsonFile"
							/>
						</div>
						<div class="wp-hint">已选 {{ newTask.waypoints.length }} 个航点，将按顺序循环巡逻{{ withCoords }}</div>
					</div>
				</div>

				<div class="build-grid">
					<div class="build-cell cell-loops">
						<label class="build-label">循环巡逻次数</label>
						<div class="loops-input">
							<button class="loops-btn" @click="changeLoops(-1)" :disabled="newTask.loops <= 1">
								<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
									<line x1="5" y1="12" x2="19" y2="12"></line>
								</svg>
							</button>
							<input
								v-model.number="newTask.loops"
								type="number"
								min="1"
								max="99"
								class="loops-num"
							/>
							<button class="loops-btn" @click="changeLoops(1)" :disabled="newTask.loops >= 99">
								<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
									<line x1="12" y1="5" x2="12" y2="19"></line>
									<line x1="5" y1="12" x2="19" y2="12"></line>
								</svg>
							</button>
							<span class="loops-suffix">次</span>
						</div>
					</div>
					<div class="build-cell cell-time">
						<label class="build-label">巡检时段</label>
						<div class="time-range">
							<input v-model="newTask.startTime" type="datetime-local" class="time-input" />
							<span class="time-sep">至</span>
							<input v-model="newTask.endTime" type="datetime-local" class="time-input" />
						</div>
					</div>
					<div class="build-actions">
						<button
							class="btn btn-build"
							:disabled="newTask.waypoints.length < 2"
							@click="createTask"
						>
							<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
								<polyline points="20 6 9 17 4 12"></polyline>
							</svg>
							创建任务
						</button>
						<button class="btn btn-reset" @click="resetNewTask">
							<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
								<polyline points="1 4 1 10 7 10"></polyline>
								<path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path>
							</svg>
							重置
						</button>
					</div>
				</div>
			</div>
		</div>

		<!-- 任务列表（三分类） -->
		<div class="card list-card">
			<div class="card-title">
				<svg class="title-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<line x1="8" y1="6" x2="21" y2="6"></line>
					<line x1="8" y1="12" x2="21" y2="12"></line>
					<line x1="8" y1="18" x2="21" y2="18"></line>
					<line x1="3" y1="6" x2="3.01" y2="6"></line>
					<line x1="3" y1="12" x2="3.01" y2="12"></line>
					<line x1="3" y1="18" x2="3.01" y2="18"></line>
				</svg>
				<span>任务列表</span>
				<div class="tab-group">
					<button
						v-for="t in tabs"
						:key="t.key"
						class="tab-btn"
						:class="{ active: currentTab === t.key }"
						@click="currentTab = t.key"
					>
						{{ t.label }}
						<span class="tab-count">{{ countByStatus(t.key) }}</span>
					</button>
				</div>
			</div>

			<div class="task-list">
				<div
					v-for="task in filteredTasks"
					:key="task.id"
					class="task-card"
					:class="statusClass(task.status)"
				>
					<div class="task-head">
						<div class="task-id">#{{ task.id }}</div>
						<span class="status-tag" :class="statusClass(task.status)">{{ task.status }}</span>
					</div>
					<div class="task-name">
						<span class="task-name-icon">📌</span>
						<span class="task-name-text">{{ task.name }}</span>
					</div>
					<div class="task-info">
						<div class="info-row">
							<span class="info-label">航点路径</span>
							<span class="info-value route-value">{{ task.waypoints.join(' → ') || '--' }}</span>
						</div>
						<div class="info-row">
							<span class="info-label">循环次数</span>
							<span class="info-value">{{ task.loops }} 次</span>
						</div>
						<div class="info-row">
							<span class="info-label">巡检时段</span>
							<span class="info-value">{{ task.timeRangeText }}</span>
						</div>
						<div class="info-row">
							<span class="info-label">创建时间</span>
							<span class="info-value">{{ task.createdAt }}</span>
						</div>
					</div>
					<div class="progress-bar">
						<div
							class="progress-fill"
							:style="{ width: task.progress + '%' }"
							:class="statusClass(task.status)"
						></div>
					</div>
					<div class="task-foot">
						<div class="progress-text">进度 {{ task.progress }}% · 已巡 {{ task.doneLoops }}/{{ task.loops }}</div>
						<div class="task-actions">
							<button
								v-if="task.status === '待执行'"
								class="row-btn primary"
								@click="dispatchTask(task)"
							>
								<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<line x1="22" y1="2" x2="11" y2="13"></line>
									<polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
								</svg>
								下发机器人
							</button>
							<button
								v-if="task.status === '执行中'"
								class="row-btn warn"
								@click="stopTask(task)"
							>
								<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<rect x="6" y="6" width="12" height="12" rx="1"></rect>
								</svg>
								停止巡检
							</button>
							<button
								v-if="task.status === '待执行'"
								class="row-btn ghost"
								@click="removeTask(task)"
							>
								<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
									<polyline points="3 6 5 6 21 6"></polyline>
									<path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"></path>
								</svg>
								删除
							</button>
							<span v-if="task.status === '已完成'" class="done-mark">✓ 已完成</span>
						</div>
					</div>
				</div>

				<div v-if="filteredTasks.length === 0" class="empty-state">
					<div class="empty-icon">
						<svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
							<rect x="3" y="4" width="18" height="16" rx="2"></rect>
							<line x1="3" y1="9" x2="21" y2="9"></line>
						</svg>
					</div>
					<div class="empty-text">暂无{{ currentTabLabel }}任务</div>
				</div>
			</div>
		</div>

		<!-- 历史巡检记录 -->
		<div class="card history-card">
			<div class="card-title">
				<svg class="title-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
					<circle cx="12" cy="12" r="10"></circle>
					<polyline points="12 6 12 12 16 14"></polyline>
				</svg>
				<span>历史巡检记录</span>
				<span class="title-count">共 {{ historyList.length }} 条</span>
			</div>
			<div class="table-wrap">
				<table class="data-table">
					<thead>
						<tr>
							<th style="width:60px">序号</th>
							<th style="width:140px">任务编号</th>
							<th>航点路径</th>
							<th style="width:100px">循环次数</th>
							<th style="width:120px">开始时间</th>
							<th style="width:120px">结束时间</th>
							<th style="width:110px">巡逻耗时</th>
							<th style="width:120px">发现隐患</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="(item, idx) in historyList" :key="item.id" :class="{ 'row-warn': item.hazards > 0 }">
							<td class="mono">{{ idx + 1 }}</td>
							<td class="mono">#{{ item.taskId }}</td>
							<td class="route-cell">{{ item.route || '--' }}</td>
							<td>{{ item.loops }} 次</td>
							<td class="time-cell">{{ item.startTime }}</td>
							<td class="time-cell">{{ item.endTime }}</td>
							<td>
								<span class="duration-tag">{{ item.duration }}</span>
							</td>
							<td>
								<span class="hazard-tag" :class="item.hazards > 0 ? 'has' : 'none'">
									{{ item.hazards }} 条
								</span>
							</td>
						</tr>
						<tr v-if="historyList.length === 0">
							<td colspan="8" class="empty-row">
								<div class="empty-state">
									<div class="empty-icon">
										<svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
											<circle cx="12" cy="12" r="10"></circle>
											<polyline points="12 6 12 12 16 14"></polyline>
										</svg>
									</div>
									<div class="empty-text">暂无历史巡检记录</div>
								</div>
							</td>
						</tr>
					</tbody>
				</table>
			</div>
		</div>

		<!-- 编辑坐标弹窗 -->
		<div v-if="coordsDialogVisible" class="modal-overlay" @click.self="coordsDialogVisible = false">
			<div class="modal-container">
				<div class="modal-header">
					<span class="modal-title">编辑航点坐标</span>
					<button class="modal-close" @click="coordsDialogVisible = false">×</button>
				</div>
				<div class="modal-body">
					<div class="form-group">
						<label>航点名称</label>
						<input v-model="editingCoord.name" class="form-input" placeholder="航点名称" />
					</div>
					<div class="form-row">
						<div class="form-group">
							<label>X (m)</label>
							<input v-model.number="editingCoord.x" type="number" step="0.1" class="form-input" />
						</div>
						<div class="form-group">
							<label>Y (m)</label>
							<input v-model.number="editingCoord.y" type="number" step="0.1" class="form-input" />
						</div>
					</div>
					<div class="form-group">
						<label>Z (m)</label>
						<input v-model.number="editingCoord.z" type="number" step="0.1" class="form-input" />
					</div>
					<div class="form-divider">四元数朝向</div>
					<div class="form-row">
						<div class="form-group">
							<label>qx</label>
							<input v-model.number="editingCoord.qx" type="number" step="0.1" class="form-input" />
						</div>
						<div class="form-group">
							<label>qy</label>
							<input v-model.number="editingCoord.qy" type="number" step="0.1" class="form-input" />
						</div>
					</div>
					<div class="form-row">
						<div class="form-group">
							<label>qz</label>
							<input v-model.number="editingCoord.qz" type="number" step="0.1" class="form-input" />
						</div>
						<div class="form-group">
							<label>qw</label>
							<input v-model.number="editingCoord.qw" type="number" step="0.1" class="form-input" />
						</div>
					</div>
					<div class="form-divider">到达自动拍照识别</div>
					<label class="form-checkbox">
						<input type="checkbox" v-model="editingCoord.autoPhoto" />
						<span>到达该航点时，自动调动 T5-AI 摄像头拍照并识别</span>
					</label>
					<div class="form-group" v-if="editingCoord.autoPhoto">
						<label>识别类型</label>
						<select v-model="editingCoord.photoType" class="form-input">
							<option value="person">人员识别</option>
							<option value="flame">多目标检测</option>
							<option value="plate">车牌识别</option>
						</select>
					</div>
					<div class="form-hint">
						坐标为 ROS 世界坐标系（单位：米）。四元数 (qx,qy,qz,qw) 表示机器人朝向。<br/>
						若不确定朝向，可设为 qz=0, qw=1（默认朝前）。
					</div>
				</div>
				<div class="modal-footer">
					<button class="btn btn-cancel" @click="coordsDialogVisible = false">取消</button>
					<button class="btn btn-build" @click="saveCoords">保存</button>
				</div>
			</div>
		</div>

		<!-- 下方提示 toast -->
		<transition name="toast">
			<div v-if="toast.visible" class="toast" :class="toast.type">
				<svg v-if="toast.type === 'success'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
					<polyline points="20 6 9 17 4 12"></polyline>
				</svg>
				<svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
					<circle cx="12" cy="12" r="10"></circle>
					<line x1="12" y1="8" x2="12" y2="12"></line>
					<line x1="12" y1="16" x2="12.01" y2="16"></line>
				</svg>
				<span>{{ toast.msg }}</span>
			</div>
		</transition>
	</div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import request from '../../api/request'
import { getAuth } from '../../stores/community'

/* ============ 当前登录身份（普通用户仅看本社区） ============ */
const auth = getAuth()
const myCommunityId = auth.communityId || null

/* ============ 时间工具（替代原项目的 formatBeijingTime） ============ */
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
function nowIso() {
	const d = new Date()
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function fmtNum(v) {
	if (v == null) return '?'
	const n = Number(v)
	return (n >= 0 ? '' : '-') + Math.abs(n).toFixed(2)
}

/* ============ 统计：各类任务数量 ============ */
const pendingCount = computed(() => allTasks.value.filter(t => t.status === '待执行').length)
const runningCount = computed(() => allTasks.value.filter(t => t.status === '执行中').length)
const doneCount = computed(() => allTasks.value.filter(t => t.status === '已完成').length)

/* ============ 新建任务 ============ */
const waypointInput = ref('')
const newTask = reactive({
	waypoints: [],
	loops: 1,
	startTime: '',
	endTime: ''
})

const coordsDialogVisible = ref(false)
const jsonFileInput = ref(null)
const editingIndex = ref(-1)
const editingCoord = reactive({
	name: '',
	x: 0, y: 0, z: 0,
	qx: 0, qy: 0, qz: 0, qw: 1,
	autoPhoto: false,
	photoType: 'person'
})
const withCoords = computed(() => {
	const n = newTask.waypoints.filter(p => p.x != null).length
	return n ? `，其中 ${n} 个含精确坐标` : '（均未设置精确坐标）'
})
const DOCX_WAYPOINTS = [
	{ name: 'Point_1',  x: -0.06206941604614258, y: 0.014536380767822266, z: 0.0, qx: 0.0, qy: 0.0, qz: 0.0,                 qw: 1.0 },
	{ name: 'Point_2',  x: 3.3250465393066406,  y: -0.06471443176269531, z: 0.0, qx: 0.0, qy: 0.0, qz: 0.013244259373992668, qw: 0.9999122909503785 },
	{ name: 'Point_3',  x: 3.3250465393066406,  y: 1.284637451171875,   z: 0.0, qx: 0.0, qy: 0.0, qz: 0.7102342582669244,   qw: 0.7039654099343459 },
	{ name: 'Point_4',  x: 2.415658950805664,   y: 1.284637451171875,   z: 0.0, qx: 0.0, qy: 0.0, qz: -0.6967058342388733,  qw: 0.7173569408164359 },
	{ name: 'Point_5',  x: 2.434537887573242,   y: 1.284637451171875,   z: 0.0, qx: 0.0, qy: 0.0, qz: 0.7006746903004469,   qw: 0.7134808885824292 },
	{ name: 'Point_6',  x: 1.8541224002838135,  y: 1.284637451171875,   z: 0.0, qx: 0.0, qy: 0.0, qz: 0.9999474833810853,   qw: 0.010248437921664725 },
	{ name: 'Point_7',  x: 1.8541224002838135,  y: 3.3652114868164062,  z: 0.0, qx: 0.0, qy: 0.0, qz: 0.7081899822203627,   qw: 0.7060219182735918 },
	{ name: 'Point_8',  x: 0.646591305732727,   y: 3.3652114868164062,  z: 0.0, qx: 0.0, qy: 0.0, qz: 0.9999127411535306,   qw: 0.013210226297556873 },
	{ name: 'Point_9',  x: 0.646591305732727,   y: 2.6503443717956543,  z: 0.0, qx: 0.0, qy: 0.0, qz: 0.9999210797377179,   qw: 0.012563212015893725 },
	{ name: 'Point_10', x: 0.646591305732727,   y: 0.06968045234680176,  z: 0.0, qx: 0.0, qy: 0.0, qz: 0.9999223074926332,   qw: 0.01246511045309506 },
	{ name: 'Point_11', x: -0.01285707950592041, y: 0.06270623207092285, z: 0.0, qx: 0.0, qy: 0.0, qz: -0.012645382537192192, qw: 0.9999200439537593 }
]

function addWaypoint() {
	const v = waypointInput.value.trim()
	if (!v) return
	if (newTask.waypoints.some(p => p.name === v)) {
		showToast('航点已存在', 'warn')
		return
	}
	if (newTask.waypoints.length >= 20) {
		showToast('最多 20 个航点', 'warn')
		return
	}
	newTask.waypoints.push({ name: v })
	waypointInput.value = ''
}
function importDocxWaypoints() {
	if (newTask.waypoints.length + DOCX_WAYPOINTS.length > 20) {
		showToast(`导入后航点数将超过 20（现有${newTask.waypoints.length}个），请先清理或重置`, 'warn')
		return
	}
	const names = new Set(newTask.waypoints.map(p => p.name))
	for (const wp of DOCX_WAYPOINTS) {
		if (names.has(wp.name)) {
			showToast(`${wp.name} 已存在，跳过`, 'warn')
			continue
		}
		newTask.waypoints.push({ ...wp })
		names.add(wp.name)
	}
	showToast(`已按顺序导入 ${DOCX_WAYPOINTS.length} 个坐标（含 ROS 四元数朝向）`, 'success')
}
function openEditCoords(idx) {
	const wp = newTask.waypoints[idx]
	editingIndex.value = idx
	editingCoord.name = wp.name || ''
	editingCoord.x = wp.x ?? 0
	editingCoord.y = wp.y ?? 0
	editingCoord.z = wp.z ?? 0
	editingCoord.qx = wp.qx ?? 0
	editingCoord.qy = wp.qy ?? 0
	editingCoord.qz = wp.qz ?? 0
	editingCoord.qw = wp.qw ?? 1
	editingCoord.autoPhoto = !!wp.autoPhoto
	editingCoord.photoType = wp.photoType || 'person'
	coordsDialogVisible.value = true
}
function saveCoords() {
	if (editingIndex.value < 0) return
	const idx = editingIndex.value
	const wp = newTask.waypoints[idx]
	const name = editingCoord.name?.trim()
	if (!name) {
		showToast('航点名称不能为空', 'warn')
		return
	}
	newTask.waypoints[idx] = {
		...wp,
		name,
		x: Number(editingCoord.x) || 0,
		y: Number(editingCoord.y) || 0,
		z: Number(editingCoord.z) || 0,
		qx: Number(editingCoord.qx) || 0,
		qy: Number(editingCoord.qy) || 0,
		qz: Number(editingCoord.qz) || 0,
		qw: Number(editingCoord.qw) || 1,
		autoPhoto: !!editingCoord.autoPhoto,
		photoType: editingCoord.photoType || 'person'
	}
	coordsDialogVisible.value = false
	showToast('坐标已保存', 'success')
}
function triggerJsonUpload() {
	jsonFileInput.value?.click()
}
function handleJsonFile(e) {
	const file = e.target.files?.[0]
	if (!file) return
	const reader = new FileReader()
	reader.onload = () => {
		try {
			const data = JSON.parse(reader.result)
			if (!Array.isArray(data)) {
				showToast('JSON 格式错误：必须是数组', 'warn')
				return
			}
			const valid = data.filter(wp => wp && typeof wp === 'object' && wp.name)
			if (valid.length === 0) {
				showToast('未找到有效航点（每条需包含 name 字段）', 'warn')
				return
			}
			if (newTask.waypoints.length + valid.length > 20) {
				showToast(`导入后将超过 20 个航点（现有${newTask.waypoints.length}，文件${valid.length}）`, 'warn')
				return
			}
			const names = new Set(newTask.waypoints.map(p => p.name))
			let imported = 0
			for (const wp of valid) {
				if (names.has(wp.name)) continue
				newTask.waypoints.push({
					name: wp.name,
					x: Number(wp.x) || 0,
					y: Number(wp.y) || 0,
					z: Number(wp.z) || 0,
					qx: Number(wp.qx) || 0,
					qy: Number(wp.qy) || 0,
					qz: Number(wp.qz) || 0,
					qw: Number(wp.qw) || 1
				})
				names.add(wp.name)
				imported++
			}
			showToast(`成功导入 ${imported} 个航点`, 'success')
		} catch (err) {
			showToast('JSON 解析失败：' + (err.message || '格式错误'), 'warn')
		}
	}
	reader.readAsText(file)
	e.target.value = ''
}
function removeWaypoint(idx) {
	if (newTask.waypoints.length <= 2) {
		showToast('至少保留 2 个航点', 'warn')
		return
	}
	newTask.waypoints.splice(idx, 1)
}
function changeLoops(delta) {
	const next = newTask.loops + delta
	if (next >= 1 && next <= 99) newTask.loops = next
}
function resetNewTask() {
	newTask.waypoints = []
	newTask.loops = 1
	newTask.startTime = ''
	newTask.endTime = ''
	waypointInput.value = ''
}

/* ============ 任务列表（四分类） ============ */
const tabs = [
	{ key: '待执行', label: '待执行' },
	{ key: '执行中', label: '执行中' },
	{ key: '已取消', label: '已取消' },
	{ key: '已完成', label: '已完成' }
]
const currentTab = ref('待执行')
const currentTabLabel = computed(() => currentTab.value)
function countByStatus(key) {
	return allTasks.value.filter(t => t.status === key).length
}

const allTasks = ref([])
const hazardsByTask = ref({})
let _pollTimer = null
// 兜底轮询：机器人状态变化由后端写入，前端定时刷新即可
const POLL_INTERVAL_MS = 30000

const STATUS_MAP = { pending: '待执行', running: '执行中', completed: '已完成', cancelled: '已取消', stopped: '已取消' }

/* 进度：已完成 100；未开始/已取消 0；执行中优先取机器人回传的 progress */
function calcProgress(item, status) {
	if (status === '已完成') return 100
	if (status !== '执行中') return 0
	const reported = Number(item.progress)
	if (Number.isFinite(reported)) {
		return Math.min(99, Math.max(0, Math.round(reported)))
	}
	// 后端没有进度时按巡检时段已过比例兜底；时段未设置则视为 0
	const s = toDate(item.startTime)
	const e = toDate(item.endTime)
	if (s && e && e.getTime() > s.getTime()) {
		const p = Math.round((Date.now() - s.getTime()) / (e.getTime() - s.getTime()) * 100)
		return Math.min(99, Math.max(0, p))
	}
	return 0
}

function mapTask(item) {
	const loops = item.loops || 1
	const status = item.taskStatus || '待执行'
	const progress = calcProgress(item, status)
	const rangeStart = item.startTime ? fmtDateTime(item.startTime) : '未设置'
	const rangeEnd = item.endTime ? fmtDateTime(item.endTime) : '未设置'
	return {
		id: item.taskId,
		name: item.taskName || '未命名任务',
		waypoints: Array.isArray(item.waypoints)
			? item.waypoints.map(wp => (wp && typeof wp === 'object')
				? (wp.name || `(${fmtNum(wp.x)},${fmtNum(wp.y)})`)
				: String(wp))
			: [],
		loops,
		createdAt: fmtDateTime(item.createTime),
		startTime: item.startTime,
		endTime: item.endTime,
		timeRangeText: `${rangeStart} ~ ${rangeEnd}`,
		status,
		progress,
		doneLoops: progress >= 100 ? loops : Math.floor(progress / (100 / loops))
	}
}

/* ============ 从后端 API 获取任务数据 ============ */
async function fetchTasks() {
	try {
		const base = { pageNum: 1, pageSize: 200 }
		if (myCommunityId) base.communityId = myCommunityId
		const [taskRes, alarmRes] = await Promise.all([
			request.get('/task/page', { params: base }),
			request.get('/alarm/page', { params: { ...base, pageSize: 500 } })
		])
		allTasks.value = (taskRes.records || []).map(mapTask)
		const map = {}
		for (const a of (alarmRes.records || [])) {
			if (a.taskId) map[a.taskId] = (map[a.taskId] || 0) + 1
		}
		hazardsByTask.value = map
		rebuildHistory()
	} catch (e) {
		console.error('获取任务数据失败:', e)
	}
}

function startPolling() {
	if (_pollTimer) return
	_pollTimer = setInterval(fetchTasks, POLL_INTERVAL_MS)
}
function stopPolling() {
	if (_pollTimer) {
		clearInterval(_pollTimer)
		_pollTimer = null
	}
}

onMounted(async () => {
	await fetchTasks()
	startPolling()
})
onBeforeUnmount(stopPolling)

const filteredTasks = computed(() =>
	allTasks.value.filter(t => t.status === currentTab.value)
)

function statusClass(status) {
	return { '待执行': 'pending', '执行中': 'processing', '已取消': 'stopped', '已完成': 'done' }[status] || 'pending'
}

/* ============ 创建任务 ============ */
async function createTask() {
	if (newTask.waypoints.length < 2) {
		showToast('至少需要 2 个航点', 'warn')
		return
	}
	if (newTask.startTime && newTask.endTime && newTask.endTime <= newTask.startTime) {
		showToast('巡检时段的结束时间需晚于开始时间', 'warn')
		return
	}
	const payload = {
		communityId: myCommunityId,
		userId: auth.userId || null,
		taskName: `${newTask.waypoints[0].name}等${newTask.waypoints.length}点巡检`,
		taskStatus: '待执行',
		loops: newTask.loops,
		startTime: newTask.startTime || null,
		endTime: newTask.endTime || null,
		waypoints: newTask.waypoints.map((p, idx) => ({
			order: idx + 1,
			name: p.name,
			x: p.x,
			y: p.y,
			z: p.z,
			qx: p.qx,
			qy: p.qy,
			qz: p.qz,
			qw: p.qw,
			autoPhoto: !!p.autoPhoto,
			photoType: p.autoPhoto ? (p.photoType || 'person') : null
		}))
	}
	try {
		await request.post('/task', payload)
		await fetchTasks()
		showToast('任务创建成功，可在待执行列表中下发', 'success')
		currentTab.value = '待执行'
		resetNewTask()
	} catch (e) {
		console.error('创建任务失败:', e)
	}
}

/* ============ 下发/停止/删除 ============ */
async function dispatchTask(task) {
	try {
		await request.post(`/task/${task.id}/dispatch`)
		await fetchTasks()
		showToast(`任务 #${task.id} 已下发，机器人将自动启动 SLAM 巡逻`, 'success')
		currentTab.value = '执行中'
	} catch (e) {
		console.error('下发任务失败:', e)
	}
}
async function stopTask(task) {
	try {
		await request.put('/task', { taskId: task.id, taskStatus: '已取消', endTime: nowIso() })
		await fetchTasks()
		showToast(`任务 #${task.id} 已停止`, 'warn')
		currentTab.value = '已取消'
	} catch (e) {
		console.error('停止任务失败:', e)
	}
}
async function removeTask(task) {
	try {
		await request.delete(`/task/${task.id}`)
		await fetchTasks()
		showToast(`任务 #${task.id} 已删除`, 'warn')
	} catch (e) {
		console.error('删除任务失败:', e)
	}
}

/* ============ 历史巡检记录（基于已完成任务聚合） ============ */
const historyList = ref([])
function rebuildHistory() {
	const done = allTasks.value.filter(t => t.status === '已完成')
	historyList.value = done.map(t => {
		const s = toDate(t.startTime)
		const e = toDate(t.endTime)
		let duration = '--'
		if (s && e && e.getTime() > s.getTime()) {
			const min = Math.max(1, Math.round((e.getTime() - s.getTime()) / 60000))
			duration = `${min} 分钟`
		}
		return {
			id: 'H_' + t.id,
			taskId: t.id,
			route: t.waypoints.join(' → '),
			loops: t.loops,
			startTime: t.startTime ? fmtDateTime(t.startTime) : t.createdAt,
			endTime: t.endTime ? fmtDateTime(t.endTime) : '--',
			duration,
			hazards: hazardsByTask.value[t.id] || 0
		}
	})
}

/* ============ Toast ============ */
const toast = reactive({ visible: false, type: 'success', msg: '' })
let toastTimer = null
function showToast(msg, type = 'success') {
	toast.msg = msg
	toast.type = type
	toast.visible = true
	if (toastTimer) clearTimeout(toastTimer)
	toastTimer = setTimeout(() => (toast.visible = false), 2600)
}
</script>

<style scoped>
/* ============ 整体容器 ============ */
.page-wrap {
	min-height: 100%;
	height: auto;
	box-sizing: border-box;
	display: flex;
	flex-direction: column;
	gap: 12px;
	overflow-x: hidden;
	flex: 0 0 auto;
}


/* ============ 统计卡片 ============ */
.task-stats {
	display: grid;
	grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
	gap: 12px;
}
.stat-card {
	background: #FFFFFF;
	border: 1px solid #E8EDEF;
	border-radius: 10px;
	padding: 14px;
	display: flex;
	align-items: center;
	gap: 12px;
	box-shadow: var(--shadow-card);
	transition: box-shadow 0.25s ease;
	animation: cardIn 0.4s ease-out backwards;
}
.stat-card:nth-child(1) { animation-delay: 0.02s; }
.stat-card:nth-child(2) { animation-delay: 0.06s; }
.stat-card:nth-child(3) { animation-delay: 0.10s; }
.stat-card:nth-child(4) { animation-delay: 0.14s; }
.stat-card:hover {
	transform: none;
	box-shadow: var(--shadow-float);
}
.stat-icon {
	width: 32px;
	height: 32px;
	border-radius: 8px;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 18px;
	flex-shrink: 0;
	color: #fff;
}
.stat-icon.stat-blue {
	background: var(--primary);
}
.stat-icon.stat-amber {
	background: #F59E0B;
}
.stat-icon.stat-indigo {
	background: #0EA5E9;
}
.stat-icon.stat-green {
	background: #10B981;
}
.stat-body {
	flex: 1;
	min-width: 0;
}
.stat-num {
	font-size: 22px;
	font-weight: 700;
	color: var(--text-main);
	line-height: 1.2;
	letter-spacing: 0.4px;
}
.stat-label {
	font-size: 12px;
	color: var(--text-sub);
	margin-top: 3px;
	letter-spacing: 0.4px;
}

/* ============ 卡片通用 ============ */
.card {
	background: #FFFFFF;
	border: 1px solid #E8EDEF;
	border-radius: 10px;
	padding: 14px;
	box-shadow: var(--shadow-card);
	animation: cardIn 0.45s ease-out backwards;
}
.card-title {
	font-size: 14px;
	font-weight: 600;
	color: var(--text-main);
	margin-bottom: 10px;
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
	letter-spacing: 0.3px;
}
.title-icon {
	color: var(--primary);
	flex-shrink: 0;
}
.title-count {
	font-size: 12px;
	font-weight: 400;
	color: var(--text-soft);
	margin-left: 4px;
}

/* ============ 新建任务区 ============ */
.build-body {
	display: flex;
	flex-direction: column;
	gap: 18px;
}
.build-item {
	display: flex;
	flex-direction: column;
	gap: 8px;
}
.build-grid {
	display: flex;
	flex-wrap: wrap;
	gap: 16px;
	align-items: flex-end;
}
.build-cell {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 8px;
}
.cell-loops {
	flex: 0 0 auto;
}
.cell-time {
	flex: 1 1 300px;
}
.build-label {
	font-size: 13px;
	font-weight: 600;
	color: #406070;
	letter-spacing: 0.5px;
}

/* 航点列表 */
.waypoint-box {
	display: flex;
	flex-direction: column;
	gap: 8px;
}
.waypoint-list {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	align-items: center;
	padding: 10px;
	background: #F4F9FA;
	border: 1px dashed #B0D0D8;
	border-radius: 8px;
	min-height: 56px;
	max-height: 180px;
	overflow-y: auto;
	align-content: flex-start;
}
.wp-empty {
	margin: auto;
	font-size: 12px;
	color: #B0C8D0;
	letter-spacing: 0.3px;
}
.waypoint-tag {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 5px 10px 5px 6px;
	background: #FFFFFF;
	border: 1px solid #C0E0E8;
	border-radius: 999px;
	font-size: 13px;
	color: var(--text-main);
	box-shadow: 0 1px 2px rgba(0, 96, 128, 0.06);
}
.waypoint-tag.has-xy {
	border-color: var(--primary-light);
	background: linear-gradient(135deg, #F2FAFC 0%, #E6F4F8 100%);
}
.wp-photo {
	font-size: 12px;
	line-height: 1;
}
.wp-name {
	font-weight: 600;
}
.wp-coords {
	font-family: 'Consolas', monospace;
	font-size: 11px;
	color: var(--primary-light);
	background: rgba(0, 128, 160, 0.08);
	padding: 1px 6px;
	border-radius: 4px;
	letter-spacing: -0.2px;
}
.wp-idx {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 20px;
	height: 20px;
	border-radius: 50%;
	background: linear-gradient(135deg, var(--primary), var(--primary-light));
	color: #fff;
	font-size: 11px;
	font-weight: 700;
}
.wp-del {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 18px;
	height: 18px;
	border: none;
	background: transparent;
	color: var(--text-soft);
	cursor: pointer;
	border-radius: 50%;
	transition: all 0.2s ease;
}
.wp-del:hover {
	background: #FFE0E0;
	color: #E54848;
}
.wp-edit {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 18px;
	height: 18px;
	border: none;
	background: transparent;
	color: var(--primary-light);
	cursor: pointer;
	border-radius: 50%;
	transition: all 0.2s ease;
}
.wp-edit:hover {
	background: #E6F4F8;
	color: var(--primary);
}
.wp-upload {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 7px 12px;
	background: linear-gradient(135deg, #E8F5E9, #C8E6C9);
	color: #2E7D32;
	border: 1px solid #A5D6A7;
	border-radius: 8px;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
	transition: all 0.2s ease;
	white-space: nowrap;
}
.wp-upload:hover {
	background: linear-gradient(135deg, #A5D6A7, #81C784);
	color: #1B5E20;
	border-color: #66BB6A;
	transform: translateY(-1px);
	box-shadow: 0 3px 8px rgba(100, 180, 100, 0.2);
}
.wp-input-wrap {
	display: flex;
	gap: 6px;
	flex-wrap: wrap;
	align-items: center;
	margin-top: 4px;
}
.wp-input {
	flex: 1;
	padding: 7px 12px;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-size: 13px;
	color: var(--text-main);
	background: #FFFFFF;
	outline: none;
	transition: border-color 0.2s ease;
}
.wp-input:focus {
	border-color: var(--primary-light);
	box-shadow: 0 0 0 3px rgba(0, 128, 160, 0.1);
}
.wp-add {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 7px 14px;
	background: #E6F4F8;
	color: var(--primary);
	border: 1px solid #C0E0E8;
	border-radius: 8px;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
	transition: all 0.2s ease;
}
.wp-add:hover {
	background: var(--primary-light);
	color: #fff;
	border-color: var(--primary-light);
}
.wp-import {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 7px 12px;
	background: linear-gradient(135deg, #FFF5E1, #FFEBC7);
	color: #8A5A00;
	border: 1px solid #E6C475;
	border-radius: 8px;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
	transition: all 0.2s ease;
	white-space: nowrap;
}
.wp-import:hover {
	background: linear-gradient(135deg, #FFE0A0, #FFD370);
	color: #5A3B00;
	border-color: #D4A040;
	transform: translateY(-1px);
	box-shadow: 0 3px 8px rgba(210, 150, 50, 0.2);
}
.wp-hint {
	font-size: 12px;
	color: var(--text-soft);
	padding-left: 2px;
}

/* 循环次数输入 */
.loops-input {
	display: flex;
	align-items: center;
	gap: 8px;
}
.loops-btn {
	width: 32px;
	height: 36px;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	background: #FFFFFF;
	border: 1px solid var(--border);
	border-radius: 8px;
	color: #406070;
	cursor: pointer;
	transition: all 0.2s ease;
}
.loops-btn:hover:not(:disabled) {
	background: #E6F4F8;
	border-color: var(--primary-light);
	color: var(--primary-light);
}
.loops-btn:disabled {
	opacity: 0.4;
	cursor: not-allowed;
}
.loops-num {
	width: 56px;
	height: 36px;
	text-align: center;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-size: 15px;
	font-weight: 700;
	color: var(--text-main);
	background: #FFFFFF;
	outline: none;
	-moz-appearance: textfield;
}
.loops-num::-webkit-outer-spin-button,
.loops-num::-webkit-inner-spin-button {
	-webkit-appearance: none;
	margin: 0;
}
.loops-suffix {
	font-size: 13px;
	color: var(--text-sub);
}

/* 巡检时段 */
.time-range {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: nowrap;
}
.time-input {
	flex: 1;
	min-width: 0;
	height: 36px;
	padding: 0 10px;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-size: 13px;
	color: var(--text-main);
	background: #FFFFFF;
	outline: none;
	font-family: inherit;
	transition: all 0.2s ease;
}
.time-input:focus {
	border-color: var(--primary-light);
	box-shadow: 0 0 0 3px rgba(0, 128, 160, 0.1);
}
.time-sep {
	font-size: 12px;
	color: var(--text-soft);
	flex-shrink: 0;
}

/* 按钮组 */
.build-actions {
	margin-left: auto;
	display: flex;
	gap: 8px;
}
.btn {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 9px 16px;
	border: none;
	border-radius: 8px;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
	transition: all 0.25s ease;
	letter-spacing: 0.5px;
}
.btn-build {
	background: linear-gradient(135deg, var(--primary), var(--primary-light));
	color: #fff;
	box-shadow: 0 3px 10px rgba(0, 96, 128, 0.3);
}
.btn-build:hover:not(:disabled) {
	transform: translateY(-1px);
	box-shadow: 0 5px 14px rgba(0, 96, 128, 0.4);
}
.btn-build:disabled {
	background: #B0C8D0;
	box-shadow: none;
	cursor: not-allowed;
}
.btn-reset {
	background: #FFFFFF;
	color: var(--text-sub);
	border: 1px solid var(--border);
}
.btn-reset:hover {
	background: #F4F9FA;
	border-color: var(--text-soft);
}

/* ============ 任务列表 ============ */
.tab-group {
	display: inline-flex;
	background: #F0F6F7;
	padding: 3px;
	border-radius: 999px;
	margin-left: auto;
}
.tab-btn {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	padding: 6px 16px;
	background: transparent;
	border: none;
	border-radius: 999px;
	font-size: 13px;
	font-weight: 500;
	color: var(--text-sub);
	cursor: pointer;
	transition: all 0.25s ease;
}
.tab-btn:hover {
	color: var(--text-main);
}
.tab-btn.active {
	background: #FFFFFF;
	color: var(--primary);
	font-weight: 600;
	box-shadow: 0 1px 3px rgba(0, 96, 128, 0.15);
}
.tab-count {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	min-width: 18px;
	height: 18px;
	padding: 0 5px;
	background: var(--border);
	color: #406070;
	border-radius: 999px;
	font-size: 11px;
	font-weight: 600;
}
.tab-btn.active .tab-count {
	background: var(--primary-light);
	color: #fff;
}

.task-list {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
	gap: 12px;
}
.task-card {
	position: relative;
	background: #FFFFFF;
	border: 1px solid #E8EDEF;
	border-radius: 10px;
	padding: 14px;
	box-shadow: var(--shadow-card);
	transition: box-shadow 0.25s ease;
	overflow: hidden;
	animation: cardIn 0.4s ease-out backwards;
}
.task-card::before {
	content: '';
	position: absolute;
	left: 0;
	top: 0;
	width: 100%;
	height: 3px;
	background: linear-gradient(90deg, var(--primary), var(--primary-light));
}
.task-card.pending::before {
	background: linear-gradient(90deg, #EF4444, #F87171);
}
.task-card.processing::before {
	background: linear-gradient(90deg, #F59E0B, #FBBF24);
}
.task-card.stopped::before {
	background: linear-gradient(90deg, #9CA3AF, #D1D5DB);
}
.task-card.done::before {
	background: linear-gradient(90deg, #22C55E, #34D399);
}
.task-card:hover {
	transform: none;
	box-shadow: var(--shadow-float);
}

.task-head {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 10px;
}
.task-id {
	font-size: 12px;
	color: var(--text-soft);
	font-family: ui-monospace, SFMono-Regular, monospace;
	letter-spacing: 0.5px;
}
.status-tag {
	font-size: 12px;
	font-weight: 600;
	padding: 4px 12px;
	border-radius: 999px;
	letter-spacing: 0.5px;
}
.status-tag.pending {
	background: #FEF2F2;
	color: #EF4444;
}
.status-tag.processing {
	background: #FEF3C7;
	color: #D97706;
}
.status-tag.done {
	background: #ECFDF5;
	color: #10B981;
}
.status-tag.stopped {
	background: #F3F4F6;
	color: #6B7280;
}

.task-name {
	display: flex;
	align-items: center;
	gap: 6px;
	font-size: 15px;
	font-weight: 600;
	color: var(--text-main);
	margin-bottom: 12px;
	letter-spacing: 0.5px;
}
.task-name-icon {
	font-size: 14px;
}
.task-info {
	display: flex;
	flex-direction: column;
	gap: 6px;
	margin-bottom: 12px;
	padding: 10px 12px;
	background: #F4F9FA;
	border-radius: 8px;
}
.info-row {
	display: flex;
	justify-content: space-between;
	gap: 8px;
	font-size: 12px;
}
.info-label {
	color: var(--text-soft);
	flex-shrink: 0;
}
.info-value {
	color: #406070;
	font-weight: 500;
	text-align: right;
	word-break: break-all;
}
.route-value {
	font-size: 11.5px;
	max-width: 220px;
}
.progress-bar {
	height: 8px;
	background: var(--border);
	border-radius: 999px;
	overflow: hidden;
}
.progress-fill {
	height: 100%;
	border-radius: 999px;
	transition: width 0.4s ease;
}
.progress-fill.pending {
	background: linear-gradient(90deg, #EF4444, #F87171);
}
.progress-fill.processing {
	background: linear-gradient(90deg, #F59E0B, #FBBF24);
}
.progress-fill.stopped {
	background: linear-gradient(90deg, #9CA3AF, #D1D5DB);
}
.progress-fill.done {
	background: linear-gradient(90deg, #22C55E, #34D399);
}
.task-foot {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-top: 10px;
	gap: 8px;
}
.progress-text {
	font-size: 11.5px;
	color: var(--text-sub);
	letter-spacing: 0.3px;
}
.task-actions {
	display: flex;
	gap: 6px;
	flex-shrink: 0;
}
.row-btn {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 5px 10px;
	font-size: 12px;
	font-weight: 600;
	border: none;
	border-radius: 6px;
	cursor: pointer;
	transition: all 0.2s ease;
	letter-spacing: 0.3px;
}
.row-btn.primary {
	background: linear-gradient(135deg, var(--primary), var(--primary-light));
	color: #fff;
	box-shadow: 0 2px 6px rgba(0, 96, 128, 0.25);
}
.row-btn.primary:hover {
	transform: translateY(-1px);
	box-shadow: 0 4px 10px rgba(0, 96, 128, 0.35);
}
.row-btn.warn {
	background: #FEF3C7;
	color: #D97706;
}
.row-btn.warn:hover {
	background: #F59E0B;
	color: #fff;
}
.row-btn.ghost {
	background: transparent;
	color: var(--text-soft);
}
.row-btn.ghost:hover {
	background: #FEF2F2;
	color: #EF4444;
}
.done-mark {
	font-size: 12px;
	color: #10B981;
	font-weight: 600;
}

/* ============ 历史记录表格 ============ */
.table-wrap {
	overflow-x: auto;
	border-radius: 8px;
	border: 1px solid #E0EAEC;
}
.data-table {
	width: 100%;
	border-collapse: collapse;
	font-size: 13px;
	min-width: 880px;
}
.data-table thead {
	background: #F4F9FA;
}
.data-table th {
	padding: 12px 14px;
	text-align: left;
	font-weight: 600;
	color: #406070;
	font-size: 12px;
	letter-spacing: 0.5px;
	border-bottom: 1px solid #E0EAEC;
	white-space: nowrap;
}
.data-table td {
	padding: 12px 14px;
	color: #406070;
	border-bottom: 1px solid #F0F4F5;
	vertical-align: middle;
}
.data-table tbody tr {
	transition: background 0.2s ease;
}
.data-table tbody tr:hover {
	background: #F8FCFD;
}
.data-table tbody tr.row-warn {
	background: #FFF9F0;
}
.data-table tbody tr.row-warn:hover {
	background: #FFF4E8;
}
.data-table tbody tr:last-child td {
	border-bottom: none;
}
.mono {
	font-family: ui-monospace, SFMono-Regular, monospace;
	font-size: 12px;
	color: var(--text-sub);
}
.time-cell {
	font-size: 12px;
	color: var(--text-sub);
	white-space: nowrap;
}
.route-cell {
	font-size: 12px;
	color: #406070;
	max-width: 280px;
}
.duration-tag {
	display: inline-block;
	padding: 3px 10px;
	background: #E6F4F8;
	color: var(--primary);
	border-radius: 999px;
	font-size: 12px;
	font-weight: 600;
}
.hazard-tag {
	display: inline-block;
	padding: 3px 10px;
	border-radius: 999px;
	font-size: 12px;
	font-weight: 600;
}
.hazard-tag.none {
	background: #ECFDF5;
	color: #10B981;
}
.hazard-tag.has {
	background: #FEF3C7;
	color: #D97706;
}

/* ============ 空状态 ============ */
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 40px 20px;
	color: var(--text-soft);
}
.task-list > .empty-state {
	grid-column: 1 / -1;
	width: 100%;
	box-sizing: border-box;
}
.empty-icon {
	color: #B0C8D0;
	margin-bottom: 10px;
}
.empty-text {
	font-size: 13px;
	letter-spacing: 0.5px;
}
.empty-row .empty-state {
	padding: 30px;
}

/* ============ Toast ============ */
.toast {
	position: fixed;
	top: 20px;
	left: 50%;
	transform: translateX(-50%);
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 10px 20px;
	border-radius: 999px;
	font-size: 13px;
	font-weight: 600;
	color: #fff;
	box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
	z-index: 9999;
	letter-spacing: 0.3px;
}
.toast.success {
	background: linear-gradient(135deg, #059669, #10B981);
}
.toast.warn {
	background: linear-gradient(135deg, #D97706, #F59E0B);
}
.toast-enter-active,
.toast-leave-active {
	transition: all 0.3s ease;
}
.toast-enter-from,
.toast-leave-to {
	opacity: 0;
	transform: translateX(-50%) translateY(-10px);
}

/* ============ 编辑坐标弹窗 ============ */
.modal-overlay {
	position: fixed;
	inset: 0;
	background: rgba(15, 30, 40, 0.55);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 9998;
	animation: fadeIn 0.2s ease-out;
}
.modal-container {
	background: #FFFFFF;
	border-radius: 14px;
	width: min(480px, 92vw);
	max-height: 90vh;
	display: flex;
	flex-direction: column;
	box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
	overflow: hidden;
	animation: modalIn 0.25s ease-out;
}
.modal-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 16px 20px;
	border-bottom: 1px solid #E0EAEC;
	background: linear-gradient(135deg, #F4F9FA, #E6F4F8);
}
.modal-title {
	font-size: 16px;
	font-weight: 700;
	color: var(--text-main);
	letter-spacing: 0.5px;
}
.modal-close {
	width: 28px;
	height: 28px;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	border: none;
	background: transparent;
	color: var(--text-soft);
	font-size: 22px;
	line-height: 1;
	cursor: pointer;
	border-radius: 50%;
	transition: all 0.2s ease;
}
.modal-close:hover {
	background: rgba(229, 72, 72, 0.1);
	color: #E54848;
}
.modal-body {
	padding: 20px;
	overflow-y: auto;
	flex: 1;
}
.modal-footer {
	display: flex;
	justify-content: flex-end;
	gap: 10px;
	padding: 14px 20px;
	border-top: 1px solid #E0EAEC;
	background: #FAFCFD;
}
.form-group {
	display: flex;
	flex-direction: column;
	gap: 6px;
	flex: 1;
	min-width: 0;
}
.form-group label {
	font-size: 12px;
	font-weight: 600;
	color: var(--text-sub);
	letter-spacing: 0.3px;
}
.form-input {
	padding: 8px 12px;
	border: 1px solid var(--border);
	border-radius: 8px;
	font-size: 13px;
	color: var(--text-main);
	background: #FFFFFF;
	outline: none;
	transition: all 0.2s ease;
	font-family: inherit;
}
.form-input:focus {
	border-color: var(--primary-light);
	box-shadow: 0 0 0 3px rgba(0, 128, 160, 0.1);
}
.form-row {
	display: flex;
	gap: 12px;
}
.form-row .form-group {
	flex: 1;
}
.form-divider {
	font-size: 12px;
	font-weight: 600;
	color: var(--primary);
	margin: 14px 0 10px;
	padding: 6px 12px;
	background: #E6F4F8;
	border-radius: 6px;
	letter-spacing: 0.5px;
}
.form-checkbox {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 13px;
	color: var(--text-main);
	cursor: pointer;
	user-select: none;
}
.form-checkbox input[type='checkbox'] {
	width: 15px;
	height: 15px;
	accent-color: var(--primary-light);
	cursor: pointer;
}
.form-hint {
	margin-top: 14px;
	padding: 10px 12px;
	background: #FFF9F0;
	border: 1px solid #FFE0B2;
	border-radius: 8px;
	font-size: 12px;
	color: #8A5A00;
	line-height: 1.6;
}
.btn-cancel {
	background: #FFFFFF;
	color: var(--text-sub);
	border: 1px solid var(--border);
}
.btn-cancel:hover {
	background: #F4F9FA;
	border-color: var(--text-soft);
}

@keyframes fadeIn {
	from { opacity: 0; }
	to { opacity: 1; }
}
@keyframes modalIn {
	from { opacity: 0; transform: translateY(-20px) scale(0.96); }
	to { opacity: 1; transform: translateY(0) scale(1); }
}

/* ============ 入场动画 ============ */
@keyframes cardIn {
	from { opacity: 0; transform: translateY(10px); }
	to { opacity: 1; transform: translateY(0); }
}

/* ============ 响应式 ============ */
@media (max-width: 768px) {
	.build-grid {
		flex-direction: column;
		align-items: stretch;
		gap: 16px;
	}
	.cell-loops,
	.cell-time {
		flex: 1 1 100%;
	}
	.build-actions {
		margin-left: 0;
		justify-content: stretch;
	}
	.build-actions .btn {
		flex: 1;
		justify-content: center;
	}
	.task-list {
		grid-template-columns: 1fr;
	}
	.route-value {
		max-width: 100%;
	}
}
@media (max-width: 480px) {
	.tab-group {
		margin-left: 0;
		width: 100%;
		justify-content: space-between;
	}
	.tab-btn {
		flex: 1;
		justify-content: center;
	}
	.task-foot {
		flex-direction: column;
		align-items: stretch;
	}
	.task-actions {
		justify-content: flex-end;
	}
}
</style>
