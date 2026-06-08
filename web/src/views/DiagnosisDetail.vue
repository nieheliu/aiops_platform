<template>
  <div class="diagnosis-detail">
    <el-card shadow="never" class="detail-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="router.back()" />
        <div>
          <div class="breadcrumb">大模型诊断 / #{{ diagnosis?.id || route.params.id }}</div>
          <h1>大模型诊断详情</h1>
        </div>
      </div>
      <div class="header-actions">
        <el-tag v-if="diagnosis" :type="confidenceType(diagnosis.confidenceScore)" effect="light">
          置信度：{{ diagnosis.confidenceScore ?? '-' }}{{ diagnosis.confidenceScore !== null && diagnosis.confidenceScore !== undefined ? '%' : '' }}
        </el-tag>
        <el-button type="success" :loading="knowledgeLoading" @click="handleToKnowledge">生成知识库</el-button>
      </div>
    </el-card>

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">基础信息</div></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="诊断ID">#{{ diagnosis?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="模型名称">{{ diagnosis?.aiModel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关联告警">#{{ diagnosis?.alertId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关联工单">#{{ diagnosis?.ticketId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ diagnosis?.createTime || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="related-actions">
            <el-button v-if="diagnosis?.ticketId" type="primary" plain @click="router.push(`/tickets/${diagnosis.ticketId}`)">查看工单</el-button>
            <el-button type="primary" plain @click="router.push('/alerts')">查看告警</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">根因分析</div></template>
          <MarkdownViewer :content="diagnosis?.rootCauseAnalysis || ''" />
        </el-card>
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">修复建议</div></template>
          <MarkdownViewer :content="diagnosis?.suggestedFix || ''" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { diagnosisToKnowledge, getDiagnosisDetail } from '../api/diagnosis'
import MarkdownViewer from '../components/MarkdownViewer.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const knowledgeLoading = ref(false)
const diagnosis = ref(null)

function confidenceType(value) {
  const confidence = Number(value)
  if (Number.isNaN(confidence)) return 'info'
  if (confidence >= 80) return 'success'
  if (confidence >= 60) return 'primary'
  return 'warning'
}

async function loadDetail() {
  loading.value = true
  try {
    diagnosis.value = await getDiagnosisDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

async function handleToKnowledge() {
  if (!diagnosis.value?.id) return
  knowledgeLoading.value = true
  try {
    await diagnosisToKnowledge(diagnosis.value.id)
    ElMessage.success('已生成知识库草稿')
  } finally {
    knowledgeLoading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.diagnosis-detail { display: flex; flex-direction: column; gap: 20px; }
.detail-header, .panel-card { border: 0; border-radius: 18px; }
.detail-header :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; }
.header-left, .header-actions { display: flex; gap: 14px; align-items: center; }
.breadcrumb { margin-bottom: 6px; color: #6b7280; font-size: 13px; }
h1 { margin: 0; color: #111827; font-size: 24px; }
.card-title { font-weight: 700; color: #111827; }
.panel-card { margin-bottom: 20px; }
.related-actions { display: flex; gap: 10px; margin-top: 18px; }
</style>
