# PSU全生命周期管理平台 - 后端设计文档

## 1. 技术栈选型

- **框架**: Spring Boot 3.2.0
- **语言**: Java 17
- **构建工具**: Maven
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **安全框架**: Spring Security
- **权限控制**: RBAC (基于角色的访问控制)
- **API文档**: SpringDoc OpenAPI
- **日志**: SLF4J + Logback
- **JSON处理**: Jackson
- **加密**: BCrypt + AES-256
- **ORM**: Spring Data JPA
- **代码简化**: Lombok

## 2. 整体架构设计

### 2.1 分层架构

```
com.example.psu
├── config/                # 配置类
├── controller/           # 控制器层（基础路由和权限控制）
├── service/              # 服务层（核心业务逻辑）
├── repository/           # 数据访问层（Spring Data JPA）
├── entity/               # 实体类
├── dto/                  # 数据传输对象
├── security/             # 安全相关（JWT、认证过滤器）
└── PsuPlatformApplication.java  # 应用入口
```

**注意**: 实际实现中未使用 `impl/`、`vo/`、`exception/`、`util/`、`generator/` 等子包，结构更加扁平化。

### 2.2 核心模块划分

- **auth-module**: 认证授权模块
- **psu-module**: PSU核心管理模块  
- **schema-module**: JSON Schema管理模块
- **prompt-module**: Prompt管理模块
- **version-module**: 版本审核模块
- **system-module**: 系统管理模块
- **audit-module**: 审计日志模块
- **test-dataset-module**: 测试数据集模块（新增）

### 2.3 数据库设计约束

**重要**: 本系统不使用数据库级外键约束。
- 所有表关系通过应用层JPA实体关系管理数据一致性
- 数据库表统一使用 `ai_prompt_*` 前缀
- 字段名统一使用蛇形命名（snake_case），如 `target_type` 而非 `targetType`

## 3. 核心实体设计

### 3.1 用户实体 (User)

```java
@Entity
@Table(name = "ai_prompt_users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;           // 用户名
    private String password;           // 加密密码
    private UserRole role;             // 角色：ADMIN/DEVELOPER/BUSINESS
    private Boolean enabled;           // 启用状态
    private String phoneNumber;        // 手机号码（新增）
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

**变更说明**: 
- 表名从 `users` 改为 `ai_prompt_users`
- 移除了 `email` 字段
- 新增了 `phoneNumber` 字段

### 3.2 PSU实体 (PsuUnit)

```java
@Entity
@Table(name = "ai_prompt_psu")
public class PsuUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String psuId;              // 全局唯一PSU ID
    private String name;               // PSU名称
    private String description;        // 描述
    private PsuStatus status;          // 状态：ACTIVE/ARCHIVED
    private Long creatorId;            // 创建者ID（研发）
    private Integer majorVersion;      // 主版本号
    private Integer minorVersion;      // 次版本号  
    private Integer patchVersion;      // 修订版本号
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

**变更说明**: 
- 表名从 `psu_units` 改为 `ai_prompt_psu`
- 类名从 `PromptServiceUnit` 改为 `PsuUnit`

### 3.3 JSON Schema实体 (JsonSchema)

```java
@Entity
@Table(name = "ai_prompt_json_schemas")
public class JsonSchema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long psuId;                // 关联PSU ID
    private String schemaContent;      // JSON Schema内容
    private Integer version;           // 版本号
    private Long modifiedBy;           // 修改者ID（仅研发）
    private LocalDateTime createdAt;   // 创建时间
    private String changeLog;          // 变更日志
}
```

**变更说明**: 表名从 `json_schemas` 改为 `ai_prompt_json_schemas`

### 3.4 Prompt片段实体 (PromptFragment)

```java
@Entity
@Table(name = "ai_prompt_prompt_fragments")
public class PromptFragment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long psuId;                // 关联PSU ID
    private String fragmentKey;        // 片段标识
    private String content;            // Prompt内容
    private Boolean editable;          // 是否可编辑（false=已定版锁定，true=可编辑）
    private FragmentType type;         // 类型：CORE_RULES/MESSAGE_TEMPLATE
    private Integer sortOrder;         // 排序
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

### 3.5 版本审核实体 (VersionReview)

```java
@Entity
@Table(name = "ai_prompt_version_reviews")
public class VersionReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long psuId;                // 关联PSU ID
    private Integer majorVersion;      // 主版本
    private Integer minorVersion;      // 次版本
    private Integer patchVersion;      // 修订版本
    private ReviewStatus status;       // 状态：PENDING/APPROVED/REJECTED
    private Long submitterId;          // 提交者ID（业务）
    private Long reviewerId;           // 审核者ID（研发）
    private String rejectionReason;    // 驳回原因
    private LocalDateTime submittedAt; // 提交时间
    private LocalDateTime reviewedAt;  // 审核时间
    private String gitCommitHash;      // Git提交哈希
    private String codeContent;        // 生成的代码内容
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

### 3.6 系统配置实体 (SystemConfig)

```java
@Entity
@Table(name = "ai_prompt_system_configs")
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String configKey;          // 配置键
    private String configValue;        // 配置值（加密存储）
    private ConfigType configType;     // 配置类型：API_KEY/OTHER
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

### 3.7 审计日志实体 (AuditLog)

```java
@Entity
@Table(name = "ai_prompt_audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long userId;               // 操作用户ID
    private String username;           // 操作用户名
    private String operation;          // 操作类型
    private String targetType;         // 目标类型
    private Long targetId;             // 目标ID
    private String details;            // 操作详情（JSON格式）
    private String ipAddress;          // IP地址
    private LocalDateTime createdAt;   // 操作时间
}
```

### 3.8 测试数据集实体 (TestDataset) - 新增

```java
@Entity
@Table(name = "ai_prompt_test_datasets")
public class TestDataset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long psuId;                // 关联PSU ID
    private String name;               // 数据集名称
    private String dataContent;        // 测试数据内容（JSON格式）
    private String description;        // 描述
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

## 4. 核心服务设计

### 4.1 PSU管理服务 (PsuService)

- `createPsu(PsuCreateRequest request, Long creatorId)`: 创建PSU（仅研发）
- `getPsus(int page, int size)`: 分页查询PSU列表（所有登录用户可访问）
- `updatePsu(Long id, PsuCreateRequest request)`: 更新PSU信息（仅研发）
- `deletePsu(Long id)`: 删除PSU（归档，仅研发）

### 4.2 JSON Schema服务 (JsonSchemaService)

- `getSchemaByPsuId(Long psuId, Long userId)`: 获取Schema（权限检查）
- `updateSchema(Long psuId, String schemaContent, Long userId, String changeLog)`: 更新Schema（仅研发）
- `getSchemaVersions(Long psuId)`: 获取Schema版本历史

### 4.3 Prompt管理服务 (PromptService)

- `getPromptFragments(Long psuId)`: 获取Prompt片段列表
- `updatePromptFragment(Long fragmentId, String content, Long userId)`: 更新Prompt片段（权限分级）
- `finalizePromptFragment(Long fragmentId, Long userId)`: 定版Prompt片段（仅运营）
- `testPrompt(Long psuId, Map<String, Object> params)`: 测试Prompt效果

### 4.4 版本审核服务 (VersionReviewService)

- `getVersionReviewsByPsuId(Long psuId)`: 获取PSU的版本审核列表
- `getAllVersionReviews()`: 获取所有版本审核列表
- `submitVersionForReview(Long psuId, Long submitterId)`: 提交版本审核（业务）
- `reviewVersion(Long reviewId, boolean approved, Long reviewerId, String rejectionReason)`: 审核版本（研发）
- `generateBusinessCode(Long psuId, Long userId)`: 生成业务代码

### 4.4.1 版本号管理策略

**三位版本号规则（主版本.次版本.修订版本）**：
- **起始值**: 0.0.0
- **次版本递增**: 开发定JSON Schema时，次版本号+1（0.1.0 → 0.2.0）
- **修订版本递增**: 产品定提示词和参数动态编排时，修订版本号+1（0.0.1 → 0.0.2）  
- **主版本递增**: 产品审核发布时，主版本号+1（1.0.0 → 2.0.0）

**版本号存储位置**：
- PSU表 (`ai_prompt_psu`) - 存储当前PSU的最新版本号
- JSON Schema表 (`ai_prompt_json_schemas`) - 每个Schema版本关联版本号
- Prompt片段表 (`ai_prompt_prompt_fragments`) - 每个Prompt版本关联版本号
- 版本审核表 (`ai_prompt_version_reviews`) - 审核记录包含完整版本号

### 4.5 用户管理服务 (UserService)

- `authenticate(AuthRequest request)`: 用户认证
- `getCurrentUser(Authentication authentication)`: 获取当前用户信息（从数据库查询真实用户信息）
- `getAllUsers()`: 获取用户列表（仅管理员）
- `createUser(User user)`: 创建用户（仅管理员）
- `toggleUserStatus(Long id)`: 切换用户启用状态（仅管理员）

### 4.6 系统配置服务 (SystemConfigService)

- `getAllConfigs()`: 获取所有系统配置（仅管理员）
- `getConfigByKey(String configKey)`: 根据配置键获取配置（仅管理员）
- `saveConfig(String configKey, String configValue, ConfigType configType)`: 创建或更新配置（仅管理员）
- `deleteConfig(Long id)`: 删除系统配置（仅管理员）

### 4.7 审计日志服务 (AuditLogService)

- `getAllAuditLogs()`: 获取所有审计日志（仅管理员）
- `getAuditLogsByUserId(Long userId)`: 根据用户ID获取审计日志（仅管理员）

### 4.8 测试数据集服务 (TestDatasetService) - 新增

- `getTestDatasets(Long psuId)`: 获取PSU的测试数据集列表
- `createTestDataset(Long psuId, TestDatasetCreateRequest request)`: 创建测试数据集
- `updateTestDataset(Long id, TestDatasetCreateRequest request)`: 更新测试数据集
- `deleteTestDataset(Long id)`: 删除测试数据集

### 4.9 代码生成服务 (CodeGeneratorService)

- `generateParameterValidationCode(JsonSchema schema)`: 生成参数校验代码
- `generateParameterAssemblyCode(JsonSchema inputSchema, JsonSchema outputSchema)`: 生成参数拼装代码
- `generatePromptAssemblyCode(List<PromptFragment> fragments)`: 生成Prompt组装代码
- `generateCompleteBusinessCode(PsuCodeGenerationContext context)`: 生成完整业务代码

## 5. 权限控制设计

### 5.1 角色定义

- `ROLE_ADMIN`: 超级管理员
- `ROLE_DEVELOPER`: 研发人员
- `ROLE_BUSINESS`: 产品/运营人员

### 5.2 方法级权限注解

```java
// PSU创建（仅研发）
@PreAuthorize("hasRole('DEVELOPER')")
public PsuDto createPsu(PsuCreateRequest request)

// Schema更新（仅研发）
@PreAuthorize("hasRole('DEVELOPER') and @permissionService.canEditSchema(#psuId, authentication)")
public SchemaDto updateSchema(Long psuId, SchemaUpdateRequest request)

// 业务侧Schema查看（只读）
@PreAuthorize("hasAnyRole('DEVELOPER', 'BUSINESS') and @permissionService.canViewSchema(#psuId, authentication)")
public SchemaDto getSchemaByPsuId(Long psuId)

// 系统配置（仅管理员）
@PreAuthorize("hasRole('ADMIN')")
public SystemConfigDto updateSystemConfig(SystemConfigUpdateRequest request)
```

### 5.3 自定义权限检查服务

```java
@Service
public class PermissionService {
    
    // 检查用户是否有权限编辑指定PSU的Schema
    public boolean canEditSchema(Long psuId, Authentication authentication) {
        // 仅研发可以编辑
        if (!hasRole(authentication, "DEVELOPER")) {
            return false;
        }
        // 检查PSU是否存在且未归档
        return psuRepository.existsByIdAndStatus(psuId, PsuStatus.ACTIVE);
    }
    
    // 检查用户是否有权限查看指定PSU的Schema
    public boolean canViewSchema(Long psuId, Authentication authentication) {
        // 研发和业务都可以查看
        if (!hasAnyRole(authentication, Arrays.asList("DEVELOPER", "BUSINESS"))) {
            return false;
        }
        // 检查PSU是否存在且未归档
        return psuRepository.existsByIdAndStatus(psuId, PsuStatus.ACTIVE);
    }
}
```

## 6. API接口设计

### 6.1 RESTful API规范

- 使用标准HTTP方法：GET/POST/PUT/DELETE
- 资源命名使用复数形式：/api/psus, /api/schemas
- 版本控制：/api/v1/
- 统一响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 6.2 核心API端点

- **认证**: `/api/auth/**`
  - `POST /api/auth/login` - 用户登录
  - `GET /api/auth/profile` - 获取当前用户信息
- **PSU管理**: `/api/psus/**`
  - `POST /api/psus` - 创建PSU（仅研发）
  - `GET /api/psus` - 分页查询PSU列表（所有登录用户）
  - `PUT /api/psus/{id}` - 更新PSU（仅研发）
  - `DELETE /api/psus/{id}` - 删除PSU（仅研发）
- **Schema管理**: `/api/schemas/**`
  - `GET /api/schemas/{psuId}` - 获取Schema
  - `PUT /api/schemas/{psuId}` - 更新Schema（仅研发）
  - `GET /api/schemas/{psuId}/versions` - 获取Schema版本历史
- **Prompt管理**: `/api/prompts/**`
  - `GET /api/prompts/{psuId}` - 获取Prompt片段列表
  - `PUT /api/prompts/{fragmentId}` - 更新Prompt片段
  - `POST /api/prompts/{fragmentId}/finalize` - 定版Prompt片段（仅运营）
  - `POST /api/prompts/{psuId}/test` - 测试Prompt效果
- **版本审核**: `/api/versions/**`
  - `GET /api/versions` - 获取版本审核列表
  - `POST /api/versions/{psuId}/submit` - 提交版本审核（业务）
  - `POST /api/versions/{reviewId}/review` - 审核版本（研发）
  - `GET /api/versions/{psuId}/code` - 获取生成的代码
- **用户管理**: `/api/users/**`（仅管理员）
  - `GET /api/users` - 获取用户列表
  - `POST /api/users` - 创建用户
  - `PUT /api/users/{id}/toggle-status` - 切换用户状态
- **系统配置**: `/api/configs/**`（仅管理员）
  - `GET /api/configs` - 获取所有配置
  - `GET /api/configs/{configKey}` - 根据键获取配置
  - `POST /api/configs` - 创建或更新配置
  - `DELETE /api/configs/{id}` - 删除配置
- **审计日志**: `/api/audit-logs/**`（仅管理员）
  - `GET /api/audit-logs` - 获取所有审计日志
  - `GET /api/audit-logs/user/{userId}` - 根据用户ID获取审计日志
- **测试数据集**: `/api/test-datasets/**`
  - `GET /api/test-datasets?psuId={psuId}` - 获取测试数据集列表
  - `POST /api/test-datasets?psuId={psuId}` - 创建测试数据集
  - `PUT /api/test-datasets/{id}` - 更新测试数据集
  - `DELETE /api/test-datasets/{id}` - 删除测试数据集

## 7. 安全设计

### 7.1 认证授权

- JWT Token认证
- 密码BCrypt加密存储
- Token过期时间配置
- 刷新Token机制

### 7.2 数据安全

- API密钥AES-256加密存储
- 敏感数据脱敏返回
- SQL注入防护
- XSS防护

### 7.3 权限安全

- 严格的RBAC权限控制
- 方法级权限注解
- 自定义权限检查
- 越权操作拦截

### 7.4 审计追踪

- 关键操作全量审计日志
- 不可篡改的日志存储
- 操作可追溯到具体用户

## 8. 代码生成设计

### 8.1 生成范围约束

- **仅生成业务逻辑代码**：
  - 参数合法性校验
  - 上下游参数拼装
  - Prompt自动组装
- **严格禁止生成**：
  - Controller层代码
  - Service接口定义
  - Repository层代码
  - 前端对接逻辑
  - 运维部署脚本

### 8.2 代码模板设计

```java
// 参数校验模板
public class {PsuName}ParameterValidator {
    public void validateInput({InputDto} input) {
        // 基于JSON Schema生成的校验逻辑
    }
}

// 参数拼装模板  
public class {PsuName}ParameterAssembler {
    public {OutputDto} assembleOutput({ModelResponse} response) {
        // 基于输出Schema生成的拼装逻辑
    }
}

// Prompt组装模板
public class {PsuName}PromptAssembler {
    public String assemblePrompt({InputDto} input) {
        // 基于Prompt片段生成的组装逻辑
    }
}
```

### 8.3 生成流程

1. 研发审核通过版本
2. 调用代码生成服务
3. 生成标准业务代码
4. 返回代码预览给前端
5. 研发下载并手动引入工程
6. 记录Git入库信息

## 9. 性能优化

### 9.1 数据库优化

- 关键字段索引优化
- 查询分页处理
- 批量操作支持
- 连接池配置优化

### 9.2 缓存策略

- 用户信息缓存（Redis）
- PSU元数据缓存
- Schema版本缓存
- 缓存失效策略

### 9.3 异步处理

- 审计日志异步写入
- 复杂计算异步处理

## 10. 开发约束

### 10.1 严格遵循PRD约束

- **不支持Python**: 全程纯Java实现
- **无只读账号**: 不实现只读权限体系
- **无CI/CD流水线**: 不开发流水线相关功能
- **无一键部署**: 不实现自动化部署功能
- **无插件生态**: 不开发插件市场功能
- **简化版本管理**: 不实现复杂命名空间和灰度发布

### 10.2 代码规范

- 使用Lombok减少样板代码
- 统一异常处理机制
- 完整的单元测试覆盖
- 代码注释规范（中文注释）
- SonarQube代码质量检查

## 11. 自动化流程支持

### 11.1 Git集成

- 代码生成后强制Git入库登记
- 记录提交哈希、责任人、时间戳
- 资产永久归档追溯

### 11.2 审计自动化

- 关键操作自动记录审计日志
- 操作详情结构化存储
- 审计日志查询和导出

