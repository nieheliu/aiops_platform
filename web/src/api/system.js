import request from '../utils/request'

export function getUsers() {
  return request.get('/sys-users')
}

export function getEnabledUserOptions() {
  return request.get('/sys-users/enabled-options')
}

export function getUserPage(params) {
  return request.get('/sys-users/page', { params })
}

export function createUser(data) {
  return request.post('/sys-users', data)
}

export function updateUser(data) {
  return request.put('/sys-users', data)
}

export function deleteUser(id) {
  return request.delete(`/sys-users/${id}`)
}

export function enableUser(id) {
  return request.post(`/sys-users/${id}/enable`)
}

export function disableUser(id) {
  return request.post(`/sys-users/${id}/disable`)
}

export function resetUserPassword(id, password) {
  return request.post(`/sys-users/${id}/reset-password`, { password })
}

export function getUserRoles(id) {
  return request.get(`/sys-users/${id}/roles`)
}

export function assignUserRoles(id, roleIds) {
  return request.put(`/sys-users/${id}/roles`, { roleIds })
}

export function getRoles() {
  return request.get('/sys-roles')
}

export function getRolePage(params) {
  return request.get('/sys-roles/page', { params })
}

export function createRole(data) {
  return request.post('/sys-roles', data)
}

export function updateRole(data) {
  return request.put('/sys-roles', data)
}

export function deleteRole(id) {
  return request.delete(`/sys-roles/${id}`)
}
