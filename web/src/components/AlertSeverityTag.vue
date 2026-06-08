<template>
  <el-tag :type="config.type" effect="light">{{ config.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  severity: {
    type: [Number, String],
    default: 0,
  },
})

const config = computed(() => {
  const map = {
    0: { label: '提示', type: 'info' },
    1: { label: '一般', type: 'warning' },
    2: { label: '严重', type: 'danger' },
    3: { label: '致命', type: 'danger' },
  }
  return map[Number(props.severity)] || { label: '未知', type: 'info' }
})
</script>
