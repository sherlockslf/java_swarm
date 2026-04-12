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
          <el-table :data="psus" style="width: 100%; margin-top: 20px;">
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
            
            <el-form-item label="Schema字段台账">
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
                <el-input 
                  v-model="promptForm.promptContent" 
                  type="textarea" 
                  :rows="10" 
                  placeholder="请输入Prompt内容，可使用上方变量">
                </el-input>
                <div class="variable-panel">
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
            
            <el-form-item label="输入参数">
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
            <el-table :data="versionStatus" style="width: 100%;">
              <el-table-column prop="psuName" label="PSU名称" width="200"></el-table-column>
              <el-table-column prop="version" label="版本" width="150"></el-table-column>
              <el-table-column prop="status" label="状态" width="150">
                <template #default="{ row }">
                  <el-tag :type="getStatusTagType(row.status)">
                    {{ getStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="submitTime" label="提交时间" width="180"></el-table-column>
              <el-table-column prop="reviewTime" label="审核时间" width="180"></el-table-column>
              <el-table-column prop="reviewer" label="审核人" width="150"></el-table-column>
              <el-table-column prop="remark" label="备注"></el-table-column>
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
import axios from 'axios'
import { ElMessage } from 'element-plus'

const activeMenu = ref('1')
const showSchemaDialog = ref(false)

// PSU数据
const psus = ref([])
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

// 菜单选择处理
const handleMenuSelect = (index) => {
  activeMenu.value = index
}

// 选择PSU
const selectPsu = (psu) => {
  selectedPsuId.value = psu.id
  ElMessage.success(`已选择PSU: ${psu.name}`)
}

// 查看Schema
const viewSchema = (psu) => {
  // 模拟Schema字段
  viewingSchemaFields.value = [
    { name: 'productName', type: 'string', required: true, description: '商品名称' },
    { name: 'productCategory', type: 'string', required: true, description: '商品类别' },
    { name: 'productPrice', type: 'number', required: false, description: '商品价格' },
    { name: 'userQuery', type: 'string', required: true, description: '用户查询内容' }
  ]
  showSchemaDialog.value = true
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

// 加载Prompt片段
const loadPromptFragments = async () => {
  if (!selectedPsuId.value) return
  
  try {
    // 模拟加载Schema字段
    schemaFields.value = [
      { name: 'productName', type: 'string', required: true, description: '商品名称', editable: true },
      { name: 'productCategory', type: 'string', required: true, description: '商品类别', editable: true },
      { name: 'productPrice', type: 'number', required: false, description: '商品价格', editable: true },
      { name: 'userQuery', type: 'string', required: true, description: '用户查询内容', editable: true }
    ]
    
    // 模拟加载现有Prompt内容
    promptForm.promptContent = `基于以下商品信息：{{productName}}，类别：{{productCategory}}，价格：{{productPrice}}，回答用户问题：{{userQuery}}`
  } catch (error) {
    console.error('加载Prompt片段失败:', error)
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
  // 这里应该获取实际的光标位置，简化实现返回末尾
  return promptForm.promptContent.length
}

// 保存Prompt
const savePrompt = async () => {
  try {
    // 这里应该调用API保存Prompt
    ElMessage.success('Prompt保存成功')
  } catch (error) {
    console.error('保存Prompt失败:', error)
    ElMessage.error('保存Prompt失败')
  }
}

// 加载调试Schema
const loadDebugSchema = async () => {
  if (!selectedDebugPsuId.value) return
  
  try {
    // 模拟加载调试Schema字段
    debugSchemaFields.value = [
      { name: 'productName', type: 'string', required: true, editable: true },
      { name: 'productCategory', type: 'string', required: true, editable: true },
      { name: 'productPrice', type: 'number', required: false, editable: true },
      { name: 'userQuery', type: 'string', required: true, editable: true }
    ]
    
    // 重置调试表单
    debugSchemaFields.value.forEach(field => {
      debugForm[field.name] = ''
    })
  } catch (error) {
    console.error('加载调试Schema失败:', error)
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
    // 模拟测试请求
    setTimeout(() => {
      testResult.value = `测试结果：基于输入的商品"${debugForm.productName}"和用户查询"${debugForm.userQuery}"，返回了相关推荐信息。`
      testing.value = false
      ElMessage.success('测试完成')
    }, 1000)
  } catch (error) {
    console.error('测试Prompt失败:', error)
    ElMessage.error('测试失败')
    testing.value = false
  }
}

// 提交版本
const submitVersion = async () => {
  if (!selectedVersionPsuId.value) {
    ElMessage.warning('请选择PSU')
    return
  }
  
  if (!versionForm.description.trim()) {
    ElMessage.warning('请输入版本描述')
    return
  }
  
  submitting.value = true
  try {
    // 模拟提交版本请求
    await new Promise(resolve => setTimeout(resolve, 1000))
    ElMessage.success('版本提交成功，等待研发审核')
    submitting.value = false
    
    // 添加到版本状态列表
    const selectedPsu = psus.value.find(p => p.id === parseInt(selectedVersionPsuId.value))
    versionStatus.value.unshift({
      psuName: selectedPsu?.name || '未知',
      version: '1.0.0',
      status: 'PENDING',
      submitTime: new Date().toLocaleString(),
      reviewTime: '-',
      reviewer: '-',
      remark: versionForm.description
    })
    
    // 清空表单
    versionForm.description = ''
  } catch (error) {
    console.error('提交版本失败:', error)
    ElMessage.error('提交失败')
    submitting.value = false
  }
}

// 加载PSU列表
const loadPsus = async () => {
  try {
    // 模拟数据
    psus.value = [
      { id: 1, psuId: 'psu-001', name: '商品推荐PSU', description: '用于商品推荐的PSU单元', status: 'ACTIVE' },
      { id: 2, psuId: 'psu-002', name: '客服对话PSU', description: '用于客服对话的PSU单元', status: 'ACTIVE' }
    ]
  } catch (error) {
    console.error('加载PSU列表失败:', error)
  }
}

// 加载版本状态
const loadVersionStatus = async () => {
  try {
    // 模拟数据
    versionStatus.value = [
      { psuName: '商品推荐PSU', version: '1.0.0', status: 'APPROVED', submitTime: '2026-04-12 09:30:00', reviewTime: '2026-04-12 10:00:00', reviewer: 'dev_user', remark: '初始版本' }
    ]
  } catch (error) {
    console.error('加载版本状态失败:', error)
  }
}

onMounted(() => {
  loadPsus()
  loadVersionStatus()
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