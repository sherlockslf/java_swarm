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
  // 获取PSU列表
  getPsus: () => api.get('/psus'),
  // 创建PSU
  createPsu: (data) => api.post('/psus', data),
  // 更新PSU
  updatePsu: (id, data) => api.put(`/psus/${id}`, data)
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
  // 更新Prompt片段
  updatePromptFragment: (fragmentId, data) => api.put(`/prompts/${fragmentId}`, data),
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
  createUser: (data) => api.post('/users', data)
}