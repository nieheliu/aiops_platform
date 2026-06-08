import request from '../utils/request'

export function searchTicketKnowledge(params) {
  return request.get('/ticket-knowledge/search', { params })
}
