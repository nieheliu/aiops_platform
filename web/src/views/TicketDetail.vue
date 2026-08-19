<template>
  <div class="detail-page">
    <el-card shadow="never" class="detail-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="router.back()" />
        <div><div class="breadcrumb">工单详情 / #{{ ticket?.id || route.params.id }}</div><h1>{{ ticket?.title || '工单详情' }}</h1></div>
      </div>
      <div class="header-actions">
        <TicketStatusTag v-if="ticket" :status="ticket.status" />
        <el-button v-if="canProcessTicket" type="primary" plain :loading="diagnosing" @click="handleDiagnose">大模型诊断</el-button>
        <el-button v-if="canProcessTicket && Number(ticket?.status) === 0" type="primary" :loading="actionLoading" @click="handleStart">开始处理</el-button>
        <el-button v-if="canProcessTicket && Number(ticket?.status) === 1" type="success" @click="resolveDialogVisible = true">标记已解决</el-button>
        <el-button v-if="canProcessTicket && Number(ticket?.status) === 2" type="warning" :loading="actionLoading" @click="handleClose">关闭工单</el-button>
      </div>
    </el-card>

    <el-alert
      v-if="ticket && !canProcessTicket && canManageTicket"
      type="info"
      :closable="false"
      show-icon
      class="handler-tip"
      :title="`该工单由 ${formatHandler(ticket.handlerUserId)} 负责处理，您仅可查看`"
    />

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">AI 诊断报告</div></template>
          <div v-if="diagnosis" class="diagnosis-meta"><el-tag type="primary" effect="light">模型：{{ getModelDisplayName(diagnosis.aiModel) }}</el-tag><el-tag v-if="diagnosis.confidenceScore !== undefined && diagnosis.confidenceScore !== null" type="success" effect="light">置信度：{{ diagnosis.confidenceScore }}%</el-tag><span>生成时间：{{ diagnosis.createTime || '-' }}</span></div>
          <MarkdownViewer :content="diagnosisMarkdown" />
        </el-card>
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">诊断历史</div></template>
          <el-table v-if="diagnoses.length" :data="diagnoses" size="small">
            <el-table-column prop="id" label="报告ID" width="100"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
            <el-table-column prop="aiModel" label="模型" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ getModelDisplayName(row.aiModel) }}</template>
            </el-table-column>
            <el-table-column prop="confidenceScore" label="置信度" width="100"><template #default="{ row }">{{ row.confidenceScore ?? '-' }}{{ row.confidenceScore !== null && row.confidenceScore !== undefined ? '%' : '' }}</template></el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" />
            <el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="primary" link @click="router.push(`/diagnoses/${row.id}`)">完整报告</el-button></template></el-table-column>
          </el-table>
          <el-empty v-else description="暂无诊断历史，可点击右上角大模型诊断生成报告" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card v-loading="loading" shadow="never" class="panel-card info-card">
          <template #header><div class="card-title">基础信息</div></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="工单编号">#{{ ticket?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关联告警">#{{ ticket?.alertId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理人">
              <div class="handler-row">
                <span>{{ formatHandler(ticket?.handlerUserId) }}</span>
                <el-select
                  v-if="canAssignHandler && Number(ticket?.status) !== 3"
                  v-model="assignHandlerId"
                  placeholder="选择处理人"
                  filterable
                  clearable
                  size="small"
                  class="handler-select"
                  @change="handleAssign"
                >
                  <el-option
                    v-for="user in userOptions"
                    :key="user.id"
                    :label="user.username"
                    :value="user.id"
                  />
                </el-select>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ ticket?.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ ticket?.updateTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="解决时间">{{ ticket?.resolveTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-card shadow="never" class="panel-card"><template #header><div class="card-title">工单描述</div></template><p class="description-text">{{ ticket?.description || '暂无工单描述' }}</p></el-card>
        <el-card shadow="never" class="panel-card"><template #header><div class="card-title">处理记录</div></template><el-timeline v-if="ticketLogs.length"><el-timeline-item v-for="log in ticketLogs" :key="log.id" :timestamp="log.operateTime" type="primary"><strong>{{ log.action }}</strong><div class="log-remark">{{ log.remark || '无备注' }}</div></el-timeline-item></el-timeline><el-empty v-else description="暂无处理记录" /></el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="resolveDialogVisible" title="标记工单已解决" width="560px"><el-form :model="resolveForm" label-position="top"><el-form-item label="经验总结"><el-input v-model="resolveForm.experienceSummary" type="textarea" :rows="6" maxlength="1000" show-word-limit placeholder="请输入本次故障处理经验、解决步骤和复盘结论，提交后会同步到 MySQL 知识库和 ES 经验库。" /></el-form-item></el-form><template #footer><el-button @click="resolveDialogVisible = false">取消</el-button><el-button type="success" :loading="actionLoading" @click="handleResolve">确认解决并同步经验库</el-button></template></el-dialog>

    <ModelSelectDialog
      ref="modelDialogRef"
      v-model="modelDialogVisible"
      :ticket-id="route.params.id"
      :alert-id="ticket?.alertId"
      @confirm="handleDiagnoseConfirm"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getEnabledUserOptions } from '../api/system'
import { assignTicketHandler, closeTicket, diagnoseTicket, getTicketDetail, getTicketDiagnoses, getTicketLogs, resolveTicket, startTicket } from '../api/ticket'
import MarkdownViewer from '../components/MarkdownViewer.vue'
import TicketStatusTag from '../components/TicketStatusTag.vue'
import ModelSelectDialog from '../components/ModelSelectDialog.vue'
import { getModelDisplayName } from '../utils/model'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const actionLoading = ref(false)
const diagnosing = ref(false)
const resolveDialogVisible = ref(false)
const modelDialogVisible = ref(false)
const modelDialogRef = ref(null)
const ticket = ref(null)
const diagnosis = ref(null)
const diagnoses = ref([])
const logs = ref([])
const userOptions = ref([])
const assignHandlerId = ref(null)
const resolveForm = reactive({ experienceSummary: '' })
const canAssignHandler = computed(() => authStore.roles.includes('ADMIN'))
const canManageTicket = computed(() => authStore.permissions.includes('ticket:manage'))
const currentUserId = computed(() => authStore.user?.id)
const canProcessTicket = computed(() => {
  if (!ticket.value || !canManageTicket.value) return false
  if (authStore.roles.includes('ADMIN')) return true
  const handlerId = ticket.value.handlerUserId
  const status = Number(ticket.value.status)
  if (status === 0) {
    return !handlerId || String(handlerId) === String(currentUserId.value)
  }
  return handlerId && String(handlerId) === String(currentUserId.value)
})
const ticketLogs = computed(() => logs.value.filter((item) => String(item.ticketId) === String(route.params.id)))
const diagnosisMarkdown = computed(() => diagnosis.value ? `## 根因分析\n\n${diagnosis.value.rootCauseAnalysis || '暂无根因分析。'}\n\n## 修复建议\n\n${diagnosis.value.suggestedFix || '暂无修复建议。'}` : '')
function normalizeList(data) { if (Array.isArray(data)) return data; return data?.records || data?.list || data?.data?.records || data?.data || [] }

function formatHandler(handlerUserId) {
  if (!handlerUserId) return '未分配'
  const matched = userOptions.value.find((item) => String(item.id) === String(handlerUserId))
  return matched?.username || `用户 ${handlerUserId}`
}

async function loadUserOptions() {
  userOptions.value = normalizeList(await getEnabledUserOptions())
}

async function loadDetail() {
  const id = route.params.id
  if (!id || id === 'null' || id === 'undefined') {
    ElMessage.warning('工单不存在或缺少关联工单')
    router.replace('/tickets')
    return
  }
  loading.value = true
  try {
    const [ticketData, diagnosisData, logData] = await Promise.all([getTicketDetail(id), getTicketDiagnoses(id), getTicketLogs()])
    ticket.value = ticketData
    assignHandlerId.value = ticketData?.handlerUserId || null
    diagnoses.value = normalizeList(diagnosisData)
    diagnosis.value = diagnoses.value[0] || null
    logs.value = normalizeList(logData)
  } finally {
    loading.value = false
  }
}
async function handleStart() {
  actionLoading.value = true
  try {
    await startTicket(route.params.id)
    ElMessage.success('工单已进入处理中，处理人已设为当前登录用户')
    await loadDetail()
  } finally {
    actionLoading.value = false
  }
}

async function handleAssign(handlerUserId) {
  if (!handlerUserId) return
  actionLoading.value = true
  try {
    await assignTicketHandler(route.params.id, handlerUserId)
    ElMessage.success('处理人已更新')
    await loadDetail()
  } catch (error) {
    assignHandlerId.value = ticket.value?.handlerUserId || null
  } finally {
    actionLoading.value = false
  }
}
async function handleResolve() { if (!resolveForm.experienceSummary.trim()) { ElMessage.warning('请填写经验总结'); return } actionLoading.value = true; try { await resolveTicket(route.params.id, { experienceSummary: resolveForm.experienceSummary }); ElMessage.success('工单已解决，并已同步知识库'); resolveDialogVisible.value = false; await loadDetail() } finally { actionLoading.value = false } }
async function handleClose() { await ElMessageBox.confirm('确认关闭该工单吗？关闭后将不再允许状态流转。', '关闭工单', { type: 'warning' }); actionLoading.value = true; try { await closeTicket(route.params.id); ElMessage.success('工单已关闭'); await loadDetail() } finally { actionLoading.value = false } }
function handleDiagnose() {
  modelDialogVisible.value = true
}

async function handleDiagnoseConfirm(modelId) {
  diagnosing.value = true
  modelDialogRef.value?.setSubmitting(true)
  try {
    const report = await diagnoseTicket(route.params.id, modelId)
    ElMessage.success('大模型诊断报告已生成')
    modelDialogRef.value?.close()
    await loadDetail()
    if (report?.id) router.push(`/diagnoses/${report.id}`)
  } catch (error) {
    const isTimeout = error?.code === 'ECONNABORTED' || /timeout/i.test(error?.message || '')
    if (isTimeout) {
      await loadDetail()
      if (diagnoses.value.length) {
        ElMessage.warning('诊断耗时较长，已为你刷新列表，请查看是否已生成报告')
        modelDialogRef.value?.close()
      }
    }
  } finally {
    diagnosing.value = false
    modelDialogRef.value?.setSubmitting(false)
  }
}
onMounted(async () => {
  await loadUserOptions()
  await loadDetail()
})
</script>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 20px; }
.detail-header, .panel-card { border: 0; border-radius: 18px; }
.detail-header :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; }
.header-left, .header-actions { display: flex; gap: 14px; align-items: center; }
.breadcrumb { margin-bottom: 6px; color: #6b7280; font-size: 13px; }
h1 { margin: 0; color: #111827; font-size: 24px; }
.card-title { font-weight: 700; color: #111827; }
.diagnosis-meta { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 18px; color: #6b7280; font-size: 13px; }
.info-card { margin-bottom: 20px; }
.handler-tip { border-radius: 14px; }
.handler-row { display: flex; flex-direction: column; gap: 8px; }
.handler-select { width: 100%; }
.description-text { margin: 0; color: #4b5563; line-height: 1.8; white-space: pre-wrap; }
.log-remark { margin-top: 4px; color: #6b7280; }
</style>
