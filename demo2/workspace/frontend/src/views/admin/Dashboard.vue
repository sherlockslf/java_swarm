<template>
  <div class="admin-dashboard">
    <el-container>
      <el-aside width="200px" class="sidebar">
        <el-menu default-active="1" class="menu" @select="handleMenuSelect">
          <el-menu-item index="1">
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="2">
            <span>系统配置</span>
          </el-menu-item>
          <el-menu-item index="3">
            <span>审计日志</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main-content">
        <div v-if="activeMenu === '1'" class="content-section">
          <h2>用户管理</h2>
          <el-button type="primary" @click="showCreateUserDialog = true">新增用户</el-button>
          <el-table :data="users" style="width: 100%; margin-top: 20px;">
            <el-table-column prop="username" label="用户名" width="180"></el-table-column>
            <el-table-column prop="role" label="角色" width="180"></el-table-column>
            <el-table-column prop="enabled" label="状态" width="180">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'danger'">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button size="small" @click="toggleUserStatus(row)">切换状态</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <div v-if="activeMenu === '2'" class="content-section">
          <h2>系统配置</h2>
          <el-form :model="configForm" label-width="120px">
            <el-form-item label="API密钥">
              <el-input v-model="configForm.apiKey" type="password" placeholder="请输入API密钥"></el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveConfig">保存配置</el-button>
            </el-form-item>
          </el-form>
        </div>
        
        <div v-if="activeMenu === '3'" class="content-section">
          <h2>审计日志</h2>
          <el-table :data="auditLogs" style="width: 100%;">
            <el-table-column prop="username" label="用户名" width="150"></el-table-column>
            <el-table-column prop="operation" label="操作" width="200"></el-table-column>
            <el-table-column prop="targetType" label="目标类型" width="150"></el-table-column>
            <el-table-column prop="createdAt" label="时间" width="200"></el-table-column>
            <el-table-column prop="details" label="详情"></el-table-column>
          </el-table>
        </div>
      </el-main>
    </el-container>
    
    <!-- 创建用户对话框 -->
    <el-dialog v-model="showCreateUserDialog" title="新增用户" width="500px">
      <el-form :model="newUserForm" :rules="userRules" ref="userFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="newUserForm.username" placeholder="请输入用户名"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="newUserForm.password" type="password" placeholder="请输入密码"></el-input>
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="newUserForm.role" placeholder="请选择角色">
            <el-option label="管理员" value="ADMIN"></el-option>
            <el-option label="研发" value="DEVELOPER"></el-option>
            <el-option label="业务" value="BUSINESS"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="newUserForm.enabled" active-text="启用" inactive-text="禁用"></el-switch>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateUserDialog = false">取消</el-button>
        <el-button type="primary" @click="createUser">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const activeMenu = ref('1')
const showCreateUserDialog = ref(false)

// 用户数据
const users = ref([])
const newUserForm = reactive({
  username: '',
  password: '',
  role: 'DEVELOPER',
  enabled: true
})

// 系统配置
const configForm = reactive({
  apiKey: ''
})

// 审计日志
const auditLogs = ref([])

// 表单验证规则
const userRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

// 菜单选择处理
const handleMenuSelect = (index) => {
  activeMenu.value = index
}

// 切换用户状态
const toggleUserStatus = (user) => {
  user.enabled = !user.enabled
  // 这里应该调用API更新用户状态
}

// 创建用户
const createUser = async () => {
  try {
    await axios.post('/api/users', {
      username: newUserForm.username,
      password: newUserForm.password,
      role: newUserForm.role,
      enabled: newUserForm.enabled
    })
    ElMessage.success('用户创建成功')
    showCreateUserDialog.value = false
    // 重新加载用户列表
    loadUsers()
  } catch (error) {
    console.error('创建用户失败:', error)
    ElMessage.error('创建用户失败')
  }
}

// 加载用户列表
const loadUsers = async () => {
  try {
    // 模拟数据
    users.value = [
      { id: 1, username: 'admin_user', role: 'ADMIN', enabled: true },
      { id: 2, username: 'dev_user', role: 'DEVELOPER', enabled: true },
      { id: 3, username: 'bus_user', role: 'BUSINESS', enabled: true }
    ]
  } catch (error) {
    console.error('加载用户列表失败:', error)
  }
}

// 加载审计日志
const loadAuditLogs = async () => {
  try {
    // 模拟数据
    auditLogs.value = [
      { username: 'admin_user', operation: '登录系统', targetType: 'AUTH', createdAt: '2026-04-12 10:00:00', details: '用户登录' },
      { username: 'dev_user', operation: '创建PSU', targetType: 'PSU', createdAt: '2026-04-12 09:30:00', details: '创建新的PSU单元' }
    ]
  } catch (error) {
    console.error('加载审计日志失败:', error)
  }
}

// 保存配置
const saveConfig = async () => {
  try {
    // 这里应该调用API保存配置
    ElMessage.success('配置保存成功')
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存配置失败')
  }
}

onMounted(() => {
  loadUsers()
  loadAuditLogs()
})
</script>

<style scoped>
.admin-dashboard {
  height: 100vh;
}

.sidebar {
  background-color: #545c64;
  color: #fff;
  height: 100vh;
  position: fixed;
  top: 0;
  left: 0;
}

.menu {
  border-right: none;
}

.main-content {
  margin-left: 200px;
  padding: 20px;
}

.content-section {
  padding: 20px;
}

h2 {
  margin-bottom: 20px;
}
</style>