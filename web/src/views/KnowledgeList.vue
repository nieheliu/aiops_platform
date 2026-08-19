<template>
  <div class="knowledge-page">
    <el-card shadow="never" class="search-card">
      <div class="search-head">
        <div>
          <h1>知识库</h1>
          <p>搜索工单沉淀与诊断导入的经验知识，支持关键词检索与手动录入。</p>
        </div>
        <div class="search-actions">
          <el-tag type="primary" effect="light">Elasticsearch</el-tag>
          <el-button v-if="canManage" type="primary" @click="openCreateDialog">新建知识</el-button>
          <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
        </div>
      </div>
      <el-form class="search-form" @submit.prevent>
        <el-input v-model="query.keyword" size="large" placeholder="输入关键词，例如 CPU、内存、磁盘、JVM" clearable @keyup.enter="handleSearch" />
        <el-select v-model="query.sourceType" size="large" placeholder="来源类型" clearable>
          <el-option label="诊断导入" value="diagnosis_import" />
          <el-option label="工单沉淀" value="ticket_resolve" />
          <el-option label="手动录入" value="manual_import" />
        </el-select>
        <el-input v-model="query.model" size="large" placeholder="模型名称" clearable @keyup.enter="handleSearch" />
        <el-button type="primary" size="large" :loading="loading" @click="handleSearch">搜索</el-button>
      </el-form>
    </el-card>

    <el-card v-if="canManage && workflowItems.length" shadow="never" class="workflow-card">
      <template #header>
        <div class="result-header">
          <span>流程中的知识（草稿 / 待审核）</span>
          <span class="result-total">{{ workflowItems.length }} 条</span>
        </div>
      </template>
      <div class="workflow-list">
        <div v-for="item in workflowItems" :key="`wf-${item.id}`" class="workflow-item">
          <div>
            <div class="item-title">{{ item.title }}</div>
            <div class="item-meta">
              <el-tag size="small" effect="light">{{ getLifecycleLabel(item.lifecycleStatus) }}</el-tag>
              <span>版本 v{{ item.version || 1 }}</span>
            </div>
          </div>
          <div class="item-actions">
            <el-button type="primary" link @click="openEditDialog(item.id)">编辑</el-button>
            <el-button v-if="item.lifecycleStatus === 'DRAFT'" type="warning" link @click="handleSubmit(item)">提交审核</el-button>
            <el-button v-if="isAdmin && item.lifecycleStatus === 'PENDING_REVIEW'" type="success" link @click="handlePublish(item)">审核发布</el-button>
            <el-button type="danger" link @click="handleDeleteWorkflow(item)">删除</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="result-card">
      <template #header>
        <div class="result-header">
          <span>搜索结果</span>
          <span class="result-total">
            共 {{ total }} 条<span v-if="displayRecords.length !== records.length">，当前页筛选 {{ displayRecords.length }} 条</span>
          </span>
        </div>
      </template>

      <div v-if="displayRecords.length" class="result-list">
        <el-collapse v-model="expandedItems" class="result-collapse">
          <el-collapse-item
            v-for="(item, index) in displayRecords"
            :key="item.documentId || `${item.ticketId || 'na'}-${item.alertId || 'na'}-${index}`"
            :name="item.documentId || `${item.ticketId || 'na'}-${item.alertId || 'na'}-${index}`"
          >
            <template #title>
              <div class="item-top">
                <div class="item-title" v-html="sanitize(item.titleHighlight || item.title)"></div>
                <div class="item-tags">
                  <el-tag :type="item.sourceType === 'ticket_resolve' ? 'success' : 'primary'" effect="light">
                    {{ getSourceTypeLabel(item.sourceType) }}
                  </el-tag>
                  <el-tag v-if="item.aiModel" size="small" type="primary" effect="plain">{{ getModelDisplayName(item.aiModel) }}</el-tag>
                  <el-tag v-if="item.lifecycleStatus" size="small" effect="plain">{{ getLifecycleLabel(item.lifecycleStatus) }}</el-tag>
                </div>
              </div>
            </template>

            <div class="item-meta">
              <el-tag v-if="item.ticketId" size="small">工单 #{{ item.ticketId }}</el-tag>
              <el-tag v-else size="small" type="warning">无关联工单</el-tag>
              <el-tag size="small" type="info">告警 #{{ item.alertId || '-' }}</el-tag>
              <el-tag v-if="item.diagnosisId" size="small" type="success" effect="plain">诊断 #{{ item.diagnosisId }}</el-tag>
              <span>入库时间：{{ formatKnowledgeTime(item) }}</span>
            </div>

            <div class="snippet"><strong>问题描述：</strong><span v-html="sanitize(item.descriptionHighlight || item.description || '暂无')"></span></div>
            <div class="snippet"><strong>AI 根因：</strong><span v-html="sanitize(item.aiRootCauseHighlight || item.aiRootCause || '暂无')"></span></div>
            <div class="snippet"><strong>经验总结：</strong><span v-html="sanitize(item.experienceSummaryHighlight || item.experienceSummary || item.contentMd || '暂无')"></span></div>

            <div class="item-actions">
              <el-button v-if="item.knowledgeId && canManage" type="primary" plain @click="openEditDialog(item.knowledgeId)">编辑</el-button>
              <el-button v-if="item.diagnosisId" type="success" plain @click="router.push(`/diagnoses/${item.diagnosisId}`)">查看诊断</el-button>
              <el-button v-if="item.ticketId" type="primary" plain @click="router.push(`/tickets/${item.ticketId}`)">查看工单详情</el-button>
              <el-button v-else-if="item.alertId" type="primary" plain @click="router.push('/alerts')">查看告警列表</el-button>
              <el-button
                v-if="canManage"
                type="danger"
                plain
                :loading="deletingId === item.documentId"
                @click="handleDelete(item)"
              >删除</el-button>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <el-empty v-else description="暂无搜索结果，请先完成工单解决或同步诊断到知识库" />

      <el-pagination
        v-if="total > query.size"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        class="pagination"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[5, 10, 20]"
        :total="total"
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑知识' : '新建知识'" width="760px" destroy-on-close>
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model.trim="editForm.title" placeholder="请输入知识标题" />
        </el-form-item>
        <el-form-item label="组件" required>
          <el-select v-model="editForm.component" placeholder="选择组件分类" style="width: 100%">
            <el-option v-for="item in componentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="editForm.contentMd"
            type="textarea"
            :rows="14"
            placeholder="支持直接粘贴 Markdown 或运维经验文本"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import DOMPurify from 'dompurify'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createKnowledgeArticle,
  deleteKnowledgeArticle,
  deleteTicketKnowledge,
  getKnowledgeArticle,
  getKnowledgeWorkflowList,
  publishKnowledgeArticle,
  searchTicketKnowledge,
  submitKnowledgeArticle,
  updateKnowledgeArticle,
} from '../api/knowledge'
import { useAuthStore } from '../stores/auth'
import { getModelDisplayName, getSourceTypeLabel } from '../utils/model'

const router = useRouter()
const authStore = useAuthStore()
const canManage = computed(() => authStore.permissions.includes('knowledge:manage'))
const isAdmin = computed(() => authStore.roles.includes('ADMIN'))

const loading = ref(false)
const editSubmitting = ref(false)
const deletingId = ref(null)
const records = ref([])
const workflowItems = ref([])
const total = ref(0)
const expandedItems = ref([])
const editVisible = ref(false)

const query = reactive({
  keyword: '',
  sourceType: '',
  model: '',
  page: 1,
  size: 10,
})

const editForm = reactive({
  id: null,
  title: '',
  component: 'other',
  contentMd: '',
})

const componentOptions = [
  { label: 'MySQL', value: 'mysql' },
  { label: 'Redis', value: 'redis' },
  { label: 'RabbitMQ', value: 'rabbitmq' },
  { label: 'Elasticsearch', value: 'elasticsearch' },
  { label: 'CPU', value: 'cpu' },
  { label: '内存', value: 'memory' },
  { label: '磁盘', value: 'disk' },
  { label: '网络', value: 'network' },
  { label: 'JVM', value: 'jvm' },
  { label: '应用', value: 'application' },
  { label: '其他', value: 'other' },
]

const displayRecords = computed(() => {
  const model = query.model.trim().toLowerCase()
  const sourceType = query.sourceType
  return records.value.filter((item) => {
    const matchSource = !sourceType || item.sourceType === sourceType
    const matchModel = !model || String(item.aiModel || '').toLowerCase().includes(model)
      || getModelDisplayName(item.aiModel).toLowerCase().includes(model)
    return matchSource && matchModel
  })
})

watch(displayRecords, (items) => {
  expandedItems.value = items.slice(0, 3).map((item, index) => item.documentId || `${item.ticketId || 'na'}-${item.alertId || 'na'}-${index}`)
}, { immediate: true })

function sanitize(value) {
  return DOMPurify.sanitize(value || '')
}

function getLifecycleLabel(status) {
  const map = {
    DRAFT: '草稿',
    PENDING_REVIEW: '待审核',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
    DEPRECATED: '已废弃',
  }
  return map[status] || status || '-'
}

function formatKnowledgeTime(item) {
  return item.indexedAt || item.resolvedAt || '-'
}

async function loadData() {
  loading.value = true
  try {
    const data = await searchTicketKnowledge({
      keyword: query.keyword,
      page: query.page,
      size: query.size,
    })
    records.value = data.records || []
    total.value = data.total || 0
    if (canManage.value) {
      const workflowData = await getKnowledgeWorkflowList()
      workflowItems.value = (workflowData || []).filter((item) => ['DRAFT', 'PENDING_REVIEW'].includes(item.lifecycleStatus))
    }
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

function resetEditForm() {
  Object.assign(editForm, { id: null, title: '', component: 'other', contentMd: '' })
}

function openCreateDialog() {
  resetEditForm()
  editVisible.value = true
}

async function openEditDialog(id) {
  const data = await getKnowledgeArticle(id)
  Object.assign(editForm, {
    id: data.id,
    title: data.title,
    component: data.component || 'other',
    contentMd: data.contentMd || '',
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.title.trim() || !editForm.contentMd.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  editSubmitting.value = true
  try {
    const payload = {
      title: editForm.title.trim(),
      component: editForm.component,
      contentMd: editForm.contentMd.trim(),
    }
    if (editForm.id) {
      await updateKnowledgeArticle(editForm.id, payload)
      ElMessage.success('知识草稿已更新')
    } else {
      await createKnowledgeArticle(payload)
      ElMessage.success('知识草稿已创建')
    }
    editVisible.value = false
    await loadData()
  } finally {
    editSubmitting.value = false
  }
}

async function handleSubmit(item) {
  await submitKnowledgeArticle(item.id)
  ElMessage.success('已提交审核')
  await loadData()
}

async function handlePublish(item) {
  await publishKnowledgeArticle(item.id)
  ElMessage.success('已审核发布')
  await loadData()
}

async function handleDeleteWorkflow(item) {
  await handleDelete({ title: item.title, knowledgeId: item.id, documentId: `knowledge_${item.id}` })
}

async function handleDelete(item) {
  if (!item.documentId && !item.knowledgeId) {
    ElMessage.warning('该条目缺少文档 ID，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除知识「${item.title || item.documentId}」吗？将同时移除检索索引与数据库记录。`, '删除知识', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  deletingId.value = item.documentId
  try {
    if (item.knowledgeId) {
      await deleteKnowledgeArticle(item.knowledgeId)
    } else if (item.documentId) {
      await deleteTicketKnowledge(item.documentId)
    }
    ElMessage.success('知识已删除')
    await loadData()
  } finally {
    deletingId.value = null
  }
}

onMounted(loadData)
</script>

<style scoped>
.knowledge-page { display: flex; flex-direction: column; gap: 20px; }
.search-card, .result-card, .workflow-card { border: 0; border-radius: 18px; }
.search-head, .result-header, .search-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h1 { margin: 0 0 8px; color: #111827; font-size: 26px; }
p { margin: 0; color: #6b7280; }
.search-form { display: grid; grid-template-columns: 1.4fr 180px 180px 120px; gap: 14px; margin-top: 22px; }
.result-total { color: #6b7280; font-size: 13px; }
.workflow-list { display: flex; flex-direction: column; gap: 12px; }
.workflow-item { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f3f4f6; }
.workflow-item:last-child { border-bottom: 0; }
.result-list { display: flex; flex-direction: column; gap: 16px; }
.result-collapse { border: 0; }
.result-collapse :deep(.el-collapse-item) { margin-bottom: 12px; border: 1px solid #e5e7eb; border-radius: 16px; overflow: hidden; }
.result-collapse :deep(.el-collapse-item__header) { height: auto; line-height: 1.5; padding: 16px 18px; border-bottom: 0; }
.result-collapse :deep(.el-collapse-item__wrap), .result-collapse :deep(.el-collapse-item__content) { border: 0; }
.result-collapse :deep(.el-collapse-item__content) { padding: 0 18px 18px; }
.item-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; width: 100%; padding-right: 12px; }
.item-title { color: #111827; font-size: 18px; font-weight: 800; flex: 1; }
.item-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.item-meta { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 0 0 14px; color: #6b7280; font-size: 13px; }
.snippet { margin-top: 8px; color: #4b5563; line-height: 1.7; }
.snippet strong { color: #111827; }
.item-actions { margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap; }
.pagination { justify-content: flex-end; margin-top: 20px; }
:deep(em) { padding: 0 3px; border-radius: 4px; background: #fef3c7; color: #b45309; font-style: normal; font-weight: 800; }
</style>
