# PSU全生命周期管理平台 - 数据库设计文档

## 1. 数据库选型
- **数据库**: MySQL 8.0
- **字符集**: utf8mb4
- **排序规则**: utf8mb4_unicode_ci
- **存储引擎**: InnoDB
- **连接池**: HikariCP
- **缓存**: Redis（用于会话和热点数据缓存）

## 2. 数据库设计原则

### 2.1 设计约束
- **严格遵循PRD要求**: 不包含Python相关表结构
- **权限简化**: 不设计复杂的只读权限表
- **轻量化**: 不包含CI/CD流水线、一键部署、插件市场相关表
- **安全优先**: 敏感数据加密存储
- **审计完整**: 关键操作全量审计日志

### 2.2 命名规范
- **表名**: 小写蛇形命名，复数形式（users, psu_units）
- **字段名**: 小写蛇形命名（user_id, created_at）
- **索引名**: idx_{table}_{columns}（idx_users_username）
- **外键名**: fk_{child_table}_{parent_table}（fk_psu_units_creator_id）

### 2.3 数据类型选择
- **主键**: BIGINT（自增）
- **字符串**: VARCHAR（合理长度）或 TEXT（长文本）
- **布尔值**: TINYINT(1)（0/1）
- **时间戳**: DATETIME（精确到秒）
- **JSON数据**: JSON类型（MySQL 5.7+支持）

## 3. 核心表结构设计

### 3.1 用户表 (ai_prompt_users)
```sql
CREATE TABLE ai_prompt_users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '加密密码',
    role ENUM('ADMIN', 'DEVELOPER', 'BUSINESS') NOT NULL COMMENT '角色',
    enabled TINYINT(1) DEFAULT 1 COMMENT '启用状态: 1-启用, 0-停用',
    phone_number VARCHAR(20) COMMENT '手机号码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_users_username (username),
    INDEX idx_users_role (role),
    INDEX idx_users_enabled (enabled)
) COMMENT 'AI Prompt用户表';
```

### 3.2 PSU单元表 (ai_prompt_psu)
```sql
CREATE TABLE ai_prompt_psu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    psu_id VARCHAR(100) NOT NULL UNIQUE COMMENT '全局唯一PSU ID',
    name VARCHAR(200) NOT NULL COMMENT 'PSU名称',
    description TEXT COMMENT '描述',
    status ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    creator_id BIGINT NOT NULL COMMENT '创建者ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_psu_units_psu_id (psu_id),
    INDEX idx_psu_units_status (status),
    INDEX idx_psu_units_creator (creator_id),
    INDEX idx_psu_units_created (created_at)
) COMMENT 'AI Prompt PSU单元表';
```

### 3.3 JSON Schema表 (ai_prompt_json_schemas)
```sql
CREATE TABLE ai_prompt_json_schemas (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    psu_id BIGINT NOT NULL COMMENT '关联PSU ID',
    schema_content JSON NOT NULL COMMENT 'JSON Schema内容',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    modified_by BIGINT NOT NULL COMMENT '修改者ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_log TEXT COMMENT '变更日志',
    
    INDEX idx_json_schemas_psu_id (psu_id),
    INDEX idx_json_schemas_version (version),
    INDEX idx_json_schemas_modified_by (modified_by),
    INDEX idx_json_schemas_created (created_at),
    
    CONSTRAINT uk_json_schemas_psu_version UNIQUE (psu_id, version)
) COMMENT 'AI Prompt JSON Schema表';
```

### 3.4 Prompt片段表 (prompt_fragments)
```sql
CREATE TABLE prompt_fragments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    psu_id BIGINT NOT NULL COMMENT '关联PSU ID',
    fragment_key VARCHAR(100) NOT NULL COMMENT '片段标识',
    content TEXT NOT NULL COMMENT 'Prompt内容',
    editable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可编辑: 0-锁定, 1-可编辑',
    type ENUM('CORE_RULES', 'MESSAGE_TEMPLATE') NOT NULL COMMENT '类型',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_prompt_fragments_psu_id (psu_id),
    INDEX idx_prompt_fragments_type (type),
    INDEX idx_prompt_fragments_editable (editable),
    INDEX idx_prompt_fragments_sort (sort_order),
    
    CONSTRAINT fk_prompt_fragments_psu_id FOREIGN KEY (psu_id) REFERENCES psu_units(id),
    CONSTRAINT uk_prompt_fragments_psu_key UNIQUE (psu_id, fragment_key)
) COMMENT 'Prompt片段表';
```

### 3.5 版本审核表 (version_reviews)
```sql
CREATE TABLE version_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    psu_id BIGINT NOT NULL COMMENT '关联PSU ID',
    major_version INT NOT NULL DEFAULT 1 COMMENT '主版本',
    minor_version INT NOT NULL DEFAULT 0 COMMENT '次版本',
    patch_version INT NOT NULL DEFAULT 0 COMMENT '修订版本',
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    submitter_id BIGINT NOT NULL COMMENT '提交者ID',
    reviewer_id BIGINT COMMENT '审核者ID',
    rejection_reason TEXT COMMENT '驳回原因',
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    reviewed_at DATETIME COMMENT '审核时间',
    git_commit_hash VARCHAR(64) COMMENT 'Git提交哈希',
    code_content LONGTEXT COMMENT '生成的代码内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_version_reviews_psu_id (psu_id),
    INDEX idx_version_reviews_status (status),
    INDEX idx_version_reviews_submitter (submitter_id),
    INDEX idx_version_reviews_reviewer (reviewer_id),
    INDEX idx_version_reviews_submitted (submitted_at),
    INDEX idx_version_reviews_git_hash (git_commit_hash),
    
    CONSTRAINT uk_version_reviews_version UNIQUE (psu_id, major_version, minor_version, patch_version)
) COMMENT '版本审核表';
```

### 3.6 系统配置表 (system_configs)
```sql
CREATE TABLE system_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值（加密存储）',
    config_type ENUM('API_KEY', 'OTHER') NOT NULL DEFAULT 'OTHER' COMMENT '配置类型',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_system_configs_key (config_key),
    INDEX idx_system_configs_type (config_type)
) COMMENT '系统配置表（仅存储API密钥等核心配置）';
```

### 3.7 审计日志表 (audit_logs)
```sql
CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '操作用户ID',
    username VARCHAR(50) NOT NULL COMMENT '操作用户名',
    operation VARCHAR(100) NOT NULL COMMENT '操作类型',
    targetType VARCHAR(50) NOT NULL COMMENT '目标类型',
    target_id BIGINT COMMENT '目标ID',
    details JSON COMMENT '操作详情',
    ip_address VARCHAR(45) COMMENT 'IP地址',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_audit_logs_user_id (user_id),
    INDEX idx_audit_logs_operation (operation),
    INDEX idx_audit_logs_target_type (targetType),
    INDEX idx_audit_logs_target_id (target_id),
    INDEX idx_audit_logs_created (created_at)
) COMMENT '审计日志表';
```

### 3.8 测试数据集表 (test_datasets)
```sql
CREATE TABLE test_datasets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    psu_id BIGINT NOT NULL COMMENT '关联PSU ID',
    name VARCHAR(200) NOT NULL COMMENT '数据集名称',
    data_content TEXT NOT NULL COMMENT '测试数据内容（JSON格式）',
    description VARCHAR(500) COMMENT '描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_test_datasets_psu_id (psu_id)
) COMMENT '测试数据集表';
```

## 4. 关系图谱

### 4.1 实体关系
```
ai_prompt_users (1) ──── (N) ai_prompt_psu
ai_prompt_users (1) ──── (N) ai_prompt_json_schemas
ai_prompt_users (1) ──── (N) version_reviews (submitter)
ai_prompt_users (1) ──── (N) version_reviews (reviewer)
ai_prompt_users (1) ──── (N) audit_logs

ai_prompt_psu (1) ──── (N) ai_prompt_json_schemas
ai_prompt_psu (1) ──── (N) prompt_fragments  
ai_prompt_psu (1) ──── (N) version_reviews
ai_prompt_psu (1) ──── (N) test_datasets
```

### 4.2 外键约束汇总
**注意**: 实际实现中未使用数据库级外键约束，而是在应用层通过JPA关系管理数据一致性。
- `ai_prompt_psu.creator_id` → `ai_prompt_users.id` (应用层管理)
- `ai_prompt_json_schemas.psu_id` → `ai_prompt_psu.id` (应用层管理)
- `ai_prompt_json_schemas.modified_by` → `ai_prompt_users.id` (应用层管理)
- `prompt_fragments.psu_id` → `ai_prompt_psu.id` (应用层管理)
- `version_reviews.psu_id` → `ai_prompt_psu.id` (应用层管理)
- `version_reviews.submitter_id` → `ai_prompt_users.id` (应用层管理)
- `version_reviews.reviewer_id` → `ai_prompt_users.id` (应用层管理)
- `audit_logs.user_id` → `ai_prompt_users.id` (应用层管理)
- `test_datasets.psu_id` → `ai_prompt_psu.id` (应用层管理)

## 5. 索引策略

### 5.1 查询优化索引
- **用户查询**: `idx_users_username`, `idx_users_role`
- **PSU查询**: `idx_psu_units_psu_id`, `idx_psu_units_status`, `idx_psu_units_created`
- **Schema查询**: `idx_json_schemas_psu_id`, `idx_json_schemas_version`
- **版本审核**: `idx_version_reviews_psu_id`, `idx_version_reviews_status`, `idx_version_reviews_submitted`
- **审计日志**: `idx_audit_logs_created`, `idx_audit_logs_operation`

### 5.2 联合索引
- **Schema版本唯一性**: `(psu_id, version)`
- **Prompt片段唯一性**: `(psu_id, fragment_key)`
- **版本号唯一性**: `(psu_id, major_version, minor_version, patch_version)`

## 6. 数据安全设计

### 6.1 敏感数据加密
- **系统配置表**: `config_value` 字段使用AES-256加密存储
- **用户密码**: 使用BCrypt加密存储
- **审计日志**: 不存储敏感数据明文

### 6.2 数据脱敏
- API返回时对敏感字段进行掩码处理
- 前端只显示密钥的部分掩码（如：sk-****-abcd）

### 6.3 访问控制
- 数据库用户权限最小化
- 应用层严格权限控制
- SQL注入防护

## 7. 性能考虑

### 7.1 表分区策略
- **审计日志表**: 按月分区，便于历史数据归档
- **版本审核表**: 按PSU ID哈希分区，提高查询性能

### 7.2 缓存策略
- **热点数据**: 用户信息、PSU元数据缓存到Redis
- **Schema版本**: 频繁访问的Schema版本缓存
- **缓存失效**: 数据变更时主动失效缓存

### 7.3 查询优化
- 避免SELECT *
- 合理使用JOIN
- 大表查询必须带WHERE条件
- 分页查询使用LIMIT OFFSET

## 8. 扩展性设计

### 8.1 预留字段
- 各表预留`ext_info` JSON字段，用于未来扩展
- 时间字段统一使用DATETIME，便于时区处理

### 8.2 版本兼容
- Schema表支持版本历史追溯
- 版本审核表支持完整的版本管理

### 8.3 审计完整性
- 审计日志表设计为只追加，不更新删除
- 操作详情使用JSON格式，便于扩展

## 9. 运维考虑

### 9.1 备份策略
- 每日全量备份
- 每小时增量备份
- 审计日志长期保留

### 9.2 监控指标
- 表大小监控
- 查询性能监控
- 连接数监控
- 锁等待监控

### 9.3 清理策略
- 临时数据定期清理
- 归档数据按策略迁移

## 10. 开发约束

### 10.1 严格遵循PRD约束
- **不包含Python相关表**: 无Python运行环境配置表
- **简化权限模型**: 无复杂的只读权限表结构
- **无CI/CD相关表**: 不包含流水线、部署任务等表
- **无插件市场表**: 不包含插件、扩展功能相关表
- **轻量化设计**: 表结构简洁，避免过度设计

### 10.2 自动化流程支持
- **Git集成**: 版本审核表包含Git提交信息字段
- **代码生成**: 版本审核表存储生成的代码内容
- **审计追踪**: 完整的操作审计日志表

## 11. 初始化脚本

### 11.1 基础数据初始化
```sql
-- 初始化预置用户（每种角色一个）
INSERT IGNORE INTO ai_prompt_users (username, password, role, enabled) 
VALUES 
('admin_user', '$2a$10$vDtCxgJ5qwnH3Hmm.RB3D.Ke0gBPnPvGoTEmZ8Tk6AuNx.n2BRklW', 'ADMIN', 1),
('dev_user', '$2a$10$vDtCxgJ5qwnH3Hmm.RB3D.Ke0gBPnPvGoTEmZ8Tk6AuNx.n2BRklW', 'DEVELOPER', 1),
('bus_user', '$2a$10$vDtCxgJ5qwnH3Hmm.RB3D.Ke0gBPnPvGoTEmZ8Tk6AuNx.n2BRklW', 'BUSINESS', 1);

-- 初始化系统配置（API密钥占位）
INSERT IGNORE INTO system_configs (config_key, config_value, config_type) 
VALUES ('default_api_key', 'encrypted_value_placeholder', 'API_KEY');
```

### 11.2 权限验证
- 确保外键约束正确建立
- 验证唯一性约束生效
- 测试索引查询性能