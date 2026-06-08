import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useAuthStore } from '../stores/auth'

const request = axios.create({
  baseURL: '',
  timeout: 15000,
})

request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.response?.data?.msg || error.message || '请求失败'

    if (status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      router.replace('/login')
      ElMessage.error('登录状态已过期，请重新登录')
    } else {
      ElMessage.error(message)
    }

    return Promise.reject(error)
  },
)

export default request
