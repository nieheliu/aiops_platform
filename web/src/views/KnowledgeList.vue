<template>
  <div class="knowledge-page">
    <el-card shadow="never" class="search-card">
      <div class="search-head">
        <div>
          <h1>历史问题搜索</h1>
          <p>搜索已解决工单沉淀的经验总结、AI 根因分析和修复建议，关键词由 Elasticsearch 高亮展示。</p>
        </div>
        <el-tag type="primary" effect="light">Elasticsearch</el-tag>
      </div>
      <el-form class="search-form" @submit.prevent>
        <el-input v-model="query.keyword" size="large" placeholder="输入关键词，例如 CPU、内存、磁盘、JVM" clearable @keyup.enter="handleSearch" />
        <el-button type="primary" size="large" :loading="loading" @click="handleSearch">搜索</el-button>
      </el-form>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="result-card">
      <template #header>
        <div class="result-header">
          <span>搜索结果</span>
          <span class="result-total">共 {{ total }} 条</span>
        </div>
      </template>

      <div v-if="records.length" class="result-list">
        <div v-for="item in records" :key="item.ticketId" class="result-item">
          <div class="item-title" v-html="sanitize(item.titleHighlight || item.title)"></div>
          <div class="item-meta">
            <el-tag size="small">工单 #{{ item.ticketId }}</el-tag>
            <el-tag size="small" type="info">告警 #{{ item.alertId || '-' }}</el-tag>
            <span>解决时间：{{ item.resolvedAt || '-' }}</span>
          </div>
          <div class="snippet"><strong>问题描述：</strong><span v-html="sanitize(item.descriptionHighlight || item.description || '暂无')"></span></div>
          <div class="snippet"><strong>AI 根因：</strong><span v-html="sanitize(item.aiRootCauseHighlight || item.aiRootCause || '暂无')"></span></div>
          <div class="snippet"><strong>经验总结：</strong><span v-html="sanitize(item.experienceSummaryHighlight || item.experienceSummary || '暂无')"></span></div>
          <div class="item-actions">
            <el-button type="primary" plain @click="router.push(`/tickets/${item.ticketId}`)">查看工单详情</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无搜索结果，请先在工单详情中标记已解决并同步经验库" />

      <el-pagination v-if="total > query.size" v-model:current-page="query.page" v-model:page-size="query.size" class="pagination" layout="total, prev, pager, next, sizes" :page-sizes="[5, 10, 20]" :total="total" @current-change="loadData" @size-change="handleSizeChange" />
    </el-card>
  </div>
</template>

<script setup>
import DOMPurify from 'dompurify'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchTicketKnowledge } from '../api/knowledge'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

function sanitize(value) {
  return DOMPurify.sanitize(value || '')
}

async function loadData() {
  loading.value = true
  try {
    const data = await searchTicketKnowledge(query)
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleSizeChange() {
  query.page = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.knowledge-page { display: flex; flex-direction: column; gap: 20px; }
.search-card, .result-card { border: 0; border-radius: 18px; }
.search-head, .result-header { display: flex; align-items: center; justify-content: space-between; }
h1 { margin: 0 0 8px; color: #111827; font-size: 26px; }
p { margin: 0; color: #6b7280; }
.search-form { display: grid; grid-template-columns: 1fr 120px; gap: 14px; margin-top: 22px; }
.result-total { color: #6b7280; font-size: 13px; }
.result-list { display: flex; flex-direction: column; gap: 16px; }
.result-item { padding: 18px; border: 1px solid #e5e7eb; border-radius: 16px; background: #fff; }
.item-title { color: #111827; font-size: 18px; font-weight: 800; }
.item-meta { display: flex; gap: 8px; align-items: center; margin: 10px 0 14px; color: #6b7280; font-size: 13px; }
.snippet { margin-top: 8px; color: #4b5563; line-height: 1.7; }
.snippet strong { color: #111827; }
.item-actions { margin-top: 14px; }
.pagination { justify-content: flex-end; margin-top: 20px; }
:deep(em) { padding: 0 3px; border-radius: 4px; background: #fef3c7; color: #b45309; font-style: normal; font-weight: 800; }
</style>
