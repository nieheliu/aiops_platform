<template>
  <div class="system-page">
    <el-card shadow="never" class="filter-card">
      <div class="page-head">
        <div>
          <h1>角色管理</h1>
          <p>维护平台角色编码、名称和描述，作为菜单展示和后续权限控制的基础。</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">新增角色</el-button>
      </div>

      <el-form :model="query" class="filter-form" @submit.prevent>
        <el-input v-model="query.keyword" placeholder="角色编码 / 角色名称" clearable @keyup.enter="handleSearch" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="pagedRoles" stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="roleCode" label="角色编码" min-width="160">
          <template #default="{ row }">
            <el-tag effect="light">{{ row.roleCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column label="描述" min-width="240">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.status ?? 1) === 1 ? 'success' : 'info'" effect="light">
              {{ Number(row.status ?? 1) === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
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
        :total="filteredRoles.length"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model.trim="form.roleCode" placeholder="例如 ADMIN / OPS / VIEWER" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model.trim="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model.trim="form.description" type="textarea" :rows="3" placeholder="请输入角色描述" />
        </el-form-item>
        <el-form-item label="状态">
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRole, deleteRole, getRoles, updateRole } from '../api/system'

const loading = ref(false)
const submitting = ref(false)
const roles = ref([])
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const query = reactive({ keyword: '' })
const page = reactive({ current: 1, size: 10 })
const form = reactive(createEmptyForm())

const formRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

const filteredRoles = computed(() => roles.value.filter((item) => {
  const keyword = query.keyword.trim().toLowerCase()
  return !keyword
    || String(item.roleCode || '').toLowerCase().includes(keyword)
    || String(item.roleName || '').toLowerCase().includes(keyword)
}))

const pagedRoles = computed(() => {
  const start = (page.current - 1) * page.size
  return filteredRoles.value.slice(start, start + page.size)
})

function createEmptyForm() {
  return {
    id: null,
    roleCode: '',
    roleName: '',
    description: '',
    status: 1,
  }
}

function normalizeList(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || data?.data?.records || data?.data || []
}

function resetForm(row = createEmptyForm()) {
  Object.assign(form, createEmptyForm(), row)
}

async function loadData() {
  loading.value = true
  try {
    roles.value = normalizeList(await getRoles())
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.current = 1
}

function handleReset() {
  query.keyword = ''
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
    const payload = { ...form, roleCode: form.roleCode.trim().toUpperCase() }
    if (isEdit.value) {
      await updateRole(payload)
      ElMessage.success('角色已更新')
    } else {
      await createRole(payload)
      ElMessage.success('角色已创建')
    }
    formVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName}？`, '删除确认', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('角色已删除')
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
.filter-form { display: grid; grid-template-columns: 1fr 88px 88px; gap: 12px; margin-top: 22px; }
.pagination { justify-content: flex-end; margin-top: 18px; }
</style>
