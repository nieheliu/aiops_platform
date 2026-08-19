<template>
  <el-dialog v-model="visible" title="选择诊断模型" width="560px" @closed="handleClosed">
    <p class="dialog-tip">同一告警/工单下，每个模型只能诊断一次。已使用过的模型不可重复选择。</p>
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-radio-group v-else v-model="selectedModelId" class="model-list">
      <el-radio
        v-for="item in models"
        :key="item.id"
        :value="item.id"
        :disabled="!item.available"
        class="model-item"
      >
        <div class="model-item-content">
          <div class="model-item-title">
            <span>{{ item.name }}</span>
            <el-tag v-if="item.free" size="small" type="success" effect="light">免费</el-tag>
            <el-tag v-if="item.used" size="small" type="info" effect="light">已诊断</el-tag>
          </div>
          <div class="model-item-meta">{{ providerLabel(item.provider) }} · {{ item.model }}</div>
        </div>
      </el-radio>
    </el-radio-group>
    <el-empty v-if="!loading && !models.length" description="暂无可用模型" />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selectedModelId" @click="handleConfirm">开始诊断</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAvailableModels } from '../api/model'

const props = defineProps({
  alertId: { type: [Number, String], default: null },
  ticketId: { type: [Number, String], default: null },
})

const emit = defineEmits(['confirm'])

const visible = defineModel({ type: Boolean, default: false })
const loading = ref(false)
const submitting = ref(false)
const models = ref([])
const selectedModelId = ref('')

watch(visible, async (open) => {
  if (!open) return
  loading.value = true
  selectedModelId.value = ''
  try {
    const params = {}
    if (props.ticketId) params.ticketId = props.ticketId
    if (props.alertId) params.alertId = props.alertId
    models.value = await getAvailableModels(params)
    const firstAvailable = models.value.find((item) => item.available)
    if (firstAvailable) selectedModelId.value = firstAvailable.id
  } finally {
    loading.value = false
  }
})

function providerLabel(provider) {
  if (provider === 'bailian') return '阿里云百炼'
  if (provider === 'opencode') return 'OpenCode Zen'
  return provider || '未知平台'
}

function handleConfirm() {
  if (!selectedModelId.value) {
    ElMessage.warning('请选择诊断模型')
    return
  }
  emit('confirm', selectedModelId.value)
}

function handleClosed() {
  selectedModelId.value = ''
}

defineExpose({
  setSubmitting(value) {
    submitting.value = value
  },
  close() {
    visible.value = false
  },
})
</script>

<style scoped>
.dialog-tip { margin: 0 0 16px; color: #6b7280; line-height: 1.6; }
.model-list { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.model-item { width: 100%; margin: 0; height: auto; align-items: flex-start; padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 12px; }
.model-item-content { display: flex; flex-direction: column; gap: 6px; }
.model-item-title { display: flex; gap: 8px; align-items: center; font-weight: 700; color: #111827; }
.model-item-meta { color: #6b7280; font-size: 13px; }
</style>
