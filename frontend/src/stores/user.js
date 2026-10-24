import { defineStore } from 'pinia'
import { login as loginApi } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    isLoggedIn: state => !!state.token,
    userName: state => state.userInfo?.userName || '',
    role: state => state.userInfo?.role || '',
    userId: state => state.userInfo?.userId,
    communityId: state => state.userInfo?.communityId
  },
  actions: {
    async login(payload) {
      const data = await loginApi(payload)
      // data = { token, tokenType, expiresIn }
      this.token = data.token
      // 后端 login 没返回用户信息，这里只存 token；后续可加 /auth/profile 接口
      this.userInfo = { userName: payload.userName }
      localStorage.setItem('token', data.token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
      return data
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
