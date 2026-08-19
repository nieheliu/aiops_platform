import request, { DIAGNOSE_TIMEOUT } from '../utils/request'

export function getAlertList() {
  return request.get('/ops-alerts')
}

export function getAlertDetail(id) {
  return request.get(`/ops-alerts/${id}`)
}

export function createTicketFromAlert(id) {
  return request.post(`/ops-alerts/${id}/create-ticket`)
}

export function diagnoseAlert(id, modelId) {
  return request.post(`/ops-alerts/${id}/diagnose`, { modelId }, { timeout: DIAGNOSE_TIMEOUT })
}

export function getAlertDiagnoses(id) {
  return request.get(`/ops-alerts/${id}/diagnoses`)
}
