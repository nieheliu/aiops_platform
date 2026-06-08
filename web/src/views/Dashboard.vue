<template>
  <div class="dashboard">
    <div class="hero">
      <div>
        <div class="hero-badge">AIOps Situation Dashboard</div>
        <h1>运维态势大屏</h1>
        <p>融合 Redis 缓存统计与 Grafana 基础设施监控，实时掌握系统运行状态。</p>
        <div class="cache-info">
          <el-tag :type="summary.cacheHit ? 'success' : 'warning'" effect="light">
            {{ summary.cacheHit ? 'Redis 缓存命中' : '实时查询 MySQL' }}
          </el-tag>
          <span>更新时间：{{ summary.generatedAt || '-' }}</span>
          <span>缓存过期：{{ summary.expireSeconds || 300 }} 秒</span>
        </div>
      </div>
      <el-button type="primary" size="large" :loading="loading" @click="loadDashboardData">刷新数据</el-button>
    </div>

    <div class="metrics">
      <el-card v-for="item in metrics" :key="item.label" shadow="never" class="metric-card">
        <div class="metric-icon" :style="{ background: item.color }">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <div class="metric-value">{{ item.value }}</div>
          <div class="metric-label">{{ item.label }}</div>
        </div>
      </el-card>
    </div>

    <el-row :gutter="20" class="section-row">
      <el-col :span="15">
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">近 7 天告警趋势</div></template>
          <div ref="alertTrendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="9">
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">工单状态分布</div></template>
          <div ref="ticketStatusRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="section-row">
      <el-col :span="12">
        <el-card v-loading="loading" shadow="never" class="panel-card">
          <template #header><div class="card-title">告警等级分布</div></template>
          <div ref="alertSeverityRef" class="chart small-chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="panel-card summary-card">
          <template #header><div class="card-title">平台运行摘要</div></template>
          <el-timeline>
            <el-timeline-item type="danger" timestamp="告警态势">今日新增 {{ summary.todayAlertCount }} 条告警事件，请关注高等级告警分布。</el-timeline-item>
            <el-timeline-item type="warning" timestamp="工单处置">待处理/处理中工单 {{ summary.pendingTicketCount }} 条，建议优先关注未解决工单。</el-timeline-item>
            <el-timeline-item type="primary" timestamp="AI 诊断">今日新增 {{ summary.todayDiagnosisCount }} 份 AI 诊断报告，可辅助快速定位根因。</el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title">
      <h2>Grafana 监控图表</h2>
      <p>通过 iframe 嵌入 Node Exporter 监控面板。若显示登录页或拒绝连接，请检查 Grafana 是否允许嵌入和匿名访问。</p>
    </div>

    <el-row :gutter="20" class="section-row">
      <el-col v-for="panel in grafanaPanels" :key="panel.title" :span="12">
        <GrafanaPanel :title="panel.title" :src="panel.src" :height="360" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboardSummary } from '../api/dashboard'
import GrafanaPanel from '../components/GrafanaPanel.vue'

const loading = ref(false)
const summary = ref({
  todayAlertCount: 0,
  pendingTicketCount: 0,
  todayDiagnosisCount: 0,
  knowledgeCount: 0,
  ticketStatusStats: [],
  alertSeverityStats: [],
  alertTrend: [],
  cacheHit: false,
  generatedAt: '',
  expireSeconds: 300,
})

const alertTrendRef = ref()
const ticketStatusRef = ref()
const alertSeverityRef = ref()
let alertTrendChart = null
let ticketStatusChart = null
let alertSeverityChart = null

const grafanaPanels = [
  { title: '系统平均负载', src: 'http://192.168.30.131:31735/d/9CWBz0bik/1-node-exporter-0-16-0-17-for-prometheus-jian-kong-zhan-shi-kan-ban?orgId=1&panelId=13&fullscreen&from=1780539499704&to=1780539799704&var-interval=1m&var-env=&var-name=&var-node=cka-master&var-maxmount=%2Frootfs' },
  { title: '内存使用率', src: 'http://192.168.30.131:31735/d/9CWBz0bik/1-node-exporter-0-16-0-17-for-prometheus-jian-kong-zhan-shi-kan-ban?orgId=1&panelId=164&fullscreen&from=1780539549723&to=1780539849723&var-interval=1m&var-env=&var-name=&var-node=cka-master&var-maxmount=%2Frootfs' },
]

const metrics = computed(() => [
  { label: '今日总告警数', value: summary.value.todayAlertCount, icon: 'Bell', color: 'linear-gradient(135deg, #ef4444, #f97316)' },
  { label: '待处理工单', value: summary.value.pendingTicketCount, icon: 'Tickets', color: 'linear-gradient(135deg, #f59e0b, #eab308)' },
  { label: '今日AI诊断', value: summary.value.todayDiagnosisCount, icon: 'Cpu', color: 'linear-gradient(135deg, #2563eb, #06b6d4)' },
  { label: '知识库条目', value: summary.value.knowledgeCount, icon: 'Collection', color: 'linear-gradient(135deg, #7c3aed, #db2777)' },
])

function renderCharts() {
  if (!alertTrendRef.value || !ticketStatusRef.value || !alertSeverityRef.value) return
  alertTrendChart ||= echarts.init(alertTrendRef.value)
  ticketStatusChart ||= echarts.init(ticketStatusRef.value)
  alertSeverityChart ||= echarts.init(alertSeverityRef.value)

  const trend = summary.value.alertTrend || []
  alertTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 24, top: 30, bottom: 34 },
    xAxis: { type: 'category', data: trend.map((item) => item.name), boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '告警数', type: 'line', smooth: true, areaStyle: {}, data: trend.map((item) => item.value), lineStyle: { width: 3 }, itemStyle: { color: '#2563eb' } }],
  })

  ticketStatusChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{ name: '工单状态', type: 'pie', radius: ['45%', '70%'], center: ['50%', '44%'], data: summary.value.ticketStatusStats || [] }],
  })

  const severity = summary.value.alertSeverityStats || []
  alertSeverityChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 18, top: 24, bottom: 34 },
    xAxis: { type: 'category', data: severity.map((item) => item.name) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '告警等级', type: 'bar', data: severity.map((item) => item.value), itemStyle: { color: '#7c3aed', borderRadius: [8, 8, 0, 0] } }],
  })
}

async function loadDashboardData() {
  loading.value = true
  try {
    summary.value = await getDashboardSummary()
    await nextTick()
    renderCharts()
  } catch (error) {
    ElMessage.error('Dashboard 数据加载失败，请检查后端服务、Redis 和接口代理')
  } finally {
    loading.value = false
  }
}

function resizeCharts() {
  alertTrendChart?.resize()
  ticketStatusChart?.resize()
  alertSeverityChart?.resize()
}

onMounted(() => {
  loadDashboardData()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  alertTrendChart?.dispose()
  ticketStatusChart?.dispose()
  alertSeverityChart?.dispose()
})
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 20px; }
.hero { display: flex; align-items: center; justify-content: space-between; padding: 30px; border-radius: 22px; color: #fff; background: radial-gradient(circle at top right, rgba(125, 211, 252, 0.36), transparent 28%), linear-gradient(135deg, #0f172a, #1d4ed8 52%, #7c3aed); box-shadow: 0 20px 45px rgba(79, 70, 229, 0.24); }
.hero-badge { display: inline-flex; padding: 6px 12px; border: 1px solid rgba(255, 255, 255, 0.28); border-radius: 999px; background: rgba(255, 255, 255, 0.12); font-size: 12px; font-weight: 700; }
.hero h1 { margin: 12px 0 8px; font-size: 32px; }
.hero p { margin: 0; color: #dbeafe; }
.cache-info { display: flex; gap: 12px; align-items: center; margin-top: 14px; color: #dbeafe; font-size: 13px; }
.metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
.metric-card, .panel-card { border: 0; border-radius: 18px; }
.metric-card :deep(.el-card__body) { display: flex; gap: 16px; align-items: center; }
.metric-icon { display: grid; width: 54px; height: 54px; place-items: center; border-radius: 18px; color: #fff; font-size: 24px; }
.metric-value { font-size: 28px; font-weight: 800; color: #111827; }
.metric-label { margin-top: 4px; color: #6b7280; }
.section-row { width: 100%; }
.card-title { font-weight: 700; color: #111827; }
.chart { width: 100%; height: 340px; }
.small-chart { height: 280px; }
.summary-card { min-height: 360px; }
.section-title h2 { margin: 8px 0 6px; color: #111827; font-size: 22px; }
.section-title p { margin: 0; color: #6b7280; }
</style>
