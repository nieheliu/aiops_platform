import { defineStore } from 'pinia'
import { getCurrentUser, loginApi } from '../api/auth'

const TOKEN_KEY = 'aiops_token'
const USER_KEY = 'aiops_user'

function parseUserFromResponse(response, username) {
  return response.user || response.data?.user || {
    username: response.username || response.data?.username || username,
    realName: response.realName || response.data?.realName || username,
  }
}

function parseTokenFromResponse(response) {
  return response.token || response.accessToken || response.data?.token || response.data?.accessToken
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
  }),
  getters: {
    isLogin: (state) => Boolean(state.token),
    displayName: (state) => state.user?.realName || state.user?.username || '管理员',
    roles: (state) => state.user?.roles || [],
    permissions: (state) => state.user?.permissions || [],
  },
  actions: {
    async login(form) {
      const response = await loginApi(form)
      const token = parseTokenFromResponse(response)

      if (!token) {
        throw new Error('登录成功但未获取到 token，请检查后端返回字段')
      }

      this.token = token
      this.user = parseUserFromResponse(response, form.username)
      localStorage.setItem(TOKEN_KEY, token)
      await this.loadCurrentUser()
      return response
    },
    async loadCurrentUser() {
      if (!this.token) return null
      const currentUser = await getCurrentUser()
      this.user = { ...(this.user || {}), ...currentUser }
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return currentUser
    },
    hasRole(role) {
      return this.roles.includes(role)
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
