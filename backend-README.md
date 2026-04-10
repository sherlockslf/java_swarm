# Java Swarm 购物导购后端系统

## 项目概述

Java Swarm 是一个基于多智能体（Multi-Agent）架构的购物导购系统，通过多个子代理（SubAgent）并行处理用户购物需求，提供实时报价、商品推荐和决策支持。系统采用响应式编程模型，支持Server-Sent Events (SSE) 流式传输，为用户提供流畅的交互体验。

## 技术栈

- **核心框架**: Spring Boot 3.x
- **响应式编程**: Spring WebFlux + Reactor
- **并发模型**: Akka Actor System (用于复杂状态管理和分布式协调)
- **JSON处理**: Fastjson2
- **构建工具**: Maven
- **语言版本**: Java 21
- **Lombok**: 简化POJO开发

## 项目结构

```
java-swarm/
├── common/                 # 公共模块
│   ├── src/main/java/com/example/common/
│   │   ├── dto/           # 数据传输对象
│   │   ├── llm/           # LLM客户端抽象与实现
│   │   └── util/          # 工具类
│   └── pom.xml
├── main-service/          # 主服务模块
│   ├── src/main/java/com/example/mainservice/
│   │   ├── config/        # 配置类
│   │   ├── service/       # 核心业务服务
│   │   ├── web/           # Web控制器
│   │   └── MainServiceApplication.java
│   └── pom.xml
├── subagent-service/      # 子代理服务模块
│   ├── src/main/java/com/example/subagentservice/
│   │   ├── model/         # 子代理模型
│   │   ├── repo/          # 子代理仓库
│   │   ├── web/           # Web控制器
│   │   └── SubAgentServiceApplication.java
│   └── pom.xml
└── pom.xml                # 父POM
```

## 模块说明

### Common 模块
- 提供共享的数据传输对象（DTO）
- 实现LLM客户端抽象层，支持Mock和真实Qwen API
- 包含通用工具类和JSON处理工具

### Main Service 模块
- **核心功能**: 购物流式对话编排
- **技术特点**: 
  - 使用Spring WebFlux实现响应式SSE流
  - 集成Akka Actor System进行复杂状态管理
  - 支持多轮对话和追问机制
- **API端点**: `POST /api/shopping/stream`

### SubAgent Service 模块
- **核心功能**: 子代理管理和执行
- **技术特点**:
  - 基于Spring MVC的传统同步处理
  - 动态加载子代理配置和提示词
  - 实现商品信息解析和报价标准化
- **API端点**:
  - `GET /api/subagents` - 获取子代理列表
  - `POST /api/subagents/{id}/reply` - 子代理回复
  - `POST /api/subagents/judge` - 判定服务

## 快速开始

### 环境要求
- JDK 21+
- Maven 3.8+
- 可选：Qwen API密钥（用于真实LLM调用）

### 构建项目
```bash
# 在项目根目录执行
mvn clean install
```

### 启动服务

#### 启动主服务
```bash
cd main-service
mvn spring-boot:run
# 默认端口: 9090
```

#### 启动子代理服务
```bash
cd subagent-service  
mvn spring-boot:run
# 默认端口: 8080
```

### 配置说明

#### 主服务配置 (`main-service/src/main/resources/application.properties`)
- `server.port=9090` - 服务端口
- Akka相关配置在 `application-akka.conf`

#### 子代理服务配置 (`subagent-service/src/main/resources/application.properties`)
- `server.port=8080` - 服务端口
- 子代理数据目录: `data/subagents.json`
- 提示词目录: `data/baonuanneiyi/prompts/`

## API 文档

### 主服务 API

**POST /api/shopping/stream**
- **Content-Type**: application/json
- **Accept**: text/event-stream
- **请求体**:
  ```json
  {
    "request": "需要购买保暖内衣",
    "hidding": "注重材质和舒适度",
    "price": 300,
    "dislike": "不喜欢化纤材质",
    "followUp": "",
    "decided": false,
    "quantity": 1,
    "bundleIntent": false
  }
  ```
- **响应事件类型**:
  - `question`: 用户问题
  - `subAgentInfo`: 子代理信息
  - `subAgentReplay`: 子代理回复
  - `subAgentDone`: 子代理完成
  - `judge`: 判定结果
  - `summary`/`summay`: 总结信息
  - `done`: 对话完成

### 子代理服务 API

**GET /api/subagents**
- 获取所有子代理信息

**POST /api/subagents/{id}/reply**
- 触发指定子代理生成回复

**POST /api/subagents/judge**
- 对子代理回复进行质量判定

## 开发约定

### 代码规范
- 使用Lombok简化getter/setter
- DTO对象使用Fastjson2注解
- 控制器方法参数使用`@Validated`验证
- 日志使用Log4j2

### 测试策略
- 单元测试: JUnit 5 + Mockito
- 集成测试: Spring Boot Test + WebTestClient
- 流式测试: 专门的SSE集成测试

### 目录约定
- 提示词文件: `data/prompts/`
- 商品数据: `data/goodsInfo/`
- 子代理定义: `data/subagents.json`

## 贡献指南

1. Fork项目并创建特性分支
2. 遵循现有代码风格和命名约定
3. 添加相应的单元测试和集成测试
4. 确保所有测试通过
5. 提交Pull Request

## 许可证

本项目仅供学习和研究使用。

## 注意事项

- **配置安全**: 不要在本地创建配置副本，项目配置直接对接远程Nacos
- **LLM集成**: 默认使用Mock LLM客户端，如需真实调用需配置Qwen API密钥
- **跨域设置**: 主服务已配置允许前端开发服务器跨域访问
- **流式传输**: SSE响应头已设置禁用缓冲，确保实时性