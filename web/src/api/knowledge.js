import request from '../utils/request'



export function searchTicketKnowledge(params) {

  return request.get('/ticket-knowledge/search', { params })

}



export function getKnowledgeFacets(params) {

  return request.get('/ticket-knowledge/facets', { params })

}



export function deleteTicketKnowledge(documentId) {

  return request.delete(`/ticket-knowledge/${encodeURIComponent(documentId)}`)

}



export function createKnowledgeArticle(data) {

  return request.post('/knowledge-articles', data)

}



export function updateKnowledgeArticle(id, data) {

  return request.put(`/knowledge-articles/${id}`, data)

}



export function getKnowledgeArticle(id) {

  return request.get(`/knowledge-articles/${id}`)

}



export function getKnowledgeWorkflowList(params) {

  return request.get('/knowledge-articles/workflow-list', { params })

}



export function getKnowledgeAuditLogs(id) {

  return request.get(`/knowledge-articles/${id}/audit-logs`)

}



export function submitKnowledgeArticle(id) {

  return request.post(`/knowledge-articles/${id}/submit`)

}



export function publishKnowledgeArticle(id) {

  return request.post(`/knowledge-articles/${id}/publish`)

}



export function archiveKnowledgeArticle(id) {

  return request.post(`/knowledge-articles/${id}/archive`)

}



export function deprecateKnowledgeArticle(id) {

  return request.post(`/knowledge-articles/${id}/deprecate`)

}



export function deleteKnowledgeArticle(id) {

  return request.post(`/knowledge-articles/${id}/delete`)

}


