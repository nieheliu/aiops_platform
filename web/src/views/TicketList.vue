<template>
  <div class="ticket-page">
    <el-card shadow="never" class="filter-card">
      <div class="page-head">
        <div>
          <h1 class="page-title">工单管理</h1>
          <p class="page-desc">查看工单状态、处理进度，并进入详情查看 AI 诊断报告。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="loadTickets">刷新列表</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="搜索标题 / 描述 / 编号" clearable style="width: 240px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="待处理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已解决" :value="2" />
            <el-option label="已关闭" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="pagedTickets" row-key="id">
        <el-table-column prop="id" label="编号" width="90" />
        <el-table-column prop="title" label="工单标题" min-width="220">
          <template #default="{ row }">
            <div class="ticket-title">{{ row.title || '-' }}</div>
            <div class="ticket-desc">{{ row.description || '暂无描述' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <TicketStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="alertId" label="关联告警" width="120">
          <template #default="{ row }">#{{ row.alertId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="handlerUserId" label="处理人" width="120">
          <template #default="{ row }">{{ row.handlerUserId ? `用户 ${row.handlerUserId}` : '未分配' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/tickets/${row.id}`)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="filteredTickets.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getTicketList, getTicketPage } from '../api/ticket'
import TicketStatusTag from '../components/TicketStatusTag.vue'

const router = useRouter()
const loading = ref(false)
const tickets = ref([])
const query = reactive({
  keyword: '',
  status: '',
})
const pagination = reactive({
  current: 1,
  size: 10,
})

const filteredTickets = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return tickets.value.filter((item) => {
    const matchStatus = query.status === '' || item.status === query.status
    const matchKeyword = !keyword || [item.id, item.title, item.description, item.alertId]
      .filter((value) => value !== undefined && value !== null)
      .some((value) => String(value).toLowerCase().includes(keyword))
    return matchStatus && matchKeyword
  })
})

const pagedTickets = computed(() => {
  const start = (pagination.current - 1) * pagination.size
  return filteredTickets.value.slice(start, start + pagination.size)
})

function normalizePageData(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || data?.data?.records || data?.data || []
}

async function loadTickets() {
  loading.value = true
  try {
    let data = await getTicketPage({ current: 1, size: 500 })
    let records = normalizePageData(data)
    if (!records.length) {
      data = await getTicketList()
      records = normalizePageData(data)
    }
    tickets.value = records
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  pagination.current = 1
}

onMounted(loadTickets)
</script>

<style scoped>
.ticket-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.filter-card,
.table-card {
  border: 0;
  border-radius: 18px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.filter-form {
  margin-top: 18px;
}

.ticket-title {
  font-weight: 700;
  color: #111827;
}

.ticket-desc {
  overflow: hidden;
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
