# PSU全生命周期管理平台 - 后端设计文档

## 1. 技术栈选型

- **框架**: Spring Boot 3.x
- **语言**: Java 21
- **构建工具**: Maven
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **安全框架**: Spring Security
- **权限控制**: RBAC (基于角色的访问控制)
- **API文档**: SpringDoc OpenAPI
- **日志**: SLF4J + Logback
- **JSON处理**: Jackson
- **加密**: AES-256

## 2. 整体架构设计

### 2.1 分层架构

```
com.example.psu
├── config/                # 配置类
├── controller/           # 控制器层（仅基础路由）
├── service/              # 服务层（核心业务逻辑）
│   ├── impl/            # 服务实现
├── repository/           # 数据访问层
├── entity/               # 实体类
├── dto/                  # 数据传输对象
├── vo/                   # 视图对象
├── exception/            # 异常处理
├── security/             # 安全相关
├── util/                 # 工具类
└── generator/            # 代码生成器
```

### 2.2 核心模块划分

- **auth-module**: 认证授权模块
- **psu-module**: PSU核心管理模块  
- **schema-module**: JSON Schema管理模块
- **prompt-module**: Prompt管理模块
- **version-module**: 版本审核模块
- **system-module**: 系统管理模块
- **audit-module**: 审计日志模块
- **generator-module**: 代码生成模块

## 3. 核心实体设计

### 3.1 用户实体 (User)

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;           // 用户名
    private String password;           // 加密密码
    private UserRole role;             // 角色：ADMIN/DEVELOPER/BUSINESS
    private Boolean enabled;           // 启用状态
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

### 3.2 PSU实体 (PromptServiceUnit)

```java
@Entity
@Table(name = "psu_units")
public class PromptServiceUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String psuId;              // 全局唯一PSU ID
    private String name;               // PSU名称
    private String description;        // 描述
    private PsuStatus status;          // 状态：ACTIVE/ARCHIVED
    private Long creatorId;            // 创建者ID（研发）
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

### 3.3 JSON Schema实体 (JsonSchema)

```java
@Entity
@Table(name = "json_schemas")
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

### 3.4 Prompt片段实体 (PromptFragment)

```java
@Entity
@Table(name = "prompt_fragments")
public class PromptFragment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long psuId;                // 关联PSU ID
    private String fragmentKey;        // 片段标识
    private String content;            // Prompt内容
    private Boolean editable;          // 是否可编辑（false=锁定，true=可编辑）
    private FragmentType type;         // 类型：CORE_RULES/MESSAGE_TEMPLATE
    private Integer order;             // 排序
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
}
```

### 3.7 审计日志实体 (AuditLog)

```java
@Entity
@Table(name = "audit_logs")
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
    private LocalDateTime timestamp;   // 操作时间
    private String ipAddress;          // IP地址
}
```

## 4. 核心服务设计

### 4.1 PSU管理服务 (PsuService)

- `createPsu(PsuCreateRequest request)`: 创建PSU（仅研发）
- `getPsuById(Long psuId)`: 获取PSU详情
- `listPsu(PsuQuery query)`: 查询PSU列表
- `archivePsu(Long psuId, Long userId)`: 下架PSU（仅研发）

### 4.2 JSON Schema服务 (JsonSchemaService)

- `getSchemaByPsuId(Long psuId, Long userId)`: 获取Schema（权限检查）
- `updateSchema(Long psuId, SchemaUpdateRequest request, Long userId)`: 更新Schema（仅研发）
- `getSchemaVersions(Long psuId)`: 获取Schema版本历史
- `validateSchema(String schemaContent)`: 验证Schema合法性

### 4.7 代码生成服务 (CodeGeneratorService)

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

- **认证**: `/api/v1/auth/**`
- **PSU管理**: `/api/v1/psus/**`
- **Schema管理**: `/api/v1/schemas/**`
- **Prompt管理**: `/api/v1/prompts/**`
- **版本审核**: `/api/v1/versions/**`
- **系统配置**: `/api/v1/system/**`（仅管理员）
- **审计日志**: `/api/v1/audit-logs/**`（仅管理员）

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

