-- PSU全生命周期管理平台数据库初始化脚本

-- 创建用户表
CREATE TABLE IF NOT EXISTS ai_prompt_users (
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

-- 创建PSU单元表
CREATE TABLE IF NOT EXISTS ai_prompt_psu (
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

-- 创建JSON Schema表
CREATE TABLE IF NOT EXISTS ai_prompt_json_schemas (
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

-- 创建Prompt片段表
CREATE TABLE IF NOT EXISTS prompt_fragments (
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
    
    CONSTRAINT uk_prompt_fragments_psu_key UNIQUE (psu_id, fragment_key)
) COMMENT 'Prompt片段表';

-- 创建版本审核表
CREATE TABLE IF NOT EXISTS version_reviews (
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

-- 创建系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值（加密存储）',
    config_type ENUM('API_KEY', 'OTHER') NOT NULL DEFAULT 'OTHER' COMMENT '配置类型',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_system_configs_key (config_key),
    INDEX idx_system_configs_type (config_type)
) COMMENT '系统配置表（仅存储API密钥等核心配置）';

-- 创建审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
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

-- 初始化预置用户（每种角色一个）
INSERT IGNORE INTO ai_prompt_users (username, password, role, enabled) 
VALUES 
('admin_user', '$2a$10$vDtCxgJ5qwnH3Hmm.RB3D.Ke0gBPnPvGoTEmZ8Tk6AuNx.n2BRklW', 'ADMIN', 1),  -- password: Admin@123
('dev_user', '$2a$10$vDtCxgJ5qwnH3Hmm.RB3D.Ke0gBPnPvGoTEmZ8Tk6AuNx.n2BRklW', 'DEVELOPER', 1),  -- password: Dev@123
('bus_user', '$2a$10$vDtCxgJ5qwnH3Hmm.RB3D.Ke0gBPnPvGoTEmZ8Tk6AuNx.n2BRklW', 'BUSINESS', 1);  -- password: Bus@123

INSERT IGNORE INTO system_configs (config_key, config_value, config_type) 
VALUES ('default_api_key', 'encrypted_value_placeholder', 'API_KEY');