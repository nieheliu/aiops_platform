import request from '../utils/request'

export function getDashboardSummary() {
  return request.get('/dashboard/summary')
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
