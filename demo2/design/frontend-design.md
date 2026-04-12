# PSU全生命周期管理平台 - 前端设计文档

## 1. 技术栈选型
- **框架**: Vue 3 (Composition API)
- **状态管理**: Pinia
- **UI组件库**: Element Plus
- **HTTP客户端**: Axios
- **构建工具**: Vite
- **路由**: Vue Router 4

## 2. 整体架构设计

### 2.1 应用结构
```
src/
├── assets/                 # 静态资源
├── components/            # 公共组件
│   ├── common/           # 通用组件
│   ├── admin/            # 管理员组件
│   ├── developer/        # 研发组件  
│   └── business/         # 业务组件
├── views/                 # 页面视图
│   ├── auth/             # 认证相关
│   ├── admin/            # 超级管理员视图
│   ├── developer/        # 研发工程空间视图
│   └── business/         # 业务配置空间视图
├── stores/                # Pinia状态管理
│   ├── auth.js          # 认证状态
│   ├── psu.js           # PSU状态管理
│   └── system.js        # 系统配置状态
├── services/              # API服务层
│   ├── api.js           # API封装
│   ├── auth.js          # 认证API
│   ├── psu.js           # PSU相关API
│   └── system.js        # 系统配置API
├── utils/                 # 工具函数
├── router/                # 路由配置
└── App.vue               # 根组件
```

### 2.2 权限路由设计
基于角色的动态路由：
- **超级管理员**: `/admin/*` 
- **研发**: `/developer/*`
- **产品/运营**: `/business/*`

路由守卫严格控制访问权限，无权限页面直接拦截。

## 3. 核心页面设计

### 3.1 研发工程空间 (Developer Space)

#### 3.1.1 PSU资产管理页面
- PSU列表展示（ID、名称、状态、创建时间）
- 新建PSU按钮（仅研发可见）
- PSU详情查看（包含Schema、Prompt片段、版本信息）
- PSU下架操作（软删除）

#### 3.1.2 JSON Schema编辑器
- 可视化字段配置界面
- 支持字段类型：string、number、boolean、object、array
- 必填校验、长度限制、枚举约束配置
- 嵌套结构体支持
- 实时预览JSON结构
- 版本差异对比功能

#### 3.1.3 Prompt片段管理
- 多段Prompt拆分编辑
- 权限分级标记（锁定/可编辑）
- 核心规则片段（不可编辑）
- 话术类片段（可开放给业务侧）

#### 3.1.4 代码生成预览
- 生成代码在线预览
- 代码下载功能
- Git入库记录展示

### 3.2 业务配置空间 (Business Space)

#### 3.2.1 Prompt编排调试
- Schema字段台账只读展示
- 变量引用面板（拖拽式）
- Prompt实时编排编辑器
- 在线调试功能（调用后端接口测试效果）
- 调试结果展示区域

#### 3.2.2 版本提交与跟进
- 版本提交表单
- 审核状态实时展示
- 驳回原因查看
- 定稿进度跟踪

### 3.3 系统管理 (Admin Space)

#### 3.3.1 系统配置
- API密钥管理（掩码显示）
- 密钥新增/编辑/删除
- 加密存储状态指示

#### 3.3.2 账号管理
- 用户列表（角色、状态）
- 账号启用/停用
- 密码重置功能

#### 3.3.3 审计日志
- 操作日志列表（时间、用户、操作类型、详情）
- 日志筛选和搜索
- 导出功能

## 4. 组件设计规范

### 4.1 权限控制组件
- `PermissionGuard`: 权限路由守卫组件
- `RoleVisible`: 基于角色的元素显隐控制
- `ActionPermission`: 操作权限检查

### 4.2 PSU专用组件
- `SchemaEditor`: JSON Schema可视化编辑器
- `PromptComposer`: Prompt编排组件
- `VariableSelector`: 变量选择器（只读模式）
- `CodePreview`: 代码预览组件

### 4.3 通用组件
- `DataTable`: 数据表格（支持分页、筛选）
- `FormDialog`: 表单弹窗
- `AuditLogViewer`: 审计日志查看器

## 5. API接口设计

### 5.1 认证相关
- `POST /api/auth/login`: 用户登录
- `GET /api/auth/profile`: 获取用户信息
- `POST /api/auth/logout`: 用户登出

### 5.2 PSU管理
- `GET /api/psu/list`: 获取PSU列表
- `POST /api/psu/create`: 创建PSU
- `GET /api/psu/{id}`: 获取PSU详情
- `PUT /api/psu/{id}/archive`: 下架PSU

### 5.3 Schema管理
- `GET /api/schema/{psuId}`: 获取Schema（研发可编辑，业务只读）
- `PUT /api/schema/{psuId}`: 更新Schema（仅研发）
- `GET /api/schema/{psuId}/versions`: 获取Schema版本历史

### 5.6 系统管理（仅管理员）
- `GET /api/system/config`: 获取系统配置
- `PUT /api/system/config`: 更新系统配置
- `GET /api/system/users`: 获取用户列表
- `PUT /api/system/users/{userId}/status`: 更新用户状态
- `POST /api/system/users/{userId}/reset-password`: 重置密码
- `GET /api/system/audit-logs`: 获取审计日志

## 6. 安全设计

### 6.1 前端安全措施
- 所有API调用携带认证Token
- 敏感数据（如密钥）前端只显示掩码
- 严格的权限检查，防止越权操作
- 输入验证和XSS防护
- CSRF Token保护

### 6.2 数据传输安全
- HTTPS强制使用
- 敏感数据加密传输
- API响应数据脱敏处理

## 7. 性能优化

### 7.1 加载优化
- 路由懒加载
- 组件按需加载
- 首屏资源优先加载

### 7.2 数据优化
- 列表分页加载
- 大数据量虚拟滚动
- 接口响应缓存策略

### 7.3 交互优化
- 操作反馈及时性
- 加载状态提示
- 错误友好提示

## 8. 兼容性要求

### 8.1 浏览器兼容
- Chrome 最新版
- Firefox 最新版  
- Edge 最新版
- Safari 最新版

### 8.2 响应式设计
- 支持桌面端（≥1200px）
- 支持平板端（768px-1199px）
- 不强制支持移动端（内部系统）

## 9. 开发约束

### 9.1 严格遵循PRD约束
- 不实现Python相关功能
- 不开发只读账号功能
- 不实现CI/CD流水线相关界面
- 不开发一键部署功能
- 不开发插件市场功能
- 不开发复杂命名空间功能

### 9.2 代码规范
- 使用ESLint + Prettier统一代码风格
- 组件命名规范：PascalCase
- 文件命名规范：kebab-case
- 注释规范：关键逻辑必须添加中文注释
- TypeScript类型定义完整

## 10. 自动化流程支持

### 10.1 代码生成对接
- 提供代码生成预览界面
- 支持代码下载功能
- Git入库状态展示

### 10.2 审计追踪
- 所有关键操作记录审计日志
- 操作可追溯到具体用户和时间
- 审计日志不可篡改