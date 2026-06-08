import request from '../utils/request'

export function getTicketPage(params) {
  return request.get('/ops-tickets/page', { params })
}

export function getTicketList() {
  return request.get('/ops-tickets')
}

export function getTicketDetail(id) {
  return request.get(`/ops-tickets/${id}`)
}

export function getDiagnosisList() {
  return request.get('/ai-diagnoses')
}

export function getTicketLogs() {
  return request.get('/ops-ticket-logs')
}

export function startTicket(id) {
  return request.post(`/ops-tickets/${id}/start`)
}

export function resolveTicket(id, data) {
  return request.post(`/ops-tickets/${id}/resolve`, data)
}

export function closeTicket(id) {
  return request.post(`/ops-tickets/${id}/close`)
}

export function diagnoseTicket(id) {
  return request.post(`/ops-tickets/${id}/diagnose`)
}

export function getTicketDiagnoses(id) {
  return request.get(`/ops-tickets/${id}/diagnoses`)
}
