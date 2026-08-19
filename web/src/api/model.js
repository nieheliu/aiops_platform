import request from '../utils/request'

export function getAllModels() {
  return request.get('/ai-models')
}

export function getAvailableModels(params) {
  return request.get('/ai-models/available', { params })
}
