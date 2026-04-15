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
        </el-aside>

        <el-main class="center-panel">
          <PromptTextEditor
            v-model:content="content"
            :readonly="isReadonly"
            @update:content="onContentChange"
            ref="editorRef"
          />
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
              type="success"
              @click="showTestDialog = true"
              :disabled="isReadonly"
            >运行测试</el-button>
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

    <el-dialog v-model="showTestDialog" title="运行测试集" width="800px">
      <TestDatasetRunner
        :psu-id="psuId"
        @close="showTestDialog = false"
      />
    </el-dialog>

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
import { compositionApi, schemaApi, psuApi } from '@/services/api'
import SchemaVariablePanel from '@/components/composer/SchemaVariablePanel.vue'
import PromptTextEditor from '@/components/composer/PromptTextEditor.vue'
import InjectionSummaryPanel from '@/components/composer/InjectionSummaryPanel.vue'
import TestDatasetRunner from '@/components/composer/TestDatasetRunner.vue'

const route = useRoute()
const router = useRouter()
const psuId = computed(() => Number(route.params.psuId))

const content = ref('')
const tokens = ref([])
const injectionPlan = ref([])
const schemaFields = ref([])
const psuName = ref('')
const status = ref('DRAFT')
const updatedAt = ref('')
const saving = ref(false)
const validating = ref(false)
const submitting = ref(false)
const varSearch = ref('')
const showTestDialog = ref(false)
const showLeaveDialog = ref(false)
const hasUnsavedChanges = ref(false)
const validationErrors = ref([])
const validationWarnings = ref([])
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
  await loadPsuName()
  await loadComposition()
  await loadSchema()
})

onBeforeUnmount(() => {
  if (hasUnsavedChanges.value && !isReadonly.value) {
    window.removeEventListener('beforeunload', handleBeforeUnload)
  }
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
    const schemaContent = JSON.parse(res.data.schemaContent || '{}')
    schemaFields.value = parseSchemaFields(schemaContent)
  } catch (e) {
    ElMessage.error('加载Schema失败')
  }
}

function parseSchemaFields(schema) {
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
  const rootField = path.split(/[.\[]/)[0]
  const field = schemaFields.value.find(f => f.name === rootField)
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
    await compositionApi.submit(psuId.value)
    ElMessage.success('提交成功，编排已锁定')
    status.value = 'SUBMITTED'
    hasUnsavedChanges.value = false
    const res = await compositionApi.getComposition(psuId.value)
    if (res.data) {
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
    router.push('/business')
  }
}

function confirmLeave() {
  showLeaveDialog.value = false
  hasUnsavedChanges.value = false
  router.push('/business')
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
</style>
