import request from '../utils/request'

export function getAlertList() {
  return request.get('/ops-alerts')
}

export function getAlertDetail(id) {
  return request.get(`/ops-alerts/${id}`)
}

export function createTicketFromAlert(id) {
  return request.post(`/ops-alerts/${id}/create-ticket`)
}

export function diagnoseAlert(id) {
  return request.post(`/ops-alerts/${id}/diagnose`)
}

export function getAlertDiagnoses(id) {
  return request.get(`/ops-alerts/${id}/diagnoses`)
}
