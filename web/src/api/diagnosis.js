import request from '../utils/request'

export function getDiagnosisList() {
  return request.get('/ai-diagnoses')
}

export function getDiagnosisDetail(id) {
  return request.get(`/ai-diagnoses/${id}`)
}

export function diagnosisToKnowledge(id) {
  return request.post(`/ai-diagnoses/${id}/to-knowledge`)
}
