<template>
  <div class="diagnosis-page">
    <el-card shadow="never" class="filter-card">
      <div class="page-head">
        <div>
          <h1>大模型诊断</h1>
          <p>查看告警和工单触发的大模型诊断报告，支持按关键词、模型和置信度筛选。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
      </div>

      <el-form :model="query" class="filter-form" @submit.prevent>
        <el-input v-model="query.keyword" placeholder="根因 / 修复建议 / 告警ID / 工单ID" clearable @keyup.enter="handleSearch" />
        <el-input v-model="query.model" placeholder="模型名称" clearable @keyup.enter="handleSearch" />
        <el-input-number v-model="query.minConfidence" :min="0" :max="100" placeholder="最低置信度" controls-position="right" />
        <el-input-number v-model="query.maxConfidence" :min="0" :max="100" placeholder="最高置信度" controls-position="right" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="pagedDiagnoses" stripe>
        <el-table-column prop="id" label="报告ID" width="100">
          <template #default="{ row }">#{{ row.id }}</template>
        </el-table-column>
        <el-table-column prop="alertId" label="关联告警" width="120">
          <template #default="{ row }">#{{ row.alertId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ticketId" label="关联工单" width="120">
          <template #default="{ row }">
            <el-button v-if="row.ticketId" type="primary" link @click="router.push(`/tickets/${row.ticketId}`)">#{{ row.ticketId }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="aiModel" label="模型名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="置信度" width="120">
          <template #default="{ row }">
            <el-tag :type="confidenceType(row.confidenceScore)" effect="light">
              {{ row.confidenceScore ?? '-' }}{{ row.confidenceScore !== null && row.confidenceScore !== undefined ? '%' : '' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="摘要" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.rootCauseAnalysis || row.suggestedFix || '暂无摘要' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/diagnoses/${row.id}`)">查看详情</el-button>
            <el-button type="success" link :loading="knowledgeLoadingId === row.id" @click="handleToKnowledge(row)">生成知识库</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        class="pagination"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50]"
        :total="filteredDiagnoses.length"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { diagnosisToKnowledge, getDiagnosisList } from '../api/diagnosis'

const router = useRouter()
const loading = ref(false)
const knowledgeLoadingId = ref(null)
const diagnoses = ref([])
const query = reactive({
  keyword: '',
  model: '',
  minConfidence: undefined,
  maxConfidence: undefined,
})
const page = reactive({ current: 1, size: 10 })

const filteredDiagnoses = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  const model = query.model.trim().toLowerCase()
  return diagnoses.value.filter((item) => {
    const confidence = Number(item.confidenceScore)
    const matchKeyword = !keyword || [item.id, item.alertId, item.ticketId, item.rootCauseAnalysis, item.suggestedFix]
      .filter((value) => value !== undefined && value !== null)
      .some((value) => String(value).toLowerCase().includes(keyword))
    const matchModel = !model || String(item.aiModel || '').toLowerCase().includes(model)
    const matchMin = query.minConfidence === undefined || query.minConfidence === null || (!Number.isNaN(confidence) && confidence >= query.minConfidence)
    const matchMax = query.maxConfidence === undefined || query.maxConfidence === null || (!Number.isNaN(confidence) && confidence <= query.maxConfidence)
    return matchKeyword && matchModel && matchMin && matchMax
  })
})

const pagedDiagnoses = computed(() => {
  const start = (page.current - 1) * page.size
  return filteredDiagnoses.value.slice(start, start + page.size)
})

function normalizeList(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || data?.data?.records || data?.data || []
}

function confidenceType(value) {
  const confidence = Number(value)
  if (Number.isNaN(confidence)) return 'info'
  if (confidence >= 80) return 'success'
  if (confidence >= 60) return 'primary'
  return 'warning'
}

async function loadData() {
  loading.value = true
  try {
    diagnoses.value = normalizeList(await getDiagnosisList())
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.current = 1
}

function handleReset() {
  query.keyword = ''
  query.model = ''
  query.minConfidence = undefined
  query.maxConfidence = undefined
  page.current = 1
}

async function handleToKnowledge(row) {
  knowledgeLoadingId.value = row.id
  try {
    await diagnosisToKnowledge(row.id)
    ElMessage.success('已生成知识库草稿')
  } finally {
    knowledgeLoadingId.value = null
  }
}

onMounted(loadData)
</script>

<style scoped>
.diagnosis-page { display: flex; flex-direction: column; gap: 20px; }
.filter-card, .table-card { border: 0; border-radius: 18px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; }
h1 { margin: 0 0 8px; color: #111827; font-size: 26px; }
p { margin: 0; color: #6b7280; }
.filter-form { display: grid; grid-template-columns: 1.4fr 1fr 150px 150px 88px 88px; gap: 12px; margin-top: 22px; }
.pagination { justify-content: flex-end; margin-top: 18px; }
</style>
