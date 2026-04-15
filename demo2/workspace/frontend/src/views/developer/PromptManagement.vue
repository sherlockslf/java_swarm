<template>
  <div class="prompt-management">
    <el-card class="card-container">
      <template #header>
        <div class="card-header">
          <span class="title">Prompt管理</span>
          <el-button 
            type="primary" 
            @click="createNewPrompt" 
            :disabled="!selectedPsuId"
            :title="selectedPsuId ? '为选定的PSU添加新的Prompt片段' : '请先选择一个PSU'"
          >
            新建Prompt片段
          </el-button>
        </div>
      </template>

      <!-- PSU选择 -->
      <div class="psu-selection">
        <el-select v-model="selectedPsuId" placeholder="请选择PSU" @change="onPsuChange" style="width: 400px;">
          <el-option
            v-for="psu in psuList"
            :key="psu.id"
            :label="`${psu.name} (v${psu.majorVersion}.${psu.minorVersion}.${psu.patchVersion})`"
            :value="psu.id"
          />
        </el-select>
      </div>

      <!-- Prompt片段表格 -->
      <el-table
        v-if="selectedPsuId"
        :data="promptFragments"
        style="width: 100%; margin-top: 20px;"
        border
      >
        <el-table-column prop="fragmentKey" label="片段标识" width="150" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)">
              {{ formatTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column prop="editable" label="可编辑" width="100">
          <template #default="{ row }">
            <el-tag :type="row.editable ? 'success' : 'info'">
              {{ row.editable ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="editPrompt(row)">编辑</el-button>
            <el-button 
              size="small" 
              :disabled="!row.editable" 
              @click="finalizePrompt(row)"
            >
              定版
            </el-button>
            <el-button size="small" type="danger" @click="deletePrompt(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 新建/编辑弹窗 -->
      <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '编辑Prompt片段' : '新建Prompt片段'"
        width="60%"
      >
        <el-form :model="currentPrompt" label-width="100px">
          <el-form-item label="PSU ID" v-if="!isEdit">
            <el-input v-model="currentPrompt.psuId" :disabled="isEdit" />
          </el-form-item>
          <el-form-item label="片段标识">
            <el-input v-model="currentPrompt.fragmentKey" :disabled="isEdit" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="currentPrompt.type" placeholder="请选择类型">
              <el-option label="核心规则片段" value="CORE_RULES" />
              <el-option label="消息模板片段" value="MESSAGE_TEMPLATE" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容">
            <el-input
              v-model="currentPrompt.content"
              type="textarea"
              :rows="6"
              placeholder="请输入Prompt内容"
            />
          </el-form-item>
          <el-form-item label="可编辑">
            <el-switch v-model="currentPrompt.editable" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="currentPrompt.sortOrder" :min="0" />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="savePrompt">确定</el-button>
          </span>
        </template>
      </el-dialog>

      <!-- 测试区域 -->
      <el-card v-if="selectedPsuId" class="test-card" style="margin-top: 20px;">
        <template #header>
          <span>Prompt测试</span>
        </template>
        <el-row :gutter="20">
          <el-col :span="18">
            <el-input
              v-model="testInput"
              type="textarea"
              :rows="4"
              placeholder="请输入测试参数(JSON格式)"
            />
          </el-col>
          <el-col :span="6">
            <el-button type="primary" @click="testPrompt">测试</el-button>
          </el-col>
        </el-row>
        <div v-if="testResult" class="test-result">
          <h4>测试结果：</h4>
          <pre>{{ testResult }}</pre>
        </div>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, promptApi } from '@/services/api'

// 响应式数据
const selectedPsuId = ref(null)
const psuList = ref([])
const promptFragments = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentPrompt = ref({
  id: null,
  fragmentKey: '',
  content: '',
  editable: true,
  type: 'MESSAGE_TEMPLATE',
  sortOrder: 0
})
const testInput = ref('')
const testResult = ref('')

// 获取PSU列表
const loadPsuList = async () => {
  try {
    const response = await api.get('/api/psus')
    psuList.value = response.data.content || response.data
  } catch (error) {
    console.error('获取PSU列表失败:', error)
    ElMessage.error('获取PSU列表失败')
  }
}

// PSU变化时加载对应的Prompt片段
const onPsuChange = async () => {
  if (selectedPsuId.value) {
    await loadPromptFragments()
  }
}

// 加载Prompt片段
const loadPromptFragments = async () => {
  try {
    const response = await promptApi.getPromptFragments(selectedPsuId.value)
    promptFragments.value = response.data
  } catch (error) {
    console.error('获取Prompt片段失败:', error)
    ElMessage.error('获取Prompt片段失败')
  }
}

// 创建新Prompt
const createNewPrompt = () => {
  isEdit.value = false
  currentPrompt.value = {
    id: null,
    fragmentKey: '',
    content: '',
    editable: true,
    type: 'MESSAGE_TEMPLATE',
    sortOrder: 0,
    psuId: selectedPsuId.value
  }
  dialogVisible.value = true
}

// 编辑Prompt
const editPrompt = (row) => {
  isEdit.value = true
  currentPrompt.value = { ...row }
  dialogVisible.value = true
}

// 保存Prompt
const savePrompt = async () => {
  try {
    if (isEdit.value) {
      // 更新现有Prompt
      await promptApi.updatePromptFragment(currentPrompt.value.id, {
        content: currentPrompt.value.content
      })
      ElMessage.success('更新成功')
    } else {
      // 创建新Prompt
      await promptApi.createPromptFragment({
        psuId: selectedPsuId.value,
        fragmentKey: currentPrompt.value.fragmentKey,
        content: currentPrompt.value.content,
        editable: currentPrompt.value.editable,
        type: currentPrompt.value.type,
        sortOrder: currentPrompt.value.sortOrder
      })
      ElMessage.success('创建成功')
    }
    
    dialogVisible.value = false
    await loadPromptFragments()
  } catch (error) {
    console.error('保存Prompt失败:', error)
    ElMessage.error(error.response?.data?.message || '保存失败')
  }
}

// 定版Prompt
const finalizePrompt = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要定版 "${row.fragmentKey}" 吗？定版后将不可修改！`,
      '确认定版',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await promptApi.finalizePromptFragment(row.id)
    ElMessage.success('定版成功')
    await loadPromptFragments()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('定版Prompt失败:', error)
      ElMessage.error(error.response?.data?.message || '定版失败')
    }
  }
}

// 删除Prompt
const deletePrompt = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除 "${row.fragmentKey}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await promptApi.deletePromptFragment(row.id)
    ElMessage.success('删除成功')
    await loadPromptFragments()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除Prompt失败:', error)
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

// 测试Prompt
const testPrompt = async () => {
  try {
    let params = {}
    if (testInput.value.trim()) {
      params = JSON.parse(testInput.value)
    }
    
    const response = await promptApi.testPrompt(selectedPsuId.value, params)
    testResult.value = response.data
  } catch (error) {
    console.error('测试Prompt失败:', error)
    ElMessage.error('测试失败: ' + (error.response?.data?.message || error.message))
  }
}

// 格式化类型标签
const formatTypeLabel = (type) => {
  switch (type) {
    case 'CORE_RULES':
      return '核心规则'
    case 'MESSAGE_TEMPLATE':
      return '消息模板'
    default:
      return type
  }
}

// 获取类型标签样式
const getTypeTagType = (type) => {
  switch (type) {
    case 'CORE_RULES':
      return 'danger'
    case 'MESSAGE_TEMPLATE':
      return 'primary'
    default:
      return 'info'
  }
}

// 初始化数据
onMounted(async () => {
  await loadPsuList()
})
</script>

<style scoped>
.card-container {
  min-height: 500px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: bold;
}

.psu-selection {
  margin-bottom: 20px;
}

.test-card {
  margin-top: 20px;
}

.test-result {
  margin-top: 15px;
  padding: 10px;
  background-color: #f5f5f5;
  border-radius: 4px;
  overflow-x: auto;
}

.dialog-footer {
  text-align: right;
}
</style>