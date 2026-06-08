<template>
  <div class="system-page">
    <el-card shadow="never" class="filter-card">
      <div class="page-head">
        <div>
          <h1>用户管理</h1>
          <p>维护平台账号、登录状态和用户角色，用于多用户运维协作。</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
      </div>

      <el-form :model="query" class="filter-form" @submit.prevent>
        <el-input v-model="query.username" placeholder="用户名" clearable @keyup.enter="handleSearch" />
        <el-input v-model="query.realName" placeholder="姓名" clearable @keyup.enter="handleSearch" />
        <el-select v-model="query.status" placeholder="状态" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="pagedUsers" stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column label="姓名" min-width="130">
          <template #default="{ row }">{{ row.realName || '-' }}</template>
        </el-table-column>
        <el-table-column label="手机" min-width="140">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="180">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" effect="light">
              {{ Number(row.status) === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="primary" link @click="openRoleDialog(row)">分配角色</el-button>
            <el-button type="warning" link @click="handleResetPassword(row)">重置密码</el-button>
            <el-button v-if="Number(row.status) === 1" type="info" link @click="handleDisable(row)">禁用</el-button>
            <el-button v-else type="success" link @click="handleEnable(row)">启用</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        class="pagination"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        :total="filteredUsers.length"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model.trim="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model.trim="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model.trim="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="520px">
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ currentUser?.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="selectedRoleIds">
            <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">
              {{ role.roleName }}（{{ role.roleCode }}）
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignUserRoles,
  createUser,
  deleteUser,
  disableUser,
  enableUser,
  getRoles,
  getUserRoles,
  getUsers,
  resetUserPassword,
  updateUser,
} from '../api/system'

const loading = ref(false)
const submitting = ref(false)
const roleSubmitting = ref(false)
const users = ref([])
const roles = ref([])
const formVisible = ref(false)
const roleVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const currentUser = ref(null)
const selectedRoleIds = ref([])

const query = reactive({ username: '', realName: '', status: '' })
const page = reactive({ current: 1, size: 10 })
const form = reactive(createEmptyForm())

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const filteredUsers = computed(() => users.value.filter((item) => {
  const username = query.username.trim().toLowerCase()
  const realName = query.realName.trim().toLowerCase()
  const matchUsername = !username || String(item.username || '').toLowerCase().includes(username)
  const matchRealName = !realName || String(item.realName || '').toLowerCase().includes(realName)
  const matchStatus = query.status === '' || query.status === null || Number(item.status) === Number(query.status)
  return matchUsername && matchRealName && matchStatus
}))

const pagedUsers = computed(() => {
  const start = (page.current - 1) * page.size
  return filteredUsers.value.slice(start, start + page.size)
})

function createEmptyForm() {
  return {
    id: null,
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    status: 1,
  }
}

function normalizeList(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || data?.data?.records || data?.data || []
}

function resetForm(row = createEmptyForm()) {
  Object.assign(form, createEmptyForm(), row, { password: '' })
}

async function loadData() {
  loading.value = true
  try {
    const [userData, roleData] = await Promise.all([getUsers(), getRoles()])
    users.value = normalizeList(userData)
    roles.value = normalizeList(roleData)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.current = 1
}

function handleReset() {
  Object.assign(query, { username: '', realName: '', status: '' })
  page.current = 1
}

function openCreateDialog() {
  isEdit.value = false
  resetForm()
  formVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  resetForm(row)
  formVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = { ...form }
    if (isEdit.value) {
      delete payload.password
      await updateUser(payload)
      ElMessage.success('用户已更新')
    } else {
      await createUser(payload)
      ElMessage.success('用户已创建')
    }
    formVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function openRoleDialog(row) {
  currentUser.value = row
  roleVisible.value = true
  const userRoles = normalizeList(await getUserRoles(row.id))
  selectedRoleIds.value = userRoles.map((role) => role.id)
}

async function submitRoles() {
  roleSubmitting.value = true
  try {
    await assignUserRoles(currentUser.value.id, selectedRoleIds.value)
    ElMessage.success('角色已更新')
    roleVisible.value = false
  } finally {
    roleSubmitting.value = false
  }
}

async function handleResetPassword(row) {
  const { value } = await ElMessageBox.prompt(`请输入 ${row.username} 的新密码`, '重置密码', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputType: 'password',
    inputValidator: (value) => Boolean(value && value.trim()),
    inputErrorMessage: '新密码不能为空',
  })
  await resetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function handleEnable(row) {
  await enableUser(row.id)
  ElMessage.success('用户已启用')
  await loadData()
}

async function handleDisable(row) {
  await disableUser(row.id)
  ElMessage.success('用户已禁用')
  await loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除用户 ${row.username}？`, '删除确认', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('用户已删除')
  await loadData()
}

onMounted(loadData)
</script>

<style scoped>
.system-page { display: flex; flex-direction: column; gap: 20px; }
.filter-card, .table-card { border: 0; border-radius: 18px; }
.page-head { display: flex; align-items: center; justify-content: space-between; }
h1 { margin: 0 0 8px; color: #111827; font-size: 26px; }
p { margin: 0; color: #6b7280; }
.filter-form { display: grid; grid-template-columns: 1fr 1fr 180px 88px 88px; gap: 12px; margin-top: 22px; }
.pagination { justify-content: flex-end; margin-top: 18px; }
:deep(.el-checkbox-group) { display: grid; gap: 10px; }
</style>
