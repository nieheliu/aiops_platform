<template>
  <div class="alert-page">
    <el-card shadow="never" class="filter-card">
      <div class="page-head"><div><h1>告警管理</h1><p>查看告警事件、筛选高等级告警，并将告警转为运维工单。</p></div><el-button type="primary" :loading="loading" @click="loadData">刷新</el-button></div>
      <el-form :model="query" class="filter-form" @submit.prevent>
        <el-input v-model="query.keyword" placeholder="告警名称 / 实例 IP" clearable />
        <el-select v-model="query.severity" placeholder="告警等级" clearable><el-option label="提示" :value="0" /><el-option label="一般" :value="1" /><el-option label="严重" :value="2" /><el-option label="致命" :value="3" /></el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="pagedAlerts" stripe>
        <el-table-column prop="alertName" label="告警名称" min-width="180" />
        <el-table-column label="等级" width="100"><template #default="{ row }"><AlertSeverityTag :severity="row.severity" /></template></el-table-column>
        <el-table-column prop="instanceIp" label="实例 IP" width="150" />
        <el-table-column prop="triggerTime" label="触发时间" width="180" />
        <el-table-column label="工单状态" width="150"><template #default="{ row }"><TicketStatusTag v-if="ticketMap[row.id]" :status="ticketMap[row.id].status" /><el-tag v-else type="info" effect="light">未转工单</el-tag></template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="showDetail(row)">查看详情</el-button><el-button type="primary" link :loading="diagnosingId === row.id" @click="handleDiagnose(row)">诊断</el-button><el-button v-if="ticketMap[row.id]" type="success" link @click="router.push(`/tickets/${ticketMap[row.id].id}`)">查看工单</el-button><el-button v-else type="warning" link :loading="creatingId === row.id" @click="handleCreateTicket(row)">转工单</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" class="pagination" layout="total, prev, pager, next, sizes" :page-sizes="[10, 20, 50]" :total="filteredAlerts.length" />
    </el-card>

    <el-dialog v-model="detailVisible" title="告警详情" width="720px"><el-descriptions :column="2" border><el-descriptions-item label="告警名称">{{ currentAlert?.alertName }}</el-descriptions-item><el-descriptions-item label="等级"><AlertSeverityTag :severity="currentAlert?.severity" /></el-descriptions-item><el-descriptions-item label="实例 IP">{{ currentAlert?.instanceIp }}</el-descriptions-item><el-descriptions-item label="触发时间">{{ currentAlert?.triggerTime }}</el-descriptions-item></el-descriptions><h3>原始 Payload</h3><pre>{{ formatPayload(currentAlert?.rawPayload) }}</pre></el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTicketFromAlert, diagnoseAlert, getAlertList } from '../api/alert'
import { getTicketList } from '../api/ticket'
import AlertSeverityTag from '../components/AlertSeverityTag.vue'
import TicketStatusTag from '../components/TicketStatusTag.vue'

const router = useRouter(); const loading = ref(false); const alerts = ref([]); const tickets = ref([]); const creatingId = ref(null); const diagnosingId = ref(null); const detailVisible = ref(false); const currentAlert = ref(null); const query = reactive({ keyword: '', severity: '' }); const page = reactive({ current: 1, size: 10 })
const ticketMap = computed(() => Object.fromEntries(tickets.value.map((item) => [item.alertId, item])))
const filteredAlerts = computed(() => alerts.value.filter((item) => { const keyword = query.keyword.trim().toLowerCase(); const matchKeyword = !keyword || String(item.alertName || '').toLowerCase().includes(keyword) || String(item.instanceIp || '').toLowerCase().includes(keyword); const matchSeverity = query.severity === '' || query.severity === null || Number(item.severity) === Number(query.severity); return matchKeyword && matchSeverity }))
const pagedAlerts = computed(() => filteredAlerts.value.slice((page.current - 1) * page.size, page.current * page.size))
function normalizeList(data) { if (Array.isArray(data)) return data; return data?.records || data?.list || data?.data?.records || data?.data || [] }
async function loadData() { loading.value = true; try { const [alertData, ticketData] = await Promise.all([getAlertList(), getTicketList()]); alerts.value = normalizeList(alertData); tickets.value = normalizeList(ticketData) } finally { loading.value = false } }
function handleSearch() { page.current = 1 }
function handleReset() { query.keyword = ''; query.severity = ''; page.current = 1 }
function showDetail(row) { currentAlert.value = row; detailVisible.value = true }
function formatPayload(payload) { if (!payload) return '暂无'; try { return JSON.stringify(JSON.parse(payload), null, 2) } catch { return payload } }
async function handleCreateTicket(row) { creatingId.value = row.id; try { const ticket = await createTicketFromAlert(row.id); ElMessage.success('工单创建成功'); await loadData(); router.push(`/tickets/${ticket.id}`) } finally { creatingId.value = null } }
async function handleDiagnose(row) { diagnosingId.value = row.id; try { const report = await diagnoseAlert(row.id); ElMessage.success('大模型诊断报告已生成'); if (report?.id) router.push(`/diagnoses/${report.id}`) } finally { diagnosingId.value = null } }
onMounted(loadData)
</script>

<style scoped>
.alert-page { display: flex; flex-direction: column; gap: 20px; }.filter-card, .table-card { border: 0; border-radius: 18px; }.page-head { display: flex; align-items: center; justify-content: space-between; }h1 { margin: 0 0 8px; color: #111827; font-size: 26px; }p { margin: 0; color: #6b7280; }.filter-form { display: grid; grid-template-columns: 1fr 180px 88px 88px; gap: 12px; margin-top: 22px; }.pagination { justify-content: flex-end; margin-top: 18px; }pre { max-height: 320px; overflow: auto; padding: 14px; border-radius: 12px; background: #0f172a; color: #e5e7eb; white-space: pre-wrap; }
</style>
