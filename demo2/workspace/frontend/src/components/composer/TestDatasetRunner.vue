<template>
  <div class="test-dataset-runner">
    <el-form label-width="100px">
      <el-form-item label="选择测试集">
        <el-select
          v-model="selectedDatasetId"
          placeholder="请选择测试数据集"
          style="width: 100%"
          @change="onDatasetChange"
        >
          <el-option
            v-for="ds in datasets"
            :key="ds.id"
            :label="ds.name"
            :value="ds.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          @click="runTest"
          :loading="running"
          :disabled="!selectedDatasetId"
        >运行测试</el-button>
      </el-form-item>
    </el-form>

    <div v-if="testResult" class="test-result">
      <el-divider>测试结果</el-divider>
      <div class="result-summary">
        <el-tag type="info">总计: {{ testResult.totalCases }}</el-tag>
        <el-tag type="success" style="margin-left: 8px">成功: {{ testResult.successCases }}</el-tag>
        <el-tag type="danger" style="margin-left: 8px">失败: {{ testResult.failedCases }}</el-tag>
      </div>

      <el-table :data="testResult.items" style="width: 100%; margin-top: 12px" size="small">
        <el-table-column prop="caseName" label="用例名称" width="150" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="80" align="center">
          <template #default="{ row }">
            {{ row.latencyMs }}ms
          </template>
        </el-table-column>
        <el-table-column label="错误摘要" min-width="150">
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showDetailDialog" title="测试详情" width="700px">
      <div v-if="detailItem" class="detail-content">
        <h5>输入参数</h5>
        <pre class="json-block">{{ formatJson(detailItem.input) }}</pre>
        <h5>渲染后的 Prompt</h5>
        <pre class="prompt-block">{{ detailItem.renderedPrompt || '-' }}</pre>
        <h5>模型输出</h5>
        <pre class="output-block">{{ detailItem.modelOutput || '-' }}</pre>
        <div v-if="detailItem.errorMessage" class="error-block">
          <strong>错误信息:</strong> {{ detailItem.errorMessage }}
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { testDatasetApi, testRunApi } from '@/services/api'

const props = defineProps({
  psuId: { type: Number, required: true }
})

const emit = defineEmits(['close'])

const datasets = ref([])
const selectedDatasetId = ref('')
const running = ref(false)
const testResult = ref(null)
const showDetailDialog = ref(false)
const detailItem = ref(null)

onMounted(async () => {
  await loadDatasets()
})

async function loadDatasets() {
  try {
    const res = await testDatasetApi.getTestDatasets(props.psuId)
    datasets.value = res.data
  } catch (e) {
    ElMessage.error('加载测试集失败')
  }
}

function onDatasetChange() {
  testResult.value = null
}

async function runTest() {
  if (!selectedDatasetId.value) {
    ElMessage.warning('请选择测试集')
    return
  }

  running.value = true
  testResult.value = null
  try {
    const res = await testRunApi.runTest(props.psuId, selectedDatasetId.value)
    testResult.value = res.data
    if (res.data.failedCases === 0) {
      ElMessage.success('全部测试通过')
    } else {
      ElMessage.warning(`测试完成: ${res.data.failedCases} 个失败`)
    }
  } catch (e) {
    ElMessage.error('运行测试失败: ' + (e.response?.data?.error || e.message))
  } finally {
    running.value = false
  }
}

function showDetail(item) {
  detailItem.value = item
  showDetailDialog.value = true
}

function formatJson(obj) {
  if (!obj) return '{}'
  try {
    return typeof obj === 'string' ? JSON.stringify(JSON.parse(obj), null, 2) : JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}
</script>

<style scoped>
.test-dataset-runner {
  padding: 8px 0;
}

.result-summary {
  display: flex;
  align-items: center;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
}

.detail-content h5 {
  margin: 12px 0 6px 0;
  font-size: 13px;
  color: #303133;
}

.json-block, .prompt-block, .output-block {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}

.error-block {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fef0f0;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 12px;
}
</style>
