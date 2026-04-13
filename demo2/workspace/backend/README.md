# PSU全生命周期管理平台 - 后端

## 项目概述
PSU（Prompt Service Unit）全生命周期管理平台后端，基于Spring Boot 3.2 + Java 17构建。

## 技术栈
- Spring Boot 3.2.0
- Java 17
- Maven 3.8+
- Spring Security (认证授权)
- Spring Data JPA (数据访问)
- MySQL 8.0 (数据库)
- Redis (缓存)
- JWT (Token管理)
- Lombok (代码简化)
- Hibernate (ORM)

## 项目结构
```
src/main/java/com/example/psu/
├── PsuPlatformApplication.java    # 主应用类
├── config/                       # 配置类
│   ├── DatabaseConfig.java       # 数据库配置
│   └── SecurityConfig.java       # 安全配置
├── controller/                   # 控制器层
│   ├── AuthController.java       # 认证控制器
│   ├── PsuController.java        # PSU管理控制器
│   ├── JsonSchemaController.java # Schema管理控制器
│   ├── PromptController.java     # Prompt管理控制器
│   └── VersionReviewController.java # 版本审核控制器
├── service/                      # 服务层
│   ├── UserService.java          # 用户服务
│   ├── PsuService.java           # PSU服务
│   ├── JsonSchemaService.java    # Schema服务
│   ├── PromptService.java        # Prompt服务
│   ├── VersionReviewService.java # 版本审核服务
│   └── CodeGeneratorService.java # 代码生成服务
├── repository/                   # 数据访问层
│   ├── UserRepository.java       # 用户仓库
│   ├── PsuRepository.java        # PSU仓库
│   ├── JsonSchemaRepository.java # Schema仓库
│   ├── PromptFragmentRepository.java # Prompt片段仓库
│   └── VersionReviewRepository.java # 版本审核仓库
├── entity/                       # 实体类
│   ├── User.java                 # 用户实体
│   ├── PsuUnit.java              # PSU实体
│   ├── JsonSchema.java           # Schema实体
│   ├── PromptFragment.java       # Prompt片段实体
│   └── VersionReview.java        # 版本审核实体
├── dto/                          # 数据传输对象
│   ├── request/                  # 请求对象
│   │   ├── UpdateSchemaRequest.java # Schema更新请求
│   │   ├── UpdatePromptRequest.java # Prompt更新请求
│   │   └── ReviewRequest.java    # 审核请求
│   ├── AuthRequest.java          # 认证请求
│   ├── AuthResponse.java         # 认证响应
│   └── PsuCreateRequest.java     # PSU创建请求
├── security/                     # 安全相关
│   ├── JwtUtil.java              # JWT工具类
│   ├── JwtAuthenticationFilter.java # JWT认证过滤器
│   ├── UserDetailsServiceImpl.java # 用户详情服务
│   └── SecurityConfig.java       # 安全配置
└── util/                         # 工具类
    └── UserContext.java          # 用户上下文工具
```

## 核心功能模块

### 1. 认证授权模块
- JWT Token认证
- 基于角色的权限控制
- 用户登录/登出
- 密码加密存储

### 2. PSU管理模块
- PSU单元的创建、查询、归档
- 权限控制（仅研发可操作）

### 3. JSON Schema管理模块
- Schema的版本管理
- 权限分级（研发可编辑，业务只读）
- Schema验证

### 4. Prompt管理模块
- Prompt片段的管理
- 权限分级（锁定片段仅研发可编辑）
- Prompt组装功能

### 5. 版本审核模块
- 业务提交版本审核
- 研发审核版本
- Git提交记录

### 6. 代码生成模块
- 参数校验代码生成
- 参数拼装代码生成
- Prompt组装代码生成
- 完整业务代码生成

## 数据库设计

### 表结构
- `ai_prompt_users`: 用户表（用户名、角色、启用状态、手机号码等）
- `ai_prompt_psu`: PSU单元表（PSU ID、名称、描述、状态等）
- `ai_prompt_json_schemas`: JSON Schema表（Schema内容、版本、修改者等）
- `prompt_fragments`: Prompt片段表（片段内容、类型、可编辑状态等）
- `version_reviews`: 版本审核表（版本信息、审核状态、代码内容等）
- `system_configs`: 系统配置表（API密钥等配置）
- `audit_logs`: 审计日志表（操作日志）

### 约束和索引
- 所有表都有适当的索引以提高查询性能
- 外键约束保证数据完整性
- 唯一约束防止重复数据

## 配置文件
- `application.yml`: 主配置文件（数据库、Redis、安全配置等）
- `schema.sql`: 数据库初始化脚本

## 启动项目

### 环境要求
- Java 17+
- Maven 3.8+
- MySQL 8.0
- Redis

### 启动命令
```bash
mvn spring-boot:run
```

### 打包命令
```bash
mvn clean package
```

### 运行打包后的应用
```bash
java -jar target/psu-platform-1.0.0.jar
```

## API接口

### 认证接口
- `POST /api/auth/login` - 用户登录
- `GET /api/auth/profile` - 获取用户信息

### PSU管理接口
- `POST /api/psus` - 创建PSU（仅研发）
- `GET /api/psus` - 获取PSU列表

### Schema管理接口
- `GET /api/schemas/{psuId}` - 获取Schema
- `PUT /api/schemas/{psuId}` - 更新Schema（仅研发）
- `GET /api/schemas/{psuId}/versions` - 获取Schema版本历史

### Prompt管理接口
- `GET /api/prompts/{psuId}` - 获取Prompt片段
- `PUT /api/prompts/{fragmentId}` - 更新Prompt片段
- `POST /api/prompts/{psuId}/test` - 测试Prompt

### 版本审核接口
- `POST /api/versions/{psuId}/submit` - 提交版本审核（业务）
- `POST /api/versions/{reviewId}/review` - 审核版本（研发）
- `GET /api/versions/{psuId}/code` - 获取生成的代码

## 安全特性
- 所有敏感接口受JWT Token保护
- 基于角色的细粒度权限控制
- 密码使用BCrypt加密
- API密钥AES加密存储
- 完整的审计日志

## 遵循的约束
- 纯Java技术栈（无Python）
- 三类角色严格分离（管理员、研发、业务）
- 无CI/CD流水线
- 无一键部署
- 无插件市场
- 代码生成仅限业务逻辑代码