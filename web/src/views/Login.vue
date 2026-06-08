<template>
  <div class="login-page">
    <div class="login-shell">
      <section class="intro-panel">
        <div class="intro-badge">AIOps Platform</div>
        <h1>智能运维平台</h1>
        <p>聚合告警、工单、AI 诊断和知识库能力，帮助运维团队更快定位问题、沉淀经验并提升响应效率。</p>
        <div class="intro-cards">
          <div class="intro-card"><strong>统一告警</strong><span>集中监控核心事件</span></div>
          <div class="intro-card"><strong>AI诊断</strong><span>辅助分析故障根因</span></div>
          <div class="intro-card"><strong>知识沉淀</strong><span>复用运维处置经验</span></div>
        </div>
      </section>

      <section class="login-card">
        <div class="card-head">
          <h2>欢迎登录</h2>
          <p>请输入账号密码进入控制台</p>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" placeholder="密码" prefix-icon="Lock" type="password" show-password clearable />
          </el-form-item>
          <el-button type="primary" class="login-button" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form>
        <div class="login-tip">默认调用登录接口：{{ loginUrl }}</div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)
const loginUrl = computed(() => import.meta.env.VITE_LOGIN_URL || '/auth/login')

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: linear-gradient(135deg, #172554 0%, #3730a3 45%, #7c3aed 100%);
}

.login-shell {
  display: grid;
  grid-template-columns: 1.15fr 420px;
  width: min(1100px, 100%);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.14);
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(16px);
}

.intro-panel {
  padding: 64px;
  color: #fff;
}

.intro-badge {
  display: inline-flex;
  padding: 8px 14px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-weight: 700;
}

.intro-panel h1 {
  margin: 34px 0 18px;
  font-size: 48px;
  line-height: 1.1;
}

.intro-panel p {
  width: 80%;
  color: #dbeafe;
  font-size: 17px;
  line-height: 1.8;
}

.intro-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-top: 60px;
}

.intro-card {
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.12);
}

.intro-card strong,
.intro-card span {
  display: block;
}

.intro-card span {
  margin-top: 8px;
  color: #c7d2fe;
  font-size: 13px;
}

.login-card {
  margin: 18px;
  padding: 46px 38px;
  border-radius: 24px;
  background: #fff;
}

.card-head h2 {
  margin: 0;
  color: #111827;
  font-size: 30px;
}

.card-head p {
  margin: 10px 0 34px;
  color: #6b7280;
}

.login-button {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  border: 0;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  font-weight: 700;
}

.login-tip {
  margin-top: 18px;
  color: #9ca3af;
  font-size: 12px;
  text-align: center;
}
</style>
