# Java Swarm - 多智能体购物导购与PSU全生命周期管理平台

## 项目概述

Java Swarm 是一个基于多智能体（Multi-Agent）架构的购物导购系统，结合了PSU（Prompt Service Unit）全生命周期管理平台。系统通过多个子代理（SubAgent）并行处理用户购物需求，提供实时报价、商品推荐和决策支持，并提供了完整的Prompt服务单元管理能力。

## 核心功能

### 1. 购物导购系统
- **多智能体架构**: 通过多个子代理并行处理用户购物需求
- **实时交互**: 支持Server-Sent Events (SSE) 流式传输，提供流畅的交互体验
- **智能决策**: 提供商品推荐、价格比较和购买决策支持
- **多轮对话**: 支持追问机制和已决定购买状态

### 2. PSU全生命周期管理平台（V1.2）
- **动态参数编排容器（Prompt Composer）**: 业务侧使用的统一编排容器，支持文本编辑、参数注入、片段顺序配置和在线调试
- **Schema管理**: JSON Schema的版本管理与权限分级控制
- **Prompt管理**: Prompt片段的管理与组装功能
- **审核流程**: 业务提交版本审核，研发审核版本的完整流程
- **代码生成**: 自动生成参数校验、参数拼装和业务逻辑代码

## 技术栈

### 后端技术栈
- **核心框架**: Spring Boot 3.x (Java 17/21)
- **响应式编程**: Spring WebFlux + Reactor
- **并发模型**: Akka Actor System
- **数据持久化**: Spring Data JPA + MySQL 8.0
- **缓存**: Redis
- **安全**: Spring Security + JWT
- **JSON处理**: Fastjson2
- **构建工具**: Maven

### 前端技术栈
- **框架**: Vue 3 (Composition API) + Vite
- **UI组件库**: Element Plus
- **状态管理**: Pinia
- **路由管理**: Vue Router
- **HTTP客户端**: Axios

## 项目结构

```
java-swarm/
├── demo1/                      # 购物导购系统
│   ├── common/                 # 公共模块
│   ├── main-service/           # 主服务模块
│   ├── subagent-service/       # 子代理服务模块
│   ├── simple_web/             # 购物导购前端
│   └── web/                    # 示例前端
├── demo2/                      # PSU全生命周期管理平台
│   ├── config/                 # 配置文件
│   ├── design/                 # 设计文档
│   └── workspace/              # 平台工作区
│       ├── backend/            # 后端服务
│       ├── frontend/           # 前端应用
│       └── tools/              # 工具脚本
├── prd/                        # 产品需求文档
└── README.md                   # 项目说明
```

## 最新开发进展（V1.2版本）

### 动态参数编排容器（Prompt Composer）
- **变量注入**: 从Schema字段树选择并插入变量到编辑器（`{{path}}`语法）
- **编排规格**: 结构化存储（ComposeSpec）可保存、可锁定、可快照的编排内容
- **在线调试**: 支持选择测试集一键批量运行和回归测试
- **审核锁定**: 提交审核后锁定编排内容，防止业务侧修改

### 审核流程优化
- **状态管理**: DRAFT → SUBMITTED → DEV_REVIEWING → APPROVED/REJECTED 的完整状态机
- **锁定机制**: 提交后自动锁定编排内容，确保审核期间内容不变
- **退回策略**: 明确区分退回开发调整Schema/PSU与退回业务重编排的场景

### 数据库增强
- **编排版本管理**: 新增编排版本快照表，支持版本回溯和对比
- **测试运行记录**: 保存测试运行历史，便于回归分析

## 快速开始

### 环境要求
- JDK 17+ / JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0
- Redis

### 启动购物导购系统

#### 启动后端服务
```bash
# 启动主服务
cd demo1/main-service
mvn spring-boot:run

# 启动子代理服务
cd demo1/subagent-service
mvn spring-boot:run
```

#### 启动前端界面
```bash
cd demo1/simple_web
npm install
npm run dev
```

### 启动PSU管理平台

#### 启动后端服务
```bash
cd demo2/workspace/backend
mvn spring-boot:run
```

#### 启动前端界面
```bash
cd demo2/workspace/frontend
npm install
npm run dev
```

## API接口

### 购物导购API
- `POST /api/shopping/stream` - 购物流式对话接口
- 支持多种事件类型：question, subAgentInfo, subAgentReplay, judge, summary/summay, done

### PSU管理API
- `POST /api/auth/login` - 用户认证
- `GET /api/psus` - 获取PSU列表
- `GET /api/compositions` - 获取编排内容
- `PUT /api/compositions` - 保存编排草稿
- `POST /api/compositions/submit` - 提交审核
- `POST /api/compositions/render` - 渲染测试

## 开发特色

### 动态参数编排
- **占位符协议**: 统一使用 `{{path}}`（Mustache风格）变量占位符
- **结构化存储**: 保存可解释的结构化数据（tokens序列、注入计划等）
- **实时解析**: 编辑过程中实时解析tokens，防止保存后解析失败

### 权限管理
- **角色分离**: 管理员、研发、业务三种角色严格分离
- **权限控制**: 基于角色的细粒度权限控制
- **审核机制**: 业务提交、研发审核的完整审核流程

### 代码生成
- **参数校验**: 自动生成基于Schema的参数校验代码
- **参数拼装**: 自动生成参数拼装与转换代码
- **业务逻辑**: 生成完整的业务逻辑代码框架

## 部署说明

### 后端部署
```bash
# 打包
mvn clean package

# 运行
java -jar target/psu-platform-1.0.0.jar
```

### 前端部署
```bash
# 构建
npm run build

# 部署到后端静态资源目录
cp -r dist/* main-service/src/main/resources/static/
```

## 项目约束

- **技术栈**: 纯Java技术栈，不引入Python等其他语言
- **部署方式**: 私有化部署，手工/半自动化发布
- **CI/CD**: 不依赖平台侧流水线
- **角色分离**: 三种角色（管理员、研发、业务）严格分离
- **功能范围**: 遵循V1.0功能裁剪清单，聚焦核心能力

## 贡献指南

1. Fork项目并创建特性分支
2. 遵循现有代码风格和命名约定
3. 添加相应的单元测试和集成测试
4. 确保所有测试通过
5. 提交Pull Request

## 许可证

本项目仅供学习和研究使用。