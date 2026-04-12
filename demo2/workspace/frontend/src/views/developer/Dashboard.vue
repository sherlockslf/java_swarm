<template>
  <div class="developer-dashboard">
    <el-container>
      <el-aside width="200px" class="sidebar">
        <el-menu default-active="1" class="menu" @select="handleMenuSelect">
          <el-menu-item index="1">
            <span>PSU管理</span>
          </el-menu-item>
          <el-menu-item index="2">
            <span>Schema编辑器</span>
          </el-menu-item>
          <el-menu-item index="3">
            <span>Prompt管理</span>
          </el-menu-item>
          <el-menu-item index="4">
            <span>版本审核</span>
          </el-menu-item>
          <el-menu-item index="5">
            <span>代码生成</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main-content">
        <!-- PSU管理 -->
        <div v-if="activeMenu === '1'" class="content-section">
          <div class="header-section">
            <h2>PSU管理</h2>
            <el-button type="primary" @click="showCreatePsuDialog = true">新建PSU</el-button>
          </div>
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
                <el-button size="small" @click="editPsu(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="archivePsu(row)">归档</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <!-- Schema编辑器 -->
        <div v-if="activeMenu === '2'" class="content-section">
          <h2>JSON Schema编辑器</h2>
          <el-form :model="schemaForm" label-width="120px">
            <el-form-item label="选择PSU">
              <el-select v-model="selectedPsuId" placeholder="请选择PSU" @change="loadSchema">
                <el-option 
                  v-for="psu in psus" 
                  :key="psu.id" 
                  :label="psu.name" 
                  :value="psu.id">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="Schema内容">
              <el-input 
                v-model="schemaForm.schemaContent" 
                type="textarea" 
                :rows="15" 
                placeholder="请输入JSON Schema内容">
              </el-input>
            </el-form-item>
            <el-form-item label="变更日志">
              <el-input 
                v-model="schemaForm.changeLog" 
                type="textarea" 
                :rows="3" 
                placeholder="请输入变更日志">
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSchema">保存Schema</el-button>
              <el-button @click="loadSchema">加载最新版本</el-button>
            </el-form-item>
          </el-form>
        </div>
        
        <!-- Prompt管理 -->
        <div v-if="activeMenu === '3'" class="content-section">
          <h2>Prompt片段管理</h2>
          <el-form :model="promptForm" label-width="120px">
            <el-form-item label="选择PSU">
              <el-select v-model="selectedPromptPsuId" placeholder="请选择PSU" @change="loadPromptFragments">
                <el-option 
                  v-for="psu in psus" 
                  :key="psu.id" 
                  :label="psu.name" 
                  :value="psu.id">
                </el-option>
              </el-select>
            </el-form-item>
            <el-table :data="promptFragments" style="width: 100%; margin-top: 20px;">
              <el-table-column prop="fragmentKey" label="片段标识" width="200"></el-table-column>
              <el-table-column prop="type" label="类型" width="150">
                <template #default="{ row }">
                  <el-tag :type="row.type === 'CORE_RULES' ? 'danger' : 'primary'">
                    {{ row.type === 'CORE_RULES' ? '核心规则' : '消息模板' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="editable" label="可编辑" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.editable ? 'success' : 'info'">
                    {{ row.editable ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="content" label="内容"></el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" @click="editPromptFragment(row)" :disabled="!row.editable">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-form>
        </div>
        
        <!-- 版本审核 -->
        <div v-if="activeMenu === '4'" class="content-section">
          <h2>版本审核</h2>
          <el-table :data="versionReviews" style="width: 100%;">
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
            <el-table-column prop="submitterId" label="提交者" width="120"></el-table-column>
            <el-table-column prop="submittedAt" label="提交时间" width="180"></el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="approveVersion(row)" :disabled="row.status !== 'PENDING'">通过</el-button>
                <el-button size="small" type="danger" @click="rejectVersion(row)" :disabled="row.status !== 'PENDING'">拒绝</el-button>
                <el-button size="small" @click="viewCode(row)" v-if="row.codeContent">查看代码</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <!-- 代码生成 -->
        <div v-if="activeMenu === '5'" class="content-section">
          <h2>代码生成</h2>
          <el-form :model="codeGenForm" label-width="120px">
            <el-form-item label="选择PSU">
              <el-select v-model="selectedCodeGenPsuId" placeholder="请选择PSU">
                <el-option 
                  v-for="psu in psus" 
                  :key="psu.id" 
                  :label="psu.name" 
                  :value="psu.id">
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="generateCode">生成代码</el-button>
              <el-button @click="downloadCode">下载代码</el-button>
            </el-form-item>
          </el-form>
          <div v-if="generatedCode" class="code-preview">
            <h3>生成的代码预览</h3>
            <pre>{{ generatedCode }}</pre>
          </div>
        </div>
      </el-main>
    </el-container>
    
    <!-- 创建PSU对话框 -->
    <el-dialog v-model="showCreatePsuDialog" title="新建PSU" width="600px">
      <el-form :model="newPsuForm" :rules="psuRules" ref="psuFormRef" label-width="100px">
        <el-form-item label="PSU ID" prop="psuId">
          <el-input v-model="newPsuForm.psuId" placeholder="请输入唯一的PSU ID"></el-input>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="newPsuForm.name" placeholder="请输入PSU名称"></el-input>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="newPsuForm.description" type="textarea" :rows="3" placeholder="请输入PSU描述"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreatePsuDialog = false">取消</el-button>
        <el-button type="primary" @click="createPsu">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 编辑Prompt片段对话框 -->
    <el-dialog v-model="showEditPromptDialog" title="编辑Prompt片段" width="700px">
      <el-form :model="editingPromptFragment" label-width="100px">
        <el-form-item label="片段标识">
          <el-input v-model="editingPromptFragment.fragmentKey" disabled></el-input>
        </el-form-item>
        <el-form-item label="内容">
          <el-input 
            v-model="editingPromptFragment.content" 
            type="textarea" 
            :rows="8" 
            placeholder="请输入Prompt内容">
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditPromptDialog = false">取消</el-button>
        <el-button type="primary" @click="savePromptFragment">保存</el-button>
      </template>
    </el-dialog>
    
    <!-- 查看代码对话框 -->
    <el-dialog v-model="showCodeDialog" title="生成的代码" width="80%" top="5vh">
      <pre class="code-display">{{ viewingCode }}</pre>
      <template #footer>
        <el-button @click="showCodeDialog = false">关闭</el-button>
        <el-button type="primary" @click="copyCode">复制代码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const activeMenu = ref('1')
const showCreatePsuDialog = ref(false)
const showEditPromptDialog = ref(false)
const showCodeDialog = ref(false)

// PSU数据
const psus = ref([])
const newPsuForm = reactive({
  psuId: '',
  name: '',
  description: ''
})

// Schema数据
const selectedPsuId = ref('')
const schemaForm = reactive({
  schemaContent: '',
  changeLog: ''
})

// Prompt数据
const selectedPromptPsuId = ref('')
const promptFragments = ref([])
const editingPromptFragment = ref({})

// 版本审核数据
const versionReviews = ref([])

// 代码生成数据
const selectedCodeGenPsuId = ref('')
const generatedCode = ref('')
const viewingCode = ref('')

// 表单验证规则
const psuRules = {
  psuId: [
    { required: true, message: '请输入PSU ID', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入PSU名称', trigger: 'blur' }
  ]
}

// 菜单选择处理
const handleMenuSelect = (index) => {
  activeMenu.value = index
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

// 创建PSU
const createPsu = async () => {
  try {
    await axios.post('/api/psus', {
      psuId: newPsuForm.psuId,
      name: newPsuForm.name,
      description: newPsuForm.description
    })
    ElMessage.success('PSU创建成功')
    showCreatePsuDialog.value = false
    // 重新加载PSU列表
    loadPsus()
  } catch (error) {
    console.error('创建PSU失败:', error)
    ElMessage.error('创建PSU失败')
  }
}

// 编辑PSU
const editPsu = (psu) => {
  // 实现编辑逻辑
  console.log('编辑PSU:', psu)
}

// 归档PSU
const archivePsu = (psu) => {
  // 实现归档逻辑
  console.log('归档PSU:', psu)
}

// 保存Schema
const saveSchema = async () => {
  try {
    await axios.put(`/api/schemas/${selectedPsuId.value}`, {
      schemaContent: schemaForm.schemaContent,
      changeLog: schemaForm.changeLog
    })
    ElMessage.success('Schema保存成功')
  } catch (error) {
    console.error('保存Schema失败:', error)
    ElMessage.error('保存Schema失败')
  }
}

// 加载Schema
const loadSchema = async () => {
  if (!selectedPsuId.value) return
  
  try {
    const response = await axios.get(`/api/schemas/${selectedPsuId.value}`)
    schemaForm.schemaContent = response.data.schemaContent
    schemaForm.changeLog = response.data.changeLog || ''
  } catch (error) {
    console.error('加载Schema失败:', error)
    schemaForm.schemaContent = '{}'
    schemaForm.changeLog = ''
  }
}

// 加载Prompt片段
const loadPromptFragments = async () => {
  if (!selectedPromptPsuId.value) return
  
  try {
    const response = await axios.get(`/api/prompts/${selectedPromptPsuId.value}`)
    promptFragments.value = response.data
  } catch (error) {
    console.error('加载Prompt片段失败:', error)
    promptFragments.value = []
  }
}

// 编辑Prompt片段
const editPromptFragment = (fragment) => {
  editingPromptFragment.value = { ...fragment }
  showEditPromptDialog.value = true
}

// 保存Prompt片段
const savePromptFragment = async () => {
  try {
    await axios.put(`/api/prompts/${editingPromptFragment.value.id}`, {
      content: editingPromptFragment.value.content
    })
    ElMessage.success('Prompt片段保存成功')
    showEditPromptDialog.value = false
    // 重新加载Prompt片段
    loadPromptFragments()
  } catch (error) {
    console.error('保存Prompt片段失败:', error)
    ElMessage.error('保存Prompt片段失败')
  }
}

// 通过版本
const approveVersion = async (review) => {
  try {
    await axios.post(`/api/versions/${review.id}/review`, {
      approved: true
    })
    ElMessage.success('版本审核通过')
    // 重新加载版本审核列表
    loadVersionReviews()
  } catch (error) {
    console.error('审核版本失败:', error)
    ElMessage.error('审核版本失败')
  }
}

// 拒绝版本
const rejectVersion = async (review) => {
  try {
    await axios.post(`/api/versions/${review.id}/review`, {
      approved: false,
      rejectionReason: '不符合要求'
    })
    ElMessage.success('版本审核已拒绝')
    // 重新加载版本审核列表
    loadVersionReviews()
  } catch (error) {
    console.error('审核版本失败:', error)
    ElMessage.error('审核版本失败')
  }
}

// 查看代码
const viewCode = async (review) => {
  try {
    const response = await axios.get(`/api/versions/${review.psuId}/code`)
    viewingCode.value = response.data
    showCodeDialog.value = true
  } catch (error) {
    console.error('获取代码失败:', error)
    ElMessage.error('获取代码失败')
  }
}

// 生成代码
const generateCode = async () => {
  if (!selectedCodeGenPsuId.value) {
    ElMessage.warning('请选择PSU')
    return
  }
  
  try {
    const response = await axios.get(`/api/versions/${selectedCodeGenPsuId.value}/code`)
    generatedCode.value = response.data
    ElMessage.success('代码生成成功')
  } catch (error) {
    console.error('生成代码失败:', error)
    ElMessage.error('生成代码失败')
  }
}

// 下载代码
const downloadCode = () => {
  if (!generatedCode.value) {
    ElMessage.warning('请先生成代码')
    return
  }
  
  const blob = new Blob([generatedCode.value], { type: 'text/plain' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'generated-code.java'
  a.click()
  window.URL.revokeObjectURL(url)
}

// 复制代码
const copyCode = () => {
  navigator.clipboard.writeText(viewingCode.value)
  ElMessage.success('代码已复制到剪贴板')
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

// 加载版本审核列表
const loadVersionReviews = async () => {
  try {
    // 模拟数据
    versionReviews.value = [
      { id: 1, psuId: 1, majorVersion: 1, minorVersion: 0, patchVersion: 0, status: 'PENDING', submitterId: 3, submittedAt: '2026-04-12 10:30:00' },
      { id: 2, psuId: 2, majorVersion: 1, minorVersion: 1, patchVersion: 0, status: 'APPROVED', submitterId: 3, submittedAt: '2026-04-12 09:30:00' }
    ]
  } catch (error) {
    console.error('加载版本审核列表失败:', error)
  }
}

onMounted(() => {
  loadPsus()
  loadVersionReviews()
})
</script>

<style scoped>
.developer-dashboard {
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

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.code-preview {
  margin-top: 20px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px;
}

.code-display {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 60vh;
  overflow-y: auto;
}

h2 {
  margin-bottom: 20px;
}
</style>