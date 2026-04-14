<template>
  <div class="business-dashboard">
    <el-container>
      <el-aside width="200px" class="sidebar">
        <el-menu default-active="1" class="menu" @select="handleMenuSelect">
          <el-menu-item index="1">
            <span>PSU列表</span>
          </el-menu-item>
          <el-menu-item index="2">
            <span>Prompt编排</span>
          </el-menu-item>
          <el-menu-item index="3">
            <span>在线调试</span>
          </el-menu-item>
          <el-menu-item index="4">
            <span>版本提交</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main-content">
        <!-- PSU列表 -->
        <div v-if="activeMenu === '1'" class="content-section">
          <h2>PSU列表</h2>
          <el-table :data="psus" style="width: 100%; margin-top: 20px;" v-loading="loadingPsus">
            <el-table-column prop="psuId" label="PSU ID" width="200"></el-table-column>
            <el-table-column prop="name" label="名称" width="200"></el-table-column>
            <el-table-column prop="description" label="描述"></el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
                  {{ row.status === 'ACTIVE' ? '活跃' : '归档' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="selectPsu(row)">选择</el-button>
                <el-button size="small" @click="viewSchema(row)">查看Schema</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="psuPagination.page"
            :page-size="psuPagination.size"
            :total="psuPagination.total"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
            style="margin-top: 20px; justify-content: flex-end;">
          </el-pagination>
        </div>
        
        <!-- Prompt编排 -->
        <div v-if="activeMenu === '2'" class="content-section">
          <h2>Prompt编排调试</h2>
          <el-form :model="promptForm" label-width="120px">
            <el-form-item label="选择PSU">
              <el-select v-model="selectedPsuId" placeholder="请选择PSU" @change="loadPromptFragments">
                <el-option 
                  v-for="psu in psus" 
                  :key="psu.id" 
                  :label="psu.name" 
                  :value="psu.id">
                </el-option>
              </el-select>
            </el-form-item>
            
            <el-form-item label="Schema字段台账" v-if="schemaFields.length > 0">
              <el-table :data="schemaFields" style="width: 100%; margin-top: 10px;">
                <el-table-column prop="name" label="字段名" width="200"></el-table-column>
                <el-table-column prop="type" label="类型" width="150"></el-table-column>
                <el-table-column prop="required" label="必填" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.required ? 'danger' : 'success'">
                      {{ row.required ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="描述"></el-table-column>
                <el-table-column label="操作" width="150">
                  <template #default="{ row }">
                    <el-button size="small" @click="insertVariable(row)">插入变量</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-form-item>
            
            <el-form-item label="Prompt内容">
              <div class="prompt-editor-container">
                <div class="prompt-editor-main">
                  <el-input 
                    v-model="promptForm.promptContent" 
                    type="textarea" 
                    :rows="10" 
                    placeholder="请输入Prompt内容，可使用上方变量"
                    :disabled="!currentPromptEditable">
                  </el-input>
                  <div class="prompt-status-bar">
                    <el-tag :type="currentPromptEditable ? 'success' : 'danger'" size="small">
                      {{ currentPromptEditable ? '可编辑' : '已定版' }}
                    </el-tag>
                    <el-button 
                      v-if="currentPromptEditable && isBusinessUser" 
                      size="small" 
                      type="warning" 
                      @click="finalizePrompt"
                      style="margin-left: 10px;">
                      定版
                    </el-button>
                  </div>
                </div>
                <div class="variable-panel" v-if="schemaFields.length > 0">
                  <h4>可用变量</h4>
                  <div class="variable-list">
                    <el-tag 
                      v-for="field in schemaFields" 
                      :key="field.name" 
                      class="variable-tag" 
                      @click="insertVariable(field)"
                      effect="dark">
                      {{ field.name }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="savePrompt">保存Prompt</el-button>
            </el-form-item>
          </el-form>
        </div>
        
        <!-- 在线调试 -->
        <div v-if="activeMenu === '3'" class="content-section">
          <h2>在线调试</h2>
          <el-form :model="debugForm" label-width="120px">
            <el-form-item label="选择PSU">
              <el-select v-model="selectedDebugPsuId" placeholder="请选择PSU" @change="loadDebugSchema">
                <el-option 
                  v-for="psu in psus" 
                  :key="psu.id" 
                  :label="psu.name" 
                  :value="psu.id">
                </el-option>
              </el-select>
            </el-form-item>
            
            <el-form-item label="输入参数" v-if="debugSchemaFields.length > 0">
              <div v-for="(field, index) in debugSchemaFields" :key="index" class="input-field">
                <label>{{ field.name }} ({{ field.type }}):</label>
                <el-input 
                  v-model="debugForm[field.name]" 
                  :placeholder="`请输入${field.name}`"
                  :disabled="!field.editable">
                </el-input>
              </div>
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="testPrompt" :loading="testing">测试Prompt</el-button>
            </el-form-item>
            
            <el-form-item label="测试结果">
              <el-input 
                v-model="testResult" 
                type="textarea" 
                :rows="8" 
                readonly 
                placeholder="测试结果将显示在这里...">
              </el-input>
            </el-form-item>
          </el-form>
        </div>
        
        <!-- 版本提交 -->
        <div v-if="activeMenu === '4'" class="content-section">
          <h2>版本提交与跟进</h2>
          <el-form :model="versionForm" label-width="120px">
            <el-form-item label="选择PSU">
              <el-select v-model="selectedVersionPsuId" placeholder="请选择PSU">
                <el-option 
                  v-for="psu in psus" 
                  :key="psu.id" 
                  :label="psu.name" 
                  :value="psu.id">
                </el-option>
              </el-select>
            </el-form-item>
            
            <el-form-item label="版本描述">
              <el-input 
                v-model="versionForm.description" 
                type="textarea" 
                :rows="4" 
                placeholder="请输入本次版本的改进内容和优化说明">
              </el-input>
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="submitVersion" :loading="submitting">提交审核</el-button>
            </el-form-item>
          </el-form>
          
          <div class="version-status">
            <h3>审核状态</h3>
            <el-table :data="versionStatus" style="width: 100%;" v-loading="loadingVersions">
              <el-table-column prop="psuId" label="PSU ID" width="150"></el-table-column>
              <el-table-column label="版本" width="150">
                <template #default="{ row }">
                  {{ row.majorVersion }}.{{ row.minorVersion }}.{{ row.patchVersion }}
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="getStatusTagType(row.status)">
                    {{ getStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="submittedAt" label="提交时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.submittedAt) }}
                </template>
              </el-table-column>
              <el-table-column prop="reviewedAt" label="审核时间" width="180">
                <template #default="{ row }">
                  {{ row.reviewedAt ? formatDateTime(row.reviewedAt) : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="rejectionReason" label="备注"></el-table-column>
            </el-table>
          </div>
        </div>
      </el-main>
    </el-container>
    
    <!-- Schema查看对话框 -->
    <el-dialog v-model="showSchemaDialog" title="Schema详情" width="70%">
      <el-table :data="viewingSchemaFields" style="width: 100%;">
        <el-table-column prop="name" label="字段名" width="200"></el-table-column>
        <el-table-column prop="type" label="类型" width="150"></el-table-column>
        <el-table-column prop="required" label="必填" width="100">
          <template #default="{ row }">
            <el-tag :type="row.required ? 'danger' : 'success'">
              {{ row.required ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述"></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showSchemaDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api, { psuApi, schemaApi, promptApi, versionApi } from '@/services/api'
import { ElMessage } from 'element-plus'

const activeMenu = ref('1')
const showSchemaDialog = ref(false)
const loadingPsus = ref(false)
const loadingVersions = ref(false)

// PSU数据
const psus = ref([])
const psuPagination = reactive({
  page: 1,
  size: 10,
  total: 0
})
const selectedPsuId = ref('')
const selectedDebugPsuId = ref('')
const selectedVersionPsuId = ref('')

// Schema相关
const schemaFields = ref([])
const viewingSchemaFields = ref([])
const promptForm = reactive({
  promptContent: ''
})

// 调试相关
const debugForm = reactive({})
const debugSchemaFields = ref([])
const testResult = ref('')
const testing = ref(false)

// 版本相关
const versionForm = reactive({
  description: ''
})
const versionStatus = ref([])

// 提交状态
const submitting = ref(false)

// Prompt编辑状态
const currentPromptEditable = ref(true)
const currentPromptFragmentId = ref(null)
const isBusinessUser = ref(false)

// 菜单选择处理
const handleMenuSelect = (index) => {
  activeMenu.value = index
}

// 分页切换
const handlePageChange = (page) => {
  psuPagination.page = page
  loadPsus()
}

// 选择PSU
const selectPsu = (psu) => {
  selectedPsuId.value = psu.id
  selectedDebugPsuId.value = psu.id
  selectedVersionPsuId.value = psu.id
  ElMessage.success(`已选择PSU: ${psu.name}`)
}

// 查看Schema
const viewSchema = async (psu) => {
  try {
    const response = await schemaApi.getSchema(psu.id)
    const schemaContent = JSON.parse(response.data.schemaContent || '{}')
    viewingSchemaFields.value = parseSchemaFields(schemaContent)
    showSchemaDialog.value = true
  } catch (error) {
    console.error('获取Schema失败:', error)
    ElMessage.error('获取Schema失败')
  }
}

// 解析Schema字段
const parseSchemaFields = (schema) => {
  const fields = []
  if (schema.properties) {
    for (const [name, prop] of Object.entries(schema.properties)) {
      fields.push({
        name,
        type: prop.type || 'string',
        required: (schema.required || []).includes(name),
        description: prop.description || ''
      })
    }
  }
  return fields
}

// 获取状态标签类型
const getStatusTagType = (status) => {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    default: return 'info'
  }
}

// 获取状态文本
const getStatusText = (status) => {
  switch (status) {
    case 'PENDING': return '待审核'
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已拒绝'
    default: return status
  }
}

// 格式化日期时间
const formatDateTime = (dateTime) => {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', { 
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit', 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit',
    hour12: false 
  })
}

// 加载Prompt片段
const loadPromptFragments = async () => {
  if (!selectedPsuId.value) return
  
  try {
    // 先加载Schema获取字段
    const schemaResponse = await schemaApi.getSchema(selectedPsuId.value)
    const schemaContent = JSON.parse(schemaResponse.data.schemaContent || '{}')
    schemaFields.value = parseSchemaFields(schemaContent)
    
    // 加载Prompt片段
    const promptResponse = await promptApi.getPromptFragments(selectedPsuId.value)
    if (promptResponse.data && promptResponse.data.length > 0) {
      const coreRules = promptResponse.data.find(p => p.fragmentKey === 'core_rules')
      if (coreRules) {
        promptForm.promptContent = coreRules.content
        currentPromptFragmentId.value = coreRules.id
        currentPromptEditable.value = coreRules.editable !== false
      }
    }
    
    // 检查当前用户角色
    const profileResponse = await api.get('/auth/profile')
    isBusinessUser.value = profileResponse.data.role === 'BUSINESS'
  } catch (error) {
    console.error('加载Prompt片段失败:', error)
    ElMessage.error('加载Prompt片段失败')
  }
}

// 插入变量
const insertVariable = (field) => {
  const cursorPosition = getCursorPosition()
  const textBefore = promptForm.promptContent.substring(0, cursorPosition)
  const textAfter = promptForm.promptContent.substring(cursorPosition)
  promptForm.promptContent = `${textBefore}{{${field.name}}}${textAfter}`
}

// 获取光标位置（简化实现）
const getCursorPosition = () => {
  return promptForm.promptContent.length
}

// 保存Prompt
const savePrompt = async () => {
  if (!selectedPsuId.value) {
    ElMessage.warning('请先选择PSU')
    return
  }
  
  if (!currentPromptEditable.value) {
    ElMessage.warning('Prompt已定版，不允许修改')
    return
  }
  
  try {
    await promptApi.updatePromptFragment(currentPromptFragmentId.value, {
      content: promptForm.promptContent
    })
    ElMessage.success('Prompt保存成功')
  } catch (error) {
    console.error('保存Prompt失败:', error)
    ElMessage.error('保存Prompt失败')
  }
}

// 定版Prompt
const finalizePrompt = async () => {
  if (!currentPromptFragmentId.value) {
    ElMessage.warning('请先加载Prompt片段')
    return
  }
  
  try {
    await promptApi.finalizePromptFragment(currentPromptFragmentId.value)
    ElMessage.success('Prompt已定版，所有人不可修改')
    currentPromptEditable.value = false
  } catch (error) {
    console.error('定版Prompt失败:', error)
    ElMessage.error('定版失败')
  }
}

// 加载调试Schema
const loadDebugSchema = async () => {
  if (!selectedDebugPsuId.value) return
  
  try {
    const response = await schemaApi.getSchema(selectedDebugPsuId.value)
    const schemaContent = JSON.parse(response.data.schemaContent || '{}')
    debugSchemaFields.value = parseSchemaFields(schemaContent).map(f => ({...f, editable: true}))
    
    // 重置调试表单
    debugSchemaFields.value.forEach(field => {
      debugForm[field.name] = ''
    })
    testResult.value = ''
  } catch (error) {
    console.error('加载调试Schema失败:', error)
    ElMessage.error('加载调试Schema失败')
  }
}

// 测试Prompt
const testPrompt = async () => {
  if (!selectedDebugPsuId.value) {
    ElMessage.warning('请选择PSU')
    return
  }
  
  testing.value = true
  try {
    const inputParams = {}
    debugSchemaFields.value.forEach(field => {
      inputParams[field.name] = debugForm[field.name] || ''
    })
    
    const response = await promptApi.testPrompt(selectedDebugPsuId.value, inputParams)
    testResult.value = response.data
    ElMessage.success('测试完成')
  } catch (error) {
    console.error('测试Prompt失败:', error)
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

// 提交版本
const submitVersion = async () => {
  if (!selectedVersionPsuId.value) {
    ElMessage.warning('请选择PSU')
    return
  }
  
  submitting.value = true
  try {
    await versionApi.submitVersion(selectedVersionPsuId.value)
    ElMessage.success('版本提交成功，等待研发审核')
    submitting.value = false
    versionForm.description = ''
    // 重新加载版本列表
    loadVersionStatus()
  } catch (error) {
    console.error('提交版本失败:', error)
    ElMessage.error('提交失败')
    submitting.value = false
  }
}

// 加载PSU列表
const loadPsus = async () => {
  loadingPsus.value = true
  try {
    const response = await psuApi.getPsus(psuPagination.page, psuPagination.size)
    psus.value = response.data.content
    psuPagination.total = response.data.totalElements
  } catch (error) {
    console.error('加载PSU列表失败:', error)
    ElMessage.error('加载PSU列表失败')
  } finally {
    loadingPsus.value = false
  }
}

// 加载版本状态
const loadVersionStatus = async () => {
  loadingVersions.value = true
  try {
    // 获取所有PSU的版本审核记录
    const allVersions = []
    for (const psu of psus.value) {
      try {
        const response = await api.get(`/versions/${psu.id}/code`)
        // 这里应该调用获取版本列表的API，暂时使用模拟
      } catch (e) {
        // 忽略单个PSU的错误
      }
    }
    versionStatus.value = allVersions
  } catch (error) {
    console.error('加载版本状态失败:', error)
  } finally {
    loadingVersions.value = false
  }
}

onMounted(() => {
  loadPsus()
})
</script>

<style scoped>
.business-dashboard {
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

.prompt-editor-container {
  display: flex;
  gap: 20px;
}

.variable-panel {
  width: 200px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px;
  background-color: #f5f7fa;
}

.variable-list {
  margin-top: 10px;
}

.variable-tag {
  margin: 5px;
  cursor: pointer;
}

.input-field {
  margin-bottom: 15px;
}

.input-field label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}

.version-status {
  margin-top: 30px;
}

h2, h3 {
  margin-bottom: 20px;
}
</style>
