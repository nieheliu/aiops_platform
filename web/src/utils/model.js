const MODEL_NAME_MAP = {
  'deepseek-v4-flash-free': 'DeepSeek V4 Flash 免费版',
  'qwen-plus-2025-07-28': '通义千问 Plus',
  'qwen3.6-plus': '通义千问 3.6 Plus',
  'MiniMax-M2.5': 'MiniMax M2.5',
}

export function getModelDisplayName(modelId) {
  if (!modelId) return '未知模型'
  return MODEL_NAME_MAP[modelId] || modelId
}

export function getSourceTypeLabel(sourceType) {
  if (sourceType === 'ticket_resolve') return '工单沉淀'
  if (sourceType === 'diagnosis_import') return '诊断导入'
  return '知识库'
}
