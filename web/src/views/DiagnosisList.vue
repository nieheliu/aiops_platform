<template>

  <div class="diagnosis-page">

    <el-card shadow="never" class="filter-card">

      <div class="page-head">

        <div>

          <h1>大模型诊断</h1>

          <p>按告警/工单分组查看多模型诊断结果，同一模型不会重复诊断。</p>

        </div>

        <div class="head-actions">

          <el-button @click="toggleAllGroups">{{ allExpanded ? '全部折叠' : '全部展开' }}</el-button>

          <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>

        </div>

      </div>



      <el-form :model="query" class="filter-form" @submit.prevent>

        <el-input v-model="query.keyword" placeholder="根因 / 修复建议 / 告警ID / 工单ID" clearable @keyup.enter="handleSearch" />

        <el-input v-model="query.model" placeholder="模型 ID / 名称" clearable @keyup.enter="handleSearch" />

        <el-button type="primary" @click="handleSearch">查询</el-button>

        <el-button @click="handleReset">重置</el-button>

      </el-form>

    </el-card>



    <div v-loading="loading" class="group-list">

      <el-empty v-if="!groupedDiagnoses.length" description="暂无诊断报告" />



      <el-collapse v-else v-model="activeGroups" class="group-collapse">

        <el-collapse-item v-for="group in groupedDiagnoses" :key="group.key" :name="group.key">

          <template #title>

            <div class="collapse-title">

              <div>

                <div class="group-title">{{ group.title }}</div>

                <div class="group-meta">

                  <el-tag size="small" type="info">告警 #{{ group.alertId || '-' }}</el-tag>

                  <el-tag v-if="group.ticketId" size="small">工单 #{{ group.ticketId }}</el-tag>

                  <span>{{ group.items.length }} 份模型报告</span>

                </div>

              </div>

              <el-button v-if="group.ticketId" type="primary" plain size="small" @click.stop="router.push(`/tickets/${group.ticketId}`)">查看工单</el-button>

            </div>

          </template>



          <div class="report-list">

            <div v-for="item in group.items" :key="item.id" class="report-item">

              <div class="report-head">

                <div class="report-model">

                  <el-tag type="primary" effect="dark">{{ getModelDisplayName(item.aiModel) }}</el-tag>

                  <span class="report-id">报告 #{{ item.id }}</span>

                </div>

                <el-tag :type="confidenceType(item.confidenceScore)" effect="light">

                  置信度 {{ item.confidenceScore ?? '-' }}{{ item.confidenceScore !== null && item.confidenceScore !== undefined ? '%' : '' }}

                </el-tag>

              </div>

              <div class="report-time">创建时间：{{ item.createTime || '-' }}</div>

              <div class="report-summary">{{ summarize(item.rootCauseAnalysis) || summarize(item.suggestedFix) || '暂无摘要' }}</div>

              <div class="report-actions">

                <el-button type="primary" link @click="router.push(`/diagnoses/${item.id}`)">查看详情</el-button>

                <el-button type="success" link :loading="knowledgeLoadingId === item.id" @click="handleToKnowledge(item)">同步到知识库</el-button>

                <el-button type="danger" link :loading="deletingId === item.id" @click="handleDelete(item)">删除报告</el-button>

              </div>

            </div>

          </div>

        </el-collapse-item>

      </el-collapse>

    </div>

  </div>

</template>



<script setup>

import { computed, onMounted, reactive, ref, watch } from 'vue'

import { useRouter } from 'vue-router'

import { ElMessage, ElMessageBox } from 'element-plus'

import { getAlertList } from '../api/alert'

import { deleteDiagnosis, diagnosisToKnowledge, getDiagnosisList } from '../api/diagnosis'

import { getTicketList } from '../api/ticket'

import { getModelDisplayName } from '../utils/model'



const router = useRouter()

const loading = ref(false)

const knowledgeLoadingId = ref(null)

const deletingId = ref(null)

const diagnoses = ref([])

const alerts = ref([])

const tickets = ref([])

const activeGroups = ref([])

const query = reactive({ keyword: '', model: '' })



const alertMap = computed(() => Object.fromEntries(alerts.value.map((item) => [item.id, item])))

const ticketMap = computed(() => Object.fromEntries(tickets.value.map((item) => [item.id, item])))



const filteredDiagnoses = computed(() => {

  const keyword = query.keyword.trim().toLowerCase()

  const model = query.model.trim().toLowerCase()

  return diagnoses.value.filter((item) => {

    const matchKeyword = !keyword || [item.id, item.alertId, item.ticketId, item.rootCauseAnalysis, item.suggestedFix, item.aiModel, getModelDisplayName(item.aiModel)]

      .filter((value) => value !== undefined && value !== null)

      .some((value) => String(value).toLowerCase().includes(keyword))

    const matchModel = !model || String(item.aiModel || '').toLowerCase().includes(model)

      || getModelDisplayName(item.aiModel).toLowerCase().includes(model)

    return matchKeyword && matchModel

  })

})



const groupedDiagnoses = computed(() => {

  const map = new Map()

  for (const item of filteredDiagnoses.value) {

    const key = `${item.ticketId || 'none'}-${item.alertId || 'none'}`

    if (!map.has(key)) {

      const ticket = item.ticketId ? ticketMap.value[item.ticketId] : null

      const alert = item.alertId ? alertMap.value[item.alertId] : null

      const alertLabel = alert?.alertName || (item.alertId ? `告警 #${item.alertId}` : '未关联告警')

      const ticketLabel = ticket?.title || (item.ticketId ? `工单 #${item.ticketId}` : '')

      map.set(key, {

        key,

        ticketId: item.ticketId,

        alertId: item.alertId,

        title: item.ticketId ? `${ticketLabel}` : `${alertLabel} 诊断组`,

        subtitle: item.ticketId ? alertLabel : '',

        items: [],

      })

    }

    map.get(key).items.push(item)

  }

  return Array.from(map.values()).map((group) => ({

    ...group,

    items: group.items.sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || ''))),

  }))

})



const allExpanded = computed(() => {

  return groupedDiagnoses.value.length > 0

    && activeGroups.value.length === groupedDiagnoses.value.length

})



watch(groupedDiagnoses, (groups) => {

  if (!activeGroups.value.length && groups.length) {

    activeGroups.value = [groups[0].key]

  }

}, { immediate: true })



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



function summarize(text) {

  if (!text) return ''

  const plain = String(text).replace(/[#*`>-]/g, ' ').replace(/\s+/g, ' ').trim()

  return plain.length > 120 ? `${plain.slice(0, 120)}...` : plain

}



function toggleAllGroups() {

  activeGroups.value = allExpanded.value ? [] : groupedDiagnoses.value.map((group) => group.key)

}



function syncActiveGroups(preserveCollapse) {

  const validKeys = new Set(groupedDiagnoses.value.map((group) => group.key))

  if (preserveCollapse) {

    activeGroups.value = activeGroups.value.filter((key) => validKeys.has(key))

    return

  }

  if (!activeGroups.value.length && groupedDiagnoses.value.length) {

    activeGroups.value = [groupedDiagnoses.value[0].key]

  }

}



async function loadData(preserveCollapse = false) {

  loading.value = true

  try {

    const [diagnosisData, alertData, ticketData] = await Promise.all([

      getDiagnosisList(),

      getAlertList(),

      getTicketList(),

    ])

    diagnoses.value = normalizeList(diagnosisData)

    alerts.value = normalizeList(alertData)

    tickets.value = normalizeList(ticketData)

    syncActiveGroups(preserveCollapse)

  } finally {

    loading.value = false

  }

}



function handleSearch() {}



function handleReset() {

  query.keyword = ''

  query.model = ''

}



async function handleToKnowledge(row) {

  knowledgeLoadingId.value = row.id

  try {

    await diagnosisToKnowledge(row.id)

    ElMessage.success('已同步到知识库')

  } finally {

    knowledgeLoadingId.value = null

  }

}



async function handleDelete(row) {

  try {

    await ElMessageBox.confirm(`确认删除报告 #${row.id}（${getModelDisplayName(row.aiModel)}）吗？删除后不可恢复。`, '删除诊断报告', {

      type: 'warning',

      confirmButtonText: '确认删除',

      cancelButtonText: '取消',

    })

  } catch {

    return

  }



  deletingId.value = row.id

  try {

    await deleteDiagnosis(row.id)

    ElMessage.success('诊断报告已删除')

    await loadData(true)

  } finally {

    deletingId.value = null

  }

}



onMounted(loadData)

</script>



<style scoped>

.diagnosis-page { display: flex; flex-direction: column; gap: 20px; }

.filter-card, .group-collapse { border: 0; border-radius: 18px; }

.page-head, .head-actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }

h1 { margin: 0 0 8px; color: #111827; font-size: 26px; }

p { margin: 0; color: #6b7280; }

.filter-form { display: grid; grid-template-columns: 1.4fr 1fr 88px 88px; gap: 12px; margin-top: 22px; }

.group-list { display: flex; flex-direction: column; gap: 16px; min-height: 200px; }

.group-collapse { background: transparent; border: 0; }

.group-collapse :deep(.el-collapse-item) { margin-bottom: 16px; border: 0; border-radius: 18px; overflow: hidden; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06); }

.group-collapse :deep(.el-collapse-item__header) { height: auto; line-height: 1.5; padding: 18px 20px; border-bottom: 1px solid #f3f4f6; }

.group-collapse :deep(.el-collapse-item__wrap), .group-collapse :deep(.el-collapse-item__content) { border: 0; }

.group-collapse :deep(.el-collapse-item__content) { padding: 0 20px 20px; }

.collapse-title { display: flex; align-items: center; justify-content: space-between; gap: 16px; width: 100%; padding-right: 12px; }

.group-title { color: #111827; font-size: 18px; font-weight: 800; }

.group-meta { display: flex; gap: 8px; align-items: center; margin-top: 8px; color: #6b7280; font-size: 13px; }

.report-list { display: flex; flex-direction: column; gap: 14px; }

.report-item { padding: 16px; border: 1px solid #e5e7eb; border-radius: 14px; background: #fafafa; }

.report-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }

.report-model { display: flex; gap: 10px; align-items: center; }

.report-id { color: #6b7280; font-size: 13px; }

.report-time { margin-top: 8px; color: #9ca3af; font-size: 12px; }

.report-summary { margin-top: 10px; color: #4b5563; line-height: 1.7; }

.report-actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }

</style>

