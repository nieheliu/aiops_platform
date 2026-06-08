<template>
  <el-container class="layout">
    <el-aside width="240px" class="layout-aside">
      <div class="brand">
        <div class="brand-logo">AI</div>
        <div>
          <div class="brand-title">智能运维平台</div>
          <div class="brand-subtitle">AIOps Console</div>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="side-menu" background-color="transparent" text-color="#cbd5e1" active-text-color="#ffffff">
        <el-menu-item v-if="canView('dashboard:view')" index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>首页仪表盘</span>
        </el-menu-item>
        <el-menu-item v-if="canView('alert')" index="/alerts">
          <el-icon><Bell /></el-icon>
          <span>告警管理</span>
        </el-menu-item>
        <el-menu-item v-if="canView('ticket')" index="/tickets">
          <el-icon><Tickets /></el-icon>
          <span>工单管理</span>
        </el-menu-item>
        <el-menu-item v-if="canView('diagnosis')" index="/diagnoses">
          <el-icon><Cpu /></el-icon>
          <span>大模型诊断</span>
        </el-menu-item>
        <el-menu-item v-if="canView('knowledge')" index="/knowledge">
          <el-icon><Collection /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-sub-menu v-if="showSystemMenu" index="/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item v-if="canView('system:user')" index="/system/users">用户管理</el-menu-item>
          <el-menu-item v-if="canView('system:role')" index="/system/roles">角色管理</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div>
          <div class="header-title">{{ route.meta.title || '智能运维平台' }}</div>
          <div class="header-subtitle">统一告警、工单、大模型诊断与知识库管理</div>
        </div>
        <div class="header-actions">
          <el-tag type="success" effect="light">在线</el-tag>
          <el-avatar :size="34">{{ authStore.displayName.slice(0, 1) }}</el-avatar>
          <span class="username">{{ authStore.displayName }}</span>
          <el-button type="primary" plain @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const showSystemMenu = computed(() => canView('system:user') || canView('system:role'))

function canView(permission) {
  const permissions = authStore.permissions
  if (permission.includes(':')) {
    return permissions.includes(permission)
  }
  return permissions.includes(`${permission}:manage`) || permissions.includes(`${permission}:view`)
}

function handleLogout() {
  authStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout-aside {
  overflow: hidden;
  background: linear-gradient(180deg, #111827 0%, #1e1b4b 100%);
  box-shadow: 8px 0 30px rgba(15, 23, 42, 0.18);
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  height: 76px;
  padding: 0 20px;
  color: #fff;
}

.brand-logo {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #38bdf8, #8b5cf6);
  font-weight: 800;
}

.brand-title {
  font-size: 18px;
  font-weight: 700;
}

.brand-subtitle {
  margin-top: 3px;
  font-size: 12px;
  color: #94a3b8;
}

.side-menu {
  border-right: 0;
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 48px;
  margin: 6px 12px;
  border-radius: 12px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 76px;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid #e5e7eb;
  backdrop-filter: blur(8px);
}

.header-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.header-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-weight: 600;
  color: #374151;
}

.layout-main {
  padding: 24px;
  background: radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 30%), #f5f7fb;
}
</style>
