<template>
  <el-tag :type="statusConfig.type" effect="light" round>{{ statusConfig.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: [Number, String],
    default: 0,
  },
})

const statusMap = {
  0: { label: '待处理', type: 'danger' },
  1: { label: '处理中', type: 'warning' },
  2: { label: '已解决', type: 'success' },
  3: { label: '已关闭', type: 'info' },
  OPEN: { label: '待处理', type: 'danger' },
  PROCESSING: { label: '处理中', type: 'warning' },
  RESOLVED: { label: '已解决', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info' },
}

const statusConfig = computed(() => statusMap[props.status] || { label: `状态 ${props.status}`, type: 'info' })
</script>
