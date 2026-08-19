import request, { DIAGNOSE_TIMEOUT } from '../utils/request'

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

export function assignTicketHandler(id, handlerUserId) {
  return request.put(`/ops-tickets/${id}/assign`, { handlerUserId })
}

export function resolveTicket(id, data) {
  return request.post(`/ops-tickets/${id}/resolve`, data)
}

export function closeTicket(id) {
  return request.post(`/ops-tickets/${id}/close`)
}

export function diagnoseTicket(id, modelId) {
  return request.post(`/ops-tickets/${id}/diagnose`, { modelId }, { timeout: DIAGNOSE_TIMEOUT })
}

export function getTicketDiagnoses(id) {
  return request.get(`/ops-tickets/${id}/diagnoses`)
}
