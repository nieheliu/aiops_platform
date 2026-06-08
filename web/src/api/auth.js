import request from '../utils/request'

const LOGIN_URL = import.meta.env.VITE_LOGIN_URL || '/auth/login'

export function loginApi(data) {
  return request.post(LOGIN_URL, data)
}

export function getCurrentUser() {
  return request.get('/auth/me')
}
