import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(config => {
  // 优先读单独存的 token，fallback 读 security_auth blob
  let token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (!token) {
    const raw = localStorage.getItem('security_auth') || sessionStorage.getItem('security_auth')
    if (raw) {
      try { token = JSON.parse(raw).token } catch {}
    }
  }
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 响应拦截器
request.interceptors.response.use(
  response => {
    // 机器人接口不走 Result<T> 包装，直接返回；
    // 但 /robot-status/** 属于普通业务接口（返回 Result 包装），不能按裸接口处理
    const url = response.config.url || ''
    const isRawRobotApi = (url.startsWith('/robot') && !url.startsWith('/robot-status'))
      || url.startsWith('/photo')
    if (isRawRobotApi && !(response.data && response.data.code !== undefined)) {
      return response.data
    }

    const res = response.data
    // 后端 Result<T>：{ code, message, data }
    if (res && res.code !== undefined) {
      if (res.code === 200) {
        return res.data
      }
      // 业务错误
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    // 不是 Result 格式，直接返回
    return response.data
  },
  error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        localStorage.removeItem('security_auth')
        sessionStorage.removeItem('token')
        sessionStorage.removeItem('security_auth')
        if (router.currentRoute.value.path !== '/login') {
          ElMessage.error('登录已过期，请重新登录')
          router.push('/login')
        }
      } else if (status === 400) {
        ElMessage.error(data?.message || '请求参数错误')
      } else if (status === 403) {
        ElMessage.error('没有权限')
      } else if (status === 500) {
        ElMessage.error(data?.message || '服务器内部错误')
      } else {
        ElMessage.error(`请求失败 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error('网络异常，请检查后端服务')
    }
    return Promise.reject(error)
  }
)

export default request
