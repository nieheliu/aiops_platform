import request from '../utils/request'

export function getDashboardSummary(refresh = false) {
  return request.get('/dashboard/summary', { params: { refresh } })
}

export function getAlerts() {
  return request.get('/ops-alerts')
}

export function getTickets() {
  return request.get('/ops-tickets')
}

export function getDiagnoses() {
  return request.get('/ai-diagnoses')
}

export function getKnowledge() {
  return request.get('/ops-knowledge')
}
