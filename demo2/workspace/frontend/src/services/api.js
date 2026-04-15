import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 代理到后端API
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 添加认证token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response
  },
  error => {
    // 处理认证失败
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userRole')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api

// API接口定义
export const authApi = {
  // 登录
  login: (data) => api.post('/auth/login', data),
  // 获取用户信息
  getProfile: () => api.get('/auth/profile')
}

export const psuApi = {
  // 分页获取PSU列表
  getPsus: (page = 1, size = 10) => api.get('/psus', { params: { page, size } }),
  // 根据数据库ID获取PSU
  getPsuById: (id) => api.get(`/psus/${id}`),
  // 根据PSU ID（全局唯一ID）获取PSU
  getPsuByPsuId: (psuId) => api.get(`/psus/by-psu-id/${psuId}`),
  // 创建PSU
  createPsu: (data) => api.post('/psus', data),
  // 更新PSU
  updatePsu: (id, data) => api.put(`/psus/${id}`, data),
  // 删除PSU（归档）
  deletePsu: (id) => api.delete(`/psus/${id}`)
}

export const schemaApi = {
  // 获取Schema
  getSchema: (psuId) => api.get(`/schemas/${psuId}`),
  // 更新Schema
  updateSchema: (psuId, data) => api.put(`/schemas/${psuId}`, data),
  // 获取Schema版本历史
  getSchemaVersions: (psuId) => api.get(`/schemas/${psuId}/versions`)
}

export const promptApi = {
  // 获取Prompt片段
  getPromptFragments: (psuId) => api.get(`/prompts/${psuId}`),
  // 创建Prompt片段（仅研发）
  createPromptFragment: (data) => api.post('/prompts', data),
  // 更新Prompt片段
  updatePromptFragment: (fragmentId, data) => api.put(`/prompts/${fragmentId}`, data),
  // 删除Prompt片段（仅研发）
  deletePromptFragment: (fragmentId) => api.delete(`/prompts/${fragmentId}`),
  // 定版Prompt片段（仅运营）
  finalizePromptFragment: (fragmentId) => api.post(`/prompts/${fragmentId}/finalize`),
  // 测试Prompt
  testPrompt: (psuId, data) => api.post(`/prompts/${psuId}/test`, data)
}

export const versionApi = {
  // 提交版本审核
  submitVersion: (psuId) => api.post(`/versions/${psuId}/submit`),
  // 审核版本
  reviewVersion: (reviewId, data) => api.post(`/versions/${reviewId}/review`, data),
  // 获取生成的代码
  getCode: (psuId) => api.get(`/versions/${psuId}/code`)
}

export const userApi = {
  // 获取用户列表
  getUsers: () => api.get('/users'),
  // 创建用户
  createUser: (data) => api.post('/users', data),
  // 切换用户状态
  toggleUserStatus: (id) => api.put(`/users/${id}/toggle-status`)
}

export const testDatasetApi = {
  // 获取PSU的测试数据集列表
  getTestDatasets: (psuId) => api.get('/test-datasets', { params: { psuId } }),
  // 创建测试数据集
  createTestDataset: (psuId, data) => api.post('/test-datasets', data, { params: { psuId } }),
  // 更新测试数据集
  updateTestDataset: (id, data) => api.put(`/test-datasets/${id}`, data),
  // 删除测试数据集
  deleteTestDataset: (id) => api.delete(`/test-datasets/${id}`)
}

export const versionReviewApi = {
  // 获取版本审核列表
  getVersionReviews: (psuId) => {
    if (psuId) {
      return api.get('/versions', { params: { psuId } })
    }
    return api.get('/versions')
  },
  // 提交版本审核
  submitVersion: (psuId) => api.post(`/versions/${psuId}/submit`),
  // 审核版本
  reviewVersion: (reviewId, data) => api.post(`/versions/${reviewId}/review`, data),
  // 获取生成的代码
  getCode: (psuId) => api.get(`/versions/${psuId}/code`)
}

export const configApi = {
  // 获取所有系统配置
  getAllConfigs: () => api.get('/configs'),
  // 根据配置键获取配置
  getConfigByKey: (configKey) => api.get(`/configs/${configKey}`),
  // 创建或更新系统配置
  saveConfig: (data) => api.post('/configs', data),
  // 删除系统配置
  deleteConfig: (id) => api.delete(`/configs/${id}`)
}

export const auditLogApi = {
  // 获取所有审计日志
  getAllAuditLogs: () => api.get('/audit-logs'),
  // 根据用户ID获取审计日志
  getAuditLogsByUserId: (userId) => api.get(`/audit-logs/user/${userId}`)
}

export const compositionApi = {
  getComposition: (psuId) => api.get('/compositions', { params: { psuId } }),
  saveDraft: (psuId, data) => api.put('/compositions', data, { params: { psuId } }),
  validate: (psuId, data) => api.post('/compositions/validate', data, { params: { psuId } }),
  render: (psuId, data) => api.post('/compositions/render', data, { params: { psuId } }),
  submit: (psuId) => api.post('/compositions/submit', null, { params: { psuId } })
}

export const testRunApi = {
  runTest: (psuId, datasetId) => api.post('/test-runs', null, { params: { psuId, datasetId } }),
  getTestRun: (runId) => api.get(`/test-runs/${runId}`)
}