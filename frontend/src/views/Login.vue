<template>
	<div class="auth-page">
		<!-- 左侧品牌展示区 -->
		<aside class="brand-panel">
			<div class="brand-deco deco-1"></div>
			<div class="brand-deco deco-2"></div>
			<div class="brand-deco deco-3"></div>

			<div class="brand-top">
				<div class="brand-logo">
				<img class="logo-mark" src="/src/assets/logo.png" alt="Logo" />
				<span class="logo-text">安防巡检</span>
			</div>
				<div class="brand-tag">SECURITY · INSPECTION · INTELLIGENCE</div>
			</div>

			<div class="brand-center">
				<div class="brand-title">社区安防<br />智能巡检系统</div>
				<div class="brand-sub">
					Community Security Inspection System
				</div>
				<div class="brand-desc">
					全天候智能巡检 · 实时告警推送 · 数据可视化分析
				</div>
			</div>

			<div class="brand-bottom">
				<div class="brand-meta">
					<span class="dot"></span>
					系统运行正常
				</div>
				<div class="brand-copy">© 2026 安防巡检系统 · All Rights Reserved</div>
			</div>
		</aside>

		<!-- 右侧表单区 -->
		<main class="form-panel">
			<div class="form-card">
				<div class="form-header">
					<div class="form-title">
						{{ mode === 'login' ? '账户登录' : '注册账户' }}
					</div>
					<div class="form-sub">
						{{ mode === 'login' ? '请使用授权账号登录系统' : '创建您的巡检系统账户' }}
					</div>
				</div>

				<div class="tab-switch">
					<button
						:class="{ active: mode === 'login' }"
						@click="switchMode('login')"
					>
						登录
					</button>
					<button
						:class="{ active: mode === 'register' }"
						@click="switchMode('register')"
					>
						注册
					</button>
					<span class="tab-indicator" :class="mode"></span>
				</div>

				<form class="auth-form" @submit.prevent="onSubmit">
					<div class="field">
						<label>账号</label>
						<input
							v-model.trim="form.username"
							type="text"
							placeholder="请输入账号（至少 3 位）"
							autocomplete="off"
						/>
						<span v-if="errors.username" class="err">{{ errors.username }}</span>
					</div>

					<div class="field">
						<label>密码</label>
						<input
							v-model.trim="form.password"
							type="password"
							placeholder="请输入密码（6-16 位）"
							autocomplete="off"
						/>
						<span v-if="errors.password" class="err">{{ errors.password }}</span>
					</div>

					<div v-if="mode === 'register'" class="field">
						<label>确认密码</label>
						<input
							v-model.trim="form.confirm"
							type="password"
							placeholder="请再次输入密码"
							autocomplete="off"
						/>
						<span v-if="errors.confirm" class="err">{{ errors.confirm }}</span>
					</div>

					<div v-if="mode === 'login'" class="row-between">
						<label class="remember">
							<input v-model="remember" type="checkbox" />
							<span>记住我</span>
						</label>
						<a class="forget" @click="tip('请联系管理员重置密码')">忘记密码？</a>
					</div>

					<div v-if="mode === 'login'" class="role-select">
						<label class="role-label">登录身份</label>
						<el-select
							v-model="selectedRole"
							class="role-select-box"
							placeholder="请选择登录身份"
						>
							<el-option
								v-for="opt in roleOptions"
								:key="opt.value"
								:label="opt.label"
								:value="opt.value"
							/>
						</el-select>
					</div>

					<button class="submit-btn" type="submit" :disabled="loading">
						<span class="btn-text">
							{{ loading ? '处理中...' : (mode === 'login' ? '登 录' : '注 册') }}
						</span>
						<span class="btn-arrow">→</span>
					</button>

					<div v-if="globalError" class="global-err">
						<span class="msg-icon">!</span>{{ globalError }}
					</div>
					<div v-if="globalOk" class="global-ok">
						<span class="msg-icon">✓</span>{{ globalOk }}
					</div>
				</form>

				<div class="auth-foot">
					<span v-if="mode === 'login'">
						还没有账号？
						<a @click="switchMode('register')">立即注册</a>
					</span>
					<span v-else>
						已有账号？
						<a @click="switchMode('login')">返回登录</a>
					</span>
				</div>
			</div>
		</main>
	</div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../api/request'

const router = useRouter()
const route = useRoute()

const mode = ref('login')
const loading = ref(false)
const remember = ref(true)
const globalError = ref('')
const globalOk = ref('')

const roleOptions = [
	{ label: '普通用户', value: '普通用户' },
	{ label: '管理员', value: '管理员' },
	{ label: '超级管理员', value: '超级管理员' }
]
const selectedRole = ref('普通用户')

const form = reactive({
	username: '',
	password: '',
	confirm: ''
})
const errors = reactive({
	username: '',
	password: '',
	confirm: ''
})

function switchMode(m) {
	mode.value = m
	globalError.value = ''
	globalOk.value = ''
	Object.keys(errors).forEach(k => (errors[k] = ''))
}

function tip(msg) {
	globalOk.value = ''
	globalError.value = msg
}

/* ====== 表单校验 ====== */
function validate() {
	let ok = true
	Object.keys(errors).forEach(k => (errors[k] = ''))

	if (!form.username) {
		errors.username = '请输入账号'
		ok = false
	} else if (form.username.length < 3) {
		errors.username = '账号至少 3 位'
		ok = false
	} else if (!/^[A-Za-z0-9_]+$/.test(form.username)) {
		errors.username = '账号仅支持字母、数字、下划线'
		ok = false
	}

	if (!form.password) {
		errors.password = '请输入密码'
		ok = false
	} else if (form.password.length < 6 || form.password.length > 16) {
		errors.password = '密码长度 6-16 位'
		ok = false
	}

	if (mode.value === 'register') {
		if (!form.confirm) {
			errors.confirm = '请再次输入密码'
			ok = false
		} else if (form.confirm !== form.password) {
			errors.confirm = '两次输入的密码不一致'
			ok = false
		}
	}
	return ok
}

/* ====== 提交 ====== */
async function onSubmit() {
	globalError.value = ''
	globalOk.value = ''
	if (!validate()) return

	loading.value = true
	try {
		if (mode.value === 'register') {
			await doRegister()
		} else {
			await doLogin()
		}
	} catch (err) {
		// request.js 拦截器已经弹 ElMessage，这里兜底
		const msg = err.response?.data?.message || err.message || '网络错误，请稍后重试'
		globalError.value = msg
	} finally {
		loading.value = false
	}
}

/* ====== 注册：调用后端 POST /api/auth/register ====== */
async function doRegister() {
	await request.post('/auth/register', {
		userName: form.username,
		password: form.password
	})
	globalOk.value = '注册成功，即将跳转登录...'
	setTimeout(() => {
		switchMode('login')
		form.confirm = ''
		form.password = ''
		globalOk.value = ''
	}, 800)
}

/* ====== 登录：调用后端 POST /api/auth/login ======
 * request.js 拦截器自动解包 Result<T>，所以 data 直接就是 TokenVO：
 *   { token, tokenType, expiresIn, user: { userId, userName, name, role, communityId } }
 *
 * 登录身份规则（三选一）：
 *   1. 普通用户   —— 任何账号（含管理员）都按普通用户进入普通用户页面
 *   2. 管理员     —— 数据库角色必须为 管理员（超级管理员不能以管理员身份登录）
 *   3. 超级管理员 —— 数据库角色必须为 超级管理员
 */
async function doLogin() {
	const data = await request.post('/auth/login', {
		userName: form.username,
		password: form.password
	})
	const { token, user } = data

	// 数据库真实角色
	const dbRole = user.role || '普通用户'
	const wantRole = selectedRole.value

	// 身份校验：必须与数据库角色完全一致
	if (wantRole === '管理员' && dbRole !== '管理员') {
		globalError.value = '该账号的管理员身份不匹配，请选择正确的登录身份'
		return
	}
	if (wantRole === '超级管理员' && dbRole !== '超级管理员') {
		globalError.value = '该账号不是超级管理员，请选择其他登录身份'
		return
	}

	// 生效角色：选普通用户一律降权，否则与数据库角色一致
	const effectiveRole = wantRole === '普通用户' ? '普通用户' : dbRole

	const store = remember.value ? localStorage : sessionStorage
	const otherStore = remember.value ? sessionStorage : localStorage

	// 统一登录态：security_auth（router guard + Home.vue 读这个）
	const authPayload = {
		token,
		userId: user.userId,
		username: user.userName,
		name: user.name,
		role: effectiveRole,     // 当前登录身份（生效角色）
		dbRole,                  // 账号真实角色
		loginAs: wantRole,
		communityId: user.communityId,
		loginAt: Date.now()
	}
	store.setItem('security_auth', JSON.stringify(authPayload))
	store.setItem('token', token)
	otherStore.removeItem('security_auth')
	otherStore.removeItem('token')

	const redirect = route.query.redirect || (wantRole === '普通用户' ? '/home' : '/admin/users')
	router.replace(redirect)
}
</script>

<style scoped>
/* ============ 整体布局（与主页 Home 一致：#F0F6F7 底 + 白卡 + 蓝主色） ============ */
.auth-page {
	width: 100%;
	height: 100vh;
	min-height: 600px;
	display: flex;
	background: #F0F6F7;
	box-sizing: border-box;
	overflow: hidden;
}

/* ============ 左侧品牌区（淡蓝渐变，呼应主页蓝色系） ============ */
.brand-panel {
	flex: 1 1 52%;
	min-width: 0;
	position: relative;
	background:
		radial-gradient(circle at 18% 20%, rgba(0, 96, 128, 0.10), transparent 45%),
		linear-gradient(150deg, #E8F4F8 0%, #C0E0E8 55%, #A0D0D8 100%);
	display: flex;
	flex-direction: column;
	justify-content: space-between;
	padding: clamp(28px, 3.5vw, 48px);
	box-sizing: border-box;
	overflow: hidden;
}
/* 几何装饰 */
.brand-deco {
	position: absolute;
	border: 1px solid rgba(0, 96, 128, 0.14);
	pointer-events: none;
}
.deco-1 {
	width: 420px;
	height: 420px;
	border-radius: 50%;
	right: -180px;
	top: -180px;
	border-color: rgba(0, 96, 128, 0.18);
}
.deco-2 {
	width: 260px;
	height: 260px;
	border-radius: 50%;
	right: -80px;
	top: -80px;
	border-color: rgba(0, 128, 160, 0.20);
}
.deco-3 {
	width: 2px;
	height: 120px;
	background: linear-gradient(to bottom, transparent, #006080, transparent);
	left: 12%;
	top: 50%;
	transform: translateY(-50%);
	opacity: 0.5;
}

.brand-top {
	position: relative;
	z-index: 2;
}
.brand-logo {
	display: flex;
	align-items: center;
	gap: 12px;
}
.logo-mark {
	width: 36px;
	height: 36px;
	object-fit: contain;
	border-radius: 8px;
}
.logo-text {
	font-size: 22px;
	font-weight: 800;
	color: #004060;
	letter-spacing: 4px;
}
.brand-tag {
	margin-top: 10px;
	font-size: 11px;
	color: #006080;
	letter-spacing: 2px;
	font-weight: 500;
	opacity: 0.7;
}

.brand-center {
	position: relative;
	z-index: 2;
	max-width: 480px;
}
.brand-title {
	font-size: clamp(28px, 3.4vw, 40px);
	font-weight: 700;
	color: #1E3A40;
	line-height: 1.25;
	letter-spacing: 2px;
}
.brand-sub {
	margin-top: 14px;
	font-size: 13px;
	color: #406070;
	letter-spacing: 1px;
	font-weight: 400;
}
.brand-desc {
	margin-top: 22px;
	font-size: 14px;
	color: #507080;
	line-height: 1.8;
	padding-left: 14px;
	border-left: 2px solid #006080;
}

.brand-bottom {
	position: relative;
	z-index: 2;
	display: flex;
	flex-direction: column;
	gap: 8px;
}
.brand-meta {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 12px;
	color: #406070;
	letter-spacing: 0.5px;
}
.brand-meta .dot {
	width: 8px;
	height: 8px;
	border-radius: 50%;
	background: #22C55E;
	box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
	animation: blink 2s infinite;
}
@keyframes blink {
	0%, 100% { opacity: 1; }
	50% { opacity: 0.4; }
}
.brand-copy {
	font-size: 11px;
	color: #80A0A8;
	letter-spacing: 0.5px;
}

/* ============ 右侧表单区（白卡，与主页 .card 风格统一） ============ */
.form-panel {
	flex: 1 1 48%;
	min-width: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: clamp(20px, 3vw, 40px);
	box-sizing: border-box;
	background: #F0F6F7;
}
.form-card {
	width: 100%;
	max-width: 380px;
	background: #FFFFFF;
	border-radius: 10px;
	padding: clamp(24px, 3vw, 36px);
	box-shadow: 0 1px 3px rgba(15, 30, 40, 0.06), 0 10px 30px rgba(0, 96, 128, 0.06);
	box-sizing: border-box;
	animation: cardIn 0.4s ease-out;
}
@keyframes cardIn {
	from { opacity: 0; transform: translateY(10px); }
	to { opacity: 1; transform: translateY(0); }
}

.form-header {
	margin-bottom: 24px;
}
.form-title {
	font-size: 20px;
	font-weight: 700;
	color: #1E3A40;
	letter-spacing: 1px;
}
.form-sub {
	margin-top: 8px;
	font-size: 13px;
	color: #507080;
	letter-spacing: 0.5px;
}

/* ============ Tab 切换 ============ */
.tab-switch {
	position: relative;
	display: flex;
	margin-bottom: 22px;
	border-bottom: 1px solid #D0E0E4;
}
.tab-switch button {
	flex: 1;
	padding: 10px 0;
	border: none;
	background: transparent;
	font-size: 14px;
	font-weight: 500;
	color: #507080;
	cursor: pointer;
	transition: color 0.25s;
	letter-spacing: 1px;
}
.tab-switch button.active {
	color: #006080;
	font-weight: 600;
}
.tab-switch button:hover:not(.active) {
	color: #406070;
}
.tab-indicator {
	position: absolute;
	bottom: -1px;
	height: 2px;
	background: #006080;
	transition: left 0.3s ease;
	width: 50%;
	left: 0;
}
.tab-indicator.register {
	left: 50%;
}

/* ============ 表单 ============ */
.auth-form {
	display: flex;
	flex-direction: column;
	gap: 14px;
}
.field {
	display: flex;
	flex-direction: column;
	gap: 6px;
}
.field label {
	font-size: 13px;
	color: #406070;
	font-weight: 500;
}
.field input {
	width: 100%;
	padding: 10px 12px;
	border: 1px solid #D0E0E4;
	border-radius: 8px;
	font-size: 14px;
	color: #1E3A40;
	outline: none;
	transition: all 0.2s;
	background: #F4F9FA;
	box-sizing: border-box;
}
.field input::placeholder {
	color: #80A0A8;
}
.field input:focus {
	border-color: #006080;
	background: #fff;
	box-shadow: 0 0 0 3px rgba(0, 96, 128, 0.12);
}
.err {
	font-size: 12px;
	color: #EF4444;
	margin-top: 2px;
}

.row-between {
	display: flex;
	justify-content: space-between;
	align-items: center;
	font-size: 13px;
	color: #507080;
}
.remember {
	display: flex;
	align-items: center;
	gap: 6px;
	cursor: pointer;
}
.remember input {
	accent-color: #006080;
}
.forget {
	color: #006080;
	cursor: pointer;
	transition: color 0.2s;
}
.forget:hover {
	text-decoration: underline;
}

/* ============ 身份选择 ============ */
.role-select {
	display: flex;
	flex-direction: column;
	gap: 6px;
}
.role-label {
	font-size: 13px;
	color: #406070;
	font-weight: 500;
}
.role-select-box {
	width: 100%;
}
.role-select-box :deep(.el-input__wrapper) {
	height: 40px;
	border-radius: 8px;
	box-shadow: 0 0 0 1px #D0E0E4 inset;
	background: #F4F9FA;
	padding: 0 12px;
}
.role-select-box :deep(.el-input__wrapper:hover) {
	box-shadow: 0 0 0 1px #B0C8D0 inset;
}
.role-select-box :deep(.el-input__wrapper.is-focus) {
	box-shadow: 0 0 0 1px #006080 inset;
	background: #fff;
}
.role-select-box :deep(.el-input__inner) {
	font-size: 14px;
	color: #1E3A40;
	height: 38px;
	line-height: 38px;
}
.role-select-box :deep(.el-select__placeholder) {
	color: #80A0A8;
	font-size: 14px;
}
.role-select-box :deep(.el-select__caret) {
	color: #80A0A8;
}

/* ============ 提交按钮 ============ */
.submit-btn {
	width: 100%;
	padding: 10px 18px;
	border: none;
	border-radius: 8px;
	background: linear-gradient(90deg, #006080, #0080A0);
	color: #fff;
	font-size: 15px;
	font-weight: 600;
	letter-spacing: 4px;
	cursor: pointer;
	transition: all 0.25s;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10px;
	position: relative;
	overflow: hidden;
	box-shadow: 0 4px 10px rgba(0, 96, 128, 0.25);
}
.submit-btn::before {
	content: '';
	position: absolute;
	inset: 0;
	background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.25), transparent);
	transform: translateX(-100%);
	transition: transform 0.5s;
}
.submit-btn:hover:not(:disabled)::before {
	transform: translateX(100%);
}
.submit-btn:hover:not(:disabled) {
	transform: translateY(-1px);
	box-shadow: 0 6px 14px rgba(0, 96, 128, 0.35);
}
.submit-btn:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}
.btn-arrow {
	transition: transform 0.25s;
	font-weight: 400;
}
.submit-btn:hover:not(:disabled) .btn-arrow {
	transform: translateX(4px);
}

/* ============ 全局提示 ============ */
.global-err,
.global-ok {
	font-size: 13px;
	padding: 9px 11px;
	border-radius: 6px;
	display: flex;
	align-items: center;
	gap: 8px;
	letter-spacing: 0.5px;
}
.global-err {
	color: #EF4444;
	background: #FEF2F2;
	border: 1px solid #FECACA;
}
.global-ok {
	color: #10B981;
	background: #ECFDF5;
	border: 1px solid #A7F3D0;
}
.msg-icon {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 16px;
	height: 16px;
	border-radius: 50%;
	font-size: 11px;
	font-weight: 700;
	flex-shrink: 0;
	color: #fff;
}
.global-err .msg-icon {
	background: #EF4444;
}
.global-ok .msg-icon {
	background: #10B981;
}

/* ============ 底部 ============ */
.auth-foot {
	margin-top: 18px;
	text-align: center;
	font-size: 13px;
	color: #507080;
}
.auth-foot a {
	color: #006080;
	cursor: pointer;
	margin-left: 4px;
	transition: color 0.2s;
}
.auth-foot a:hover {
	text-decoration: underline;
}

/* ============ 响应式 ============ */
@media (max-width: 960px) {
	.auth-page { flex-direction: column; }
	.brand-panel {
		flex: 0 0 auto;
		min-height: 180px;
		padding: 26px;
	}
	.brand-center { display: none; }
	.brand-bottom { display: none; }
	.deco-3 { display: none; }
	.form-panel { padding: 26px 20px; }
}
@media (max-width: 480px) {
	.brand-panel { min-height: 150px; padding: 22px; }
	.logo-text { font-size: 18px; }
	.brand-tag { font-size: 10px; }
	.form-card { max-width: 100%; padding: 22px 18px; }
	.form-title { font-size: 18px; }
	.field input { padding: 9px 10px; font-size: 13px; }
	.submit-btn { letter-spacing: 2px; padding: 10px 0; }
}
</style>
