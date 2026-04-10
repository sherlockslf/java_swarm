# Simple Web - 购物导购前端应用

## 项目概述

Simple Web 是 Java Swarm 购物导购系统的前端界面，基于 Vue 3 和 Vite 构建。该应用通过 Server-Sent Events (SSE) 与后端主服务进行实时流式通信，展示多智能体购物导购的完整交互过程。

## 技术栈

- **框架**: Vue 3 (Composition API)
- **构建工具**: Vite 5
- **包管理**: npm
- **语言**: JavaScript (ES2022+)
- **样式**: 原生CSS Grid + Flexbox

## 项目结构

```
simple_web/
├── src/
│   ├── App.vue           # 主应用组件
│   └── main.js           # 应用入口
├── index.html            # HTML模板
├── package.json          # 依赖配置
├── vite.config.js        # Vite配置
└── .gitignore            # 忽略文件配置
```

## 核心功能

### 流式对话界面
- **输入区域**: 支持用户输入购物需求、预算、偏好等信息
- **实时响应**: 通过SSE接收并展示各类事件（子代理回复、判定结果、总结等）
- **统计面板**: 显示对话耗时、字符数、Token估算、报价信息等统计数据

### 事件处理
前端支持处理以下SSE事件类型：
- `question`: 用户原始问题
- `subAgentInfo`: 可用子代理列表信息
- `subAgentReplay`: 子代理回复内容（包含报价、商品信息等）
- `subAgentDone`: 子代理完成标识
- `judge`: 质量判定结果（pass/severe/decision等）
- `summary`/`summay`: 对话总结和决策信息
- `done`: 对话完成事件

### 多轮对话支持
- 支持追问（followUp）机制
- 支持已决定购买状态
- 支持购买数量和凑单意图设置

## 快速开始

### 环境要求
- Node.js 18+
- npm 8+

### 安装依赖
```bash
cd simple_web
npm install
```

### 开发模式
```bash
npm run dev
```
- 默认访问地址: `http://localhost:5173`
- 自动热重载
- 后端API默认指向 `http://localhost:9090`

### 生产构建
```bash
npm run build
```

### 预览构建结果
```bash
npm run preview
```

## 配置说明

### API基础URL配置
前端通过环境变量配置后端API地址：

**开发环境** (`vite.config.js`):
```javascript
// 默认配置
VITE_API_BASE=http://localhost:9090
```

**生产环境**: 可通过部署时设置环境变量覆盖

### 跨域处理
- 开发模式下，Vite内置代理可处理跨域
- 生产部署时，建议将前端静态资源托管在后端服务的static目录下，或配置Nginx反向代理

## 开发约定

### 代码风格
- 使用Vue 3 Composition API
- 响应式状态管理使用 `reactive` 和 `ref`
- 事件处理函数命名以 `on` 开头（如 `onStart`, `onReset`）

### SSE处理逻辑
- 使用原生 `fetch` + `ReadableStream` 处理SSE
- 手动解析SSE数据块（event + data）
- 支持事件类型 `summary` 和 `summay`（兼容历史版本）

### 状态管理
- 表单状态: `form` 对象
- 统计信息: `stats` 对象  
- 事件日志: `events` 数组
- 子代理对话: `agentConvs` 数组
- 总结文本: `summaryText` ref

### 工具函数
- `estTokens()`: 字符数转Token估算
- `fmtTime()`: 时间格式化
- `tsNow()`: 获取当前时间戳
- `upsertAgent()`: 子代理信息更新/插入
- `parseEventBlock()`: SSE数据块解析

## 调试技巧

### 控制台日志
应用内置详细的控制台日志，包含时间戳前缀：
- `[UI]`: 用户界面相关操作
- `[REQ]`: 请求相关操作  
- `[SSE]`: Server-Sent Events处理
- 日志级别: info/debug/warn/error

### 浏览器开发者工具
- Network Tab: 查看SSE连接和事件流
- Console Tab: 查看详细日志信息
- Elements Tab: 检查UI渲染状态

## 部署指南

### 开发服务器部署
1. 构建前端: `npm run build`
2. 将 `dist` 目录内容复制到后端 `main-service/src/main/resources/static/`
3. 启动后端服务，前端可通过根路径访问

### 独立部署
1. 构建前端: `npm run build`
2. 配置Web服务器（如Nginx）托管静态文件
3. 配置API代理指向后端服务

### Nginx配置示例
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    location / {
        root /path/to/simple_web/dist;
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://localhost:9090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 注意事项

- **API兼容性**: 前端同时支持 `summary` 和 `summay` 事件类型（历史兼容）
- **错误处理**: 包含完整的SSE解析错误处理和网络错误处理
- **内存管理**: 自动清理已完成的流式连接
- **用户体验**: 提供加载状态和取消功能

## 贡献指南

1. 遵循现有代码风格和命名约定
2. 添加必要的类型注释和文档
3. 确保在不同浏览器环境下正常工作
4. 测试流式事件处理的稳定性

## 许可证

本项目仅供学习和研究使用。