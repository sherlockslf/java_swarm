<template>
  <div class="prompt-composer">
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button @click="goBack" icon="ArrowLeft">返回</el-button>
          <span class="title">Prompt Composer - {{ psuName }}</span>
        </div>
        <div class="header-right">
          <el-tag :type="statusTagType" size="large">{{ statusText }}</el-tag>
          <span v-if="updatedAt" class="update-time">最后更新: {{ formatTime(updatedAt) }}</span>
        </div>
      </el-header>

      <el-container class="main-container">
        <el-aside width="280px" class="left-panel">
          <SchemaVariablePanel
            :schema-fields="schemaFields"
            :search-query="varSearch"
            @insert-variable="insertVariable"
            @update:search-query="varSearch = $event"
          />
          <div class="dataset-panel-left">
            <h4>测试数据集</h4>
            <el-select
              v-model="selectedDatasetId"
              filterable
              clearable
              style="width: 100%"
              placeholder="请选择测试数据集"
              :loading="loadingDatasets"
              @change="handleDatasetSelectionChange"
            >
              <el-option
                v-for="ds in datasets"
                :key="ds.id"
                :label="ds.name"
                :value="ds.id"
              />
            </el-select>
            <div v-if="selectedDatasetId" class="dataset-preview-list">
              <el-card
                v-for="ds in datasets.filter(item => item.id === selectedDatasetId)"
                :key="`preview-${ds.id}`"
                class="dataset-preview-card"
                shadow="never"
              >
                <template #header>
                  <div class="dataset-card-header">
                    <span>{{ ds.name }}</span>
                    <el-button text size="small" @click="toggleDatasetPreview">
                      {{ datasetPreviewExpanded ? '收起测试数据' : '展开测试数据' }}
                    </el-button>
                  </div>
                </template>
                <pre v-if="datasetPreviewExpanded" class="dataset-json">{{ formatJsonText(ds.dataContent) }}</pre>
                <div v-else class="dataset-collapsed-tip">测试数据已收起，点击“展开测试数据”查看全文</div>
              </el-card>
            </div>
            <el-divider content-position="left">模型测试</el-divider>
            <el-select v-model="selectedModel" style="width: 100%" placeholder="请选择模型">
              <el-option label="qwen-plus" value="qwen-plus" />
              <el-option label="qwen3-max" value="qwen3-max" />
              <el-option label="qwen3.6-plus" value="qwen3.6-plus" />
            </el-select>
            <el-button
              style="margin-top: 8px"
              type="success"
              :loading="testingModel"
              @click="runDashscopeTest"
            >使用阿里云模型测试</el-button>
          </div>
        </el-aside>

        <el-main class="center-panel">
          <PromptTextEditor
            v-model:content="content"
            :readonly="isReadonly"
            @update:content="onContentChange"
            ref="editorRef"
          />
          <div class="preview-panel">
            <h4>渲染预览</h4>
            <el-button
              @click="runRenderPreview"
              :disabled="isReadonly"
              :loading="rendering"
            >渲染预览</el-button>
            <el-alert
              v-if="missingVars.length > 0"
              style="margin-top: 8px"
              type="error"
              :closable="false"
              :title="`缺失变量: ${missingVars.join(', ')}`"
            />
            <el-input
              v-model="renderedPrompt"
              style="margin-top: 8px"
              type="textarea"
              :rows="6"
              placeholder="渲染后的Prompt会展示在这里，可直接编辑后做模型测试"
            />
          </div>
          <div class="test-result-panel">
            <h4>测试对话结果</h4>
            <el-input
              v-model="modelTestOutput"
              type="textarea"
              :rows="8"
              readonly
              placeholder="模型测试结果将显示在这里"
            />
            <el-alert
              v-if="modelTestError"
              style="margin-top: 8px"
              type="error"
              :closable="false"
              :title="modelTestError"
            />
          </div>
        </el-main>

        <el-aside width="320px" class="right-panel">
          <InjectionSummaryPanel
            :tokens="tokens"
            :injection-plan="injectionPlan"
            :validation-errors="validationErrors"
            :validation-warnings="validationWarnings"
            :schema-fields="schemaFields"
          />

          <div class="action-bar">
            <el-button
              type="primary"
              @click="saveDraft"
              :disabled="isReadonly"
              :loading="saving"
            >保存草稿</el-button>
            <el-button
              @click="runValidate"
              :disabled="isReadonly"
              :loading="validating"
            >校验</el-button>
            <el-button
              type="warning"
              @click="handleSubmit"
              :disabled="isReadonly"
              :loading="submitting"
            >提交审核</el-button>
          </div>
        </el-aside>
      </el-container>
    </el-container>

    <el-dialog v-model="showLeaveDialog" title="未保存提示" width="400px">
      <p>您有未保存的更改，确定要离开吗？</p>
      <template #footer>
        <el-button @click="showLeaveDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmLeave">确定离开</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { compositionApi, schemaApi, psuApi, testDatasetApi, configApi } from '@/services/api'
import SchemaVariablePanel from '@/components/composer/SchemaVariablePanel.vue'
import PromptTextEditor from '@/components/composer/PromptTextEditor.vue'
import InjectionSummaryPanel from '@/components/composer/InjectionSummaryPanel.vue'

const route = useRoute()
const router = useRouter()
const psuId = computed(() => Number(route.params.psuId))

const content = ref('')
const compositionId = ref(null)
const tokens = ref([])
const injectionPlan = ref([])
const schemaFields = ref([])
const psuName = ref('')
const status = ref('DRAFT')
const updatedAt = ref('')
const saving = ref(false)
const validating = ref(false)
const rendering = ref(false)
const submitting = ref(false)
const testingModel = ref(false)
const varSearch = ref('')
const showLeaveDialog = ref(false)
const hasUnsavedChanges = ref(false)
const validationErrors = ref([])
const validationWarnings = ref([])
const renderedPrompt = ref('')
const missingVars = ref([])
const datasets = ref([])
const loadingDatasets = ref(false)
const selectedDatasetId = ref(null)
const selectedDatasetInput = ref({})
const datasetPreviewExpanded = ref(false)
const selectedModel = ref('qwen-plus')
const dashscopeApiKey = ref('')
const modelTestOutput = ref('')
const modelTestError = ref('')
const editorRef = ref(null)

const isReadonly = computed(() => status.value !== 'DRAFT')

const statusTagType = computed(() => {
  const map = { DRAFT: 'success', SUBMITTED: 'warning', DEV_REVIEWING: 'info', APPROVED: 'success', REJECTED: 'danger' }
  return map[status.value] || 'info'
})

const statusText = computed(() => {
  const map = { DRAFT: '草稿', SUBMITTED: '已提交', DEV_REVIEWING: '审核中', APPROVED: '已通过', REJECTED: '已驳回' }
  return map[status.value] || status.value
})

onMounted(async () => {
  // 页面初始化时一次性加载编排、Schema与测试数据集
  await loadPsuName()
  await loadComposition()
  await loadSchema()
  await loadTestDatasets()
  await loadDashscopeApiKey(false)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

watch(content, () => {
  parseTokens()
  hasUnsavedChanges.value = true
})

const handleBeforeUnload = (e) => {
  if (hasUnsavedChanges.value && !isReadonly.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

window.addEventListener('beforeunload', handleBeforeUnload)

async function loadPsuName() {
  try {
    const res = await psuApi.getPsus(1, 100)
    const psu = res.data.content.find(p => p.id === psuId.value)
    if (psu) psuName.value = psu.name
  } catch (e) {
    console.error('Failed to load PSU name', e)
  }
}

async function loadComposition() {
  try {
    const res = await compositionApi.getComposition(psuId.value)
    if (res.data && res.data.content !== undefined) {
      compositionId.value = res.data.id
      content.value = res.data.content || ''
      status.value = res.data.status
      updatedAt.value = res.data.updatedAt
      if (res.data.specJson) {
        try {
          const spec = typeof res.data.specJson === 'string' ? JSON.parse(res.data.specJson) : res.data.specJson
          if (spec.tokens) tokens.value = spec.tokens
          if (spec.injectionPlan) injectionPlan.value = spec.injectionPlan
        } catch (e) {
          console.error('Failed to parse specJson', e)
        }
      }
    }
  } catch (e) {
    if (e.response && e.response.status !== 404) {
      ElMessage.error('加载编排草稿失败')
    }
  }
  parseTokens()
}

async function loadSchema() {
  try {
    const res = await schemaApi.getSchema(psuId.value)
    let schemaContent = res.data.schemaContent || '{}'
    if (typeof schemaContent === 'string') {
      schemaContent = JSON.parse(schemaContent)
    }
    schemaFields.value = parseSchemaFields(schemaContent)
  } catch (e) {
    console.error('加载Schema失败:', e)
    schemaFields.value = []
  }
}

async function loadTestDatasets() {
  // 加载当前PSU的测试数据集供多选调试
  loadingDatasets.value = true
  try {
    const res = await testDatasetApi.getTestDatasets(psuId.value)
    datasets.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    console.error('加载测试数据集失败:', e)
    datasets.value = []
  } finally {
    loadingDatasets.value = false
  }
}

async function loadDashscopeApiKey(showError = true) {
  // 从后端读取任意一个配置中的DashScope API Key
  try {
    const res = await configApi.getDashscopeKey()
    dashscopeApiKey.value = (res.data?.apiKey || '').trim()
  } catch (e) {
    dashscopeApiKey.value = ''
    if (showError) {
      ElMessage.error('获取DashScope API Key失败，请联系管理员检查后端配置')
    }
  }
}

function parseSchemaFields(schema) {
  const fields = []

  const walk = (node, parentPath = '', requiredSet = new Set()) => {
    if (!node || typeof node !== 'object') return
    if (node.properties && typeof node.properties === 'object') {
      const localRequired = new Set(node.required || [])
      for (const [name, prop] of Object.entries(node.properties)) {
        const currentPath = parentPath ? `${parentPath}.${name}` : name
        fields.push({
          name: currentPath,
          type: prop?.type || 'string',
          required: localRequired.has(name) || requiredSet.has(currentPath),
          description: prop?.description || ''
        })
        if (prop?.type === 'object' || prop?.properties) {
          walk(prop, currentPath)
        } else if (prop?.type === 'array' && prop?.items) {
          const itemPath = `${currentPath}[*]`
          fields.push({
            name: itemPath,
            type: 'array',
            required: localRequired.has(name),
            description: prop?.description || ''
          })
          if (prop.items?.properties || prop.items?.type === 'object') {
            walk(prop.items, itemPath)
          }
        }
      }
      return
    }

    for (const [name, value] of Object.entries(node)) {
      const currentPath = parentPath ? `${parentPath}.${name}` : name
      fields.push({
        name: currentPath,
        type: inferType(value),
        required: false,
        description: ''
      })
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        walk(value, currentPath)
      }
    }
  }

  walk(schema)
  return fields
}

function inferType(value) {
  if (value === null || value === undefined) return 'null'
  if (Array.isArray(value)) return 'array'
  const type = typeof value
  if (type === 'number') return 'number'
  if (type === 'boolean') return 'boolean'
  if (type === 'object') return 'object'
  return 'string'
}

function parseTokens() {
  if (!content.value) {
    tokens.value = []
    injectionPlan.value = []
    return
  }

  const VAR_PATTERN = /\{\{\s*([^}]+?)\s*\}\}/g
  const newTokens = []
  const planMap = new Map()
  let lastIndex = 0
  let match

  while ((match = VAR_PATTERN.exec(content.value)) !== null) {
    if (match.index > lastIndex) {
      newTokens.push({ type: 'TEXT', value: content.value.substring(lastIndex, match.index) })
    }
    const path = match[1].trim()
    newTokens.push({ type: 'VAR', path, source: 'INPUT_SCHEMA' })
    if (!planMap.has(path)) {
      planMap.set(path, { path, required: isFieldRequired(path), count: 0 })
    }
    planMap.get(path).count++
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < content.value.length) {
    newTokens.push({ type: 'TEXT', value: content.value.substring(lastIndex) })
  }

  tokens.value = newTokens
  injectionPlan.value = Array.from(planMap.values())
}

function isFieldRequired(path) {
  const field = schemaFields.value.find(f => f.name === path)
  return field ? field.required : false
}

function insertVariable(field) {
  if (isReadonly.value) return
  if (editorRef.value && typeof editorRef.value.insertAtCursor === 'function') {
    editorRef.value.insertAtCursor(`{{${field.name}}}`)
  } else {
    content.value += `{{${field.name}}}`
  }
}

function onContentChange(newContent) {
  content.value = newContent
}

async function saveDraft() {
  if (isReadonly.value) return
  saving.value = true
  try {
    const spec = {
      content: content.value,
      tokens: tokens.value,
      injectionPlan: injectionPlan.value,
      assembledFragments: []
    }
    await compositionApi.saveDraft(psuId.value, spec)
    ElMessage.success('草稿保存成功')
    hasUnsavedChanges.value = false
    const res = await compositionApi.getComposition(psuId.value)
    if (res.data) updatedAt.value = res.data.updatedAt
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.error || e.message))
  } finally {
    saving.value = false
  }
}

async function runValidate() {
  validating.value = true
  validationErrors.value = []
  validationWarnings.value = []

  if (hasUnclosedBraces(content.value)) {
    validationErrors.value.push({ code: 'UNCLOSED_BRACES', message: '存在未闭合的 {{ 占位符' })
  }

  const emptyVars = content.value.match(/\{\{\s*\}\}/g)
  if (emptyVars) {
    validationErrors.value.push({ code: 'EMPTY_VAR', message: '存在空变量: {{ }}' })
  }

  try {
    const spec = {
      content: content.value,
      tokens: tokens.value,
      injectionPlan: injectionPlan.value,
      assembledFragments: []
    }
    const res = await compositionApi.validate(psuId.value, spec)
    if (res.data.errors) validationErrors.value.push(...res.data.errors)
    if (res.data.warnings) validationWarnings.value.push(...res.data.warnings)

    if (res.data.ok) {
      ElMessage.success('校验通过')
    } else {
      ElMessage.warning(`校验失败: ${res.data.errors.length} 个错误`)
    }
  } catch (e) {
    ElMessage.error('校验请求失败')
  } finally {
    validating.value = false
  }
}

async function runRenderPreview() {
  rendering.value = true
  missingVars.value = []
  renderedPrompt.value = ''
  try {
    // 渲染仅使用左侧选中的测试数据集入参。
    let input = cloneObject(selectedDatasetInput.value)
    // 预览始终基于当前页面编辑中的文本，不依赖数据库中的草稿内容
    const preview = renderFromCurrentContent(content.value, input)
    renderedPrompt.value = preview.renderedPrompt
    missingVars.value = preview.missingVars
  } catch (e) {
    ElMessage.error('渲染预览失败')
  } finally {
    rendering.value = false
  }
}

function getValueByPath(source, rawPath) {
  // 支持 a.b.c 与数组下标路径读取（如 items[0].name）
  if (!source || !rawPath) return undefined
  const normalizedPath = String(rawPath).replace(/\[\*\]/g, '.0')
  const parts = normalizedPath
    .replace(/\[(\d+)\]/g, '.$1')
    .split('.')
    .filter(Boolean)
  let current = source
  for (const part of parts) {
    if (current === null || current === undefined) return undefined
    current = current[part]
  }
  return current
}

function renderFromCurrentContent(templateContent, input) {
  // 基于当前编辑内容执行变量替换，并返回缺失变量列表
  const missingSet = new Set()
  const renderedPrompt = (templateContent || '').replace(/\{\{\s*([^}]+?)\s*\}\}/g, (_, rawPath) => {
    const path = String(rawPath).trim()
    const value = getValueByPath(input, path)
    if (value === undefined || value === null) {
      missingSet.add(path)
      return ''
    }
    if (typeof value === 'object') {
      return JSON.stringify(value)
    }
    return String(value)
  })
  return {
    renderedPrompt,
    missingVars: Array.from(missingSet)
  }
}

function parseDatasetInput(dataset) {
  // 解析测试数据JSON并统一返回对象
  if (!dataset || !dataset.dataContent) return {}
  try {
    return typeof dataset.dataContent === 'string' ? JSON.parse(dataset.dataContent) : dataset.dataContent
  } catch {
    throw new Error(`测试数据集 ${dataset.name} JSON格式错误`)
  }
}

function applyDatasetToPreview() {
  // 将选中的测试数据集载入内部入参，不在中间预览区重复展示全文。
  if (!selectedDatasetId.value) {
    selectedDatasetInput.value = {}
    return
  }
  const dataset = datasets.value.find(ds => ds.id === selectedDatasetId.value)
  if (!dataset) {
    selectedDatasetInput.value = {}
    return
  }
  try {
    selectedDatasetInput.value = parseDatasetInput(dataset)
    datasetPreviewExpanded.value = false
  } catch (e) {
    selectedDatasetInput.value = {}
    ElMessage.error(e.message)
  }
}

async function handleDatasetSelectionChange() {
  // 选择测试集后自动载入数据并触发当前页面提示词渲染预览
  if (!selectedDatasetId.value) {
    selectedDatasetInput.value = {}
    renderedPrompt.value = ''
    missingVars.value = []
    return
  }
  applyDatasetToPreview()
  await runRenderPreview()
}

function toggleDatasetPreview() {
  // 左侧测试数据全文支持展开/收起，减少长文本干扰。
  datasetPreviewExpanded.value = !datasetPreviewExpanded.value
}

function cloneObject(value) {
  // 通过JSON序列化做安全拷贝，避免渲染阶段污染源对象。
  try {
    return JSON.parse(JSON.stringify(value || {}))
  } catch {
    return {}
  }
}

async function runDashscopeTest() {
  // 使用当前页面渲染结果调用阿里云兼容接口进行模型测试
  if (!selectedDatasetId.value) {
    ElMessage.warning('请先选择测试数据集')
    return
  }
  if (!renderedPrompt.value?.trim()) {
    await runRenderPreview()
  }
  if (!dashscopeApiKey.value) {
    await loadDashscopeApiKey(true)
  }
  const apiKey = dashscopeApiKey.value
  if (!apiKey) {
    ElMessage.error('缺少DashScope API Key，请联系管理员配置后端')
    return
  }
  testingModel.value = true
  modelTestOutput.value = ''
  modelTestError.value = ''
  try {
    const response = await fetch('https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        model: selectedModel.value,
        messages: [
          {
            role: 'user',
            content: renderedPrompt.value
          }
        ],
        stream: false,
        enable_thinking: false
      })
    })
    const data = await response.json()
    if (!response.ok) {
      throw new Error(data?.error?.message || data?.message || `请求失败: ${response.status}`)
    }
    const output = data?.choices?.[0]?.message?.content || ''
    modelTestOutput.value = output || JSON.stringify(data, null, 2)
    ElMessage.success('阿里云模型测试完成')
  } catch (e) {
    modelTestError.value = e.message || '模型测试失败'
    ElMessage.error(modelTestError.value)
  } finally {
    testingModel.value = false
  }
}

function formatJsonText(data) {
  // 格式化JSON文本用于页面预览
  if (!data) return '{}'
  try {
    const parsed = typeof data === 'string' ? JSON.parse(data) : data
    return JSON.stringify(parsed, null, 2)
  } catch {
    return String(data)
  }
}

function hasUnclosedBraces(text) {
  let i = 0
  while (i < text.length) {
    if (i + 1 < text.length && text[i] === '{' && text[i + 1] === '{') {
      const closeIdx = text.indexOf('}}', i + 2)
      if (closeIdx === -1) return true
      i = closeIdx + 2
    } else {
      i++
    }
  }
  return false
}

async function handleSubmit() {
  if (isReadonly.value) return

  try {
    await ElMessageBox.confirm('提交后将锁定编排，业务侧不可再修改，确认提交？', '确认提交', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  submitting.value = true
  try {
    await runValidate()
    if (validationErrors.value.length > 0) {
      ElMessage.error('请先修复校验错误后再提交')
      return
    }
    await compositionApi.submit(psuId.value)
    ElMessage.success('提交成功，编排已锁定')
    status.value = 'SUBMITTED'
    hasUnsavedChanges.value = false
    const res = await compositionApi.getComposition(psuId.value)
    if (res.data) {
      compositionId.value = res.data.id
      status.value = res.data.status
      updatedAt.value = res.data.updatedAt
    }
  } catch (e) {
    ElMessage.error('提交失败: ' + (e.response?.data?.error || e.message))
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (hasUnsavedChanges.value && !isReadonly.value) {
    showLeaveDialog.value = true
  } else {
    // 根据用户角色返回到相应页面
    const userRole = localStorage.getItem('userRole')
    if (userRole === 'DEVELOPER') {
      router.push('/developer')
    } else {
      router.push('/business')
    }
  }
}

function confirmLeave() {
  showLeaveDialog.value = false
  hasUnsavedChanges.value = false
  // 根据用户角色返回到相应页面
  const userRole = localStorage.getItem('userRole')
  if (userRole === 'DEVELOPER') {
    router.push('/developer')
  } else {
    router.push('/business')
  }
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.prompt-composer {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title {
  font-size: 18px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.update-time {
  font-size: 12px;
  color: #909399;
}

.main-container {
  flex: 1;
  overflow: hidden;
}

.left-panel {
  background: #fafafa;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  padding: 16px;
}

.dataset-panel-left {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
}

.dataset-panel-left h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
}

.dataset-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.dataset-collapsed-tip {
  padding: 8px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}

.center-panel {
  padding: 16px;
  overflow-y: auto;
}

.right-panel {
  background: #fafafa;
  border-left: 1px solid #e4e7ed;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.action-bar {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}

.preview-panel {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
}

.preview-panel h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
}

.test-result-panel {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
}

.test-result-panel h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
}

.dataset-preview-list,
.batch-result-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dataset-preview-card,
.batch-result-card {
  border: 1px solid #e4e7ed;
}

.dataset-json {
  margin: 0;
  padding: 8px;
  border-radius: 4px;
  background: #f5f7fa;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  max-height: 220px;
  overflow-y: auto;
}
</style>
