<template>
  <el-card shadow="never" class="grafana-card">
    <template #header>
      <div class="panel-header">
        <span>{{ title }}</span>
        <el-tag size="small" type="primary" effect="light">Grafana</el-tag>
      </div>
    </template>
    <iframe v-if="src" class="grafana-frame" :src="src" :style="{ height: `${height}px` }" loading="lazy"></iframe>
    <el-empty v-else :image-size="80" description="请配置 Grafana 面板地址" />
  </el-card>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
  src: {
    type: String,
    default: '',
  },
  height: {
    type: Number,
    default: 320,
  },
})
</script>

<style scoped>
.grafana-card {
  overflow: hidden;
  border: 0;
  border-radius: 18px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  color: #111827;
}

.grafana-frame {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 12px;
  background: #f8fafc;
}
</style>
