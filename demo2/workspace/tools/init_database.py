#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PSU全生命周期管理平台 - 数据库初始化工具
功能：创建所有ai_prompt_前缀的表并插入默认初始化数据
"""

import pymysql
import sys
from datetime import datetime

# 数据库连接配置
DB_CONFIG = {
    'host': 'rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com',
    'port': 3306,
    'user': 'aim_user',
    'password': 'Aim@2026',
    'database': 'aim_product_ai',  # 更新为新的数据库名
    'charset': 'utf8mb4'
}

# 预期表列表（8个）
EXPECTED_TABLES = [
    'ai_prompt_users',
    'ai_prompt_psu',
    'ai_prompt_json_schemas',
    'ai_prompt_prompt_fragments',
    'ai_prompt_version_reviews',
    'ai_prompt_system_configs',
    'ai_prompt_audit_logs',
    'ai_prompt_test_datasets'
]

# 建表SQL语句
CREATE_TABLE_SQLS = [
    # 1. 用户表
    """
    CREATE TABLE IF NOT EXISTS ai_prompt_users (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
        password VARCHAR(100) NOT NULL COMMENT '明文密码',
        role ENUM('ADMIN', 'DEVELOPER', 'BUSINESS') NOT NULL COMMENT '角色',
        enabled TINYINT(1) DEFAULT 1 COMMENT '启用状态: 1-启用, 0-停用',
        phone_number VARCHAR(20) COMMENT '手机号码',
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_users_username (username),
        INDEX idx_users_role (role),
        INDEX idx_users_enabled (enabled)
    ) COMMENT 'AI Prompt用户表'
    """,
    
    # 2. PSU单元表
    """
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
    ) COMMENT 'AI Prompt PSU单元表'
    """,
    
    # 3. JSON Schema表
    """
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
    ) COMMENT 'AI Prompt JSON Schema表'
    """,
    
    # 4. Prompt片段表
    """
    CREATE TABLE IF NOT EXISTS ai_prompt_prompt_fragments (
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
    ) COMMENT 'AI Prompt片段表'
    """,
    
    # 5. 版本审核表
    """
    CREATE TABLE IF NOT EXISTS ai_prompt_version_reviews (
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
    ) COMMENT 'AI Prompt版本审核表'
    """,
    
    # 6. 系统配置表
    """
    CREATE TABLE IF NOT EXISTS ai_prompt_system_configs (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
        config_value TEXT NOT NULL COMMENT '配置值（加密存储）',
        config_type ENUM('API_KEY', 'OTHER') NOT NULL DEFAULT 'OTHER' COMMENT '配置类型',
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_system_configs_key (config_key),
        INDEX idx_system_configs_type (config_type)
    ) COMMENT 'AI Prompt系统配置表（仅存储API密钥等核心配置）'
    """,
    
    # 7. 审计日志表
    """
    CREATE TABLE IF NOT EXISTS ai_prompt_audit_logs (
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
    ) COMMENT 'AI Prompt审计日志表'
    """,
    
    # 8. 测试数据集表
    """
    CREATE TABLE IF NOT EXISTS ai_prompt_test_datasets (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        psu_id BIGINT NOT NULL COMMENT '关联PSU ID',
        name VARCHAR(200) NOT NULL COMMENT '数据集名称',
        data_content TEXT NOT NULL COMMENT '测试数据内容（JSON格式）',
        description VARCHAR(500) COMMENT '描述',
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_test_datasets_psu_id (psu_id)
    ) COMMENT 'AI Prompt测试数据集表'
    """
]

# 初始化数据SQL
INIT_DATA_SQLS = [
    # 初始化默认用户（3个角色各一个）
    """
    INSERT IGNORE INTO ai_prompt_users (username, password, role, enabled) 
    VALUES 
    ('admin_user', 'Admin@123', 'ADMIN', 1),
    ('dev_user', 'Dev@123', 'DEVELOPER', 1),
    ('bus_user', 'Bus@123', 'BUSINESS', 1)
    """,
    
    # 初始化默认系统配置
    """
    INSERT IGNORE INTO ai_prompt_system_configs (config_key, config_value, config_type) 
    VALUES ('default_api_key', 'encrypted_value_placeholder', 'API_KEY')
    """
]


def print_header():
    """打印工具头部信息"""
    print("=" * 70)
    print("PSU全生命周期管理平台 - 数据库初始化工具")
    print(f"执行时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)


def print_success(msg):
    """打印成功信息"""
    print(f"  ✓ {msg}")


def print_error(msg):
    """打印错误信息"""
    print(f"  ✗ {msg}")


def print_info(msg):
    """打印普通信息"""
    print(f"  - {msg}")


def init_database():
    """执行数据库初始化操作"""
    print_header()
    
    try:
        # 连接数据库
        print("\n[步骤 1/3] 连接数据库...")
        connection = pymysql.connect(**DB_CONFIG)
        print_success(f"数据库连接成功: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")
        
        cursor = connection.cursor()
        
        # 创建所有表
        print("\n[步骤 2/3] 创建/验证数据表...")
        created_count = 0
        existed_count = 0
        
        for i, sql in enumerate(CREATE_TABLE_SQLS):
            table_name = EXPECTED_TABLES[i]
            try:
                # 检查表是否已存在
                cursor.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = %s AND table_name = %s", 
                             (DB_CONFIG['database'], table_name))
                exists = cursor.fetchone()[0] > 0
                
                if exists:
                    print_info(f"表 {table_name} 已存在，跳过创建")
                    existed_count += 1
                else:
                    cursor.execute(sql)
                    connection.commit()
                    print_success(f"表 {table_name} 创建成功")
                    created_count += 1
            except Exception as e:
                print_error(f"表 {table_name} 操作失败: {str(e)}")
        
        print(f"\n  统计: 新建 {created_count} 个表, 已存在 {existed_count} 个表")
        
        # 插入初始化数据
        print("\n[步骤 3/3] 插入默认初始化数据...")
        
        # 插入默认用户
        try:
            cursor.execute(INIT_DATA_SQLS[0])
            connection.commit()
            print_success("默认用户数据插入成功 (admin_user/dev_user/bus_user)")
        except Exception as e:
            print_error(f"默认用户数据插入失败: {str(e)}")
        
        # 插入默认系统配置
        try:
            cursor.execute(INIT_DATA_SQLS[1])
            connection.commit()
            print_success("默认系统配置插入成功 (default_api_key)")
        except Exception as e:
            print_error(f"默认系统配置插入失败: {str(e)}")
        
        # 验证所有表
        print("\n" + "=" * 70)
        print("数据库表验证结果:")
        print("-" * 70)
        
        cursor.execute("SHOW TABLES LIKE 'ai_prompt_%'")
        tables = cursor.fetchall()
        existing_tables = [t[0] for t in tables]
        
        # 显示每个表的状态和数据量
        for table_name in EXPECTED_TABLES:
            if table_name in existing_tables:
                cursor.execute(f"SELECT COUNT(*) FROM {table_name}")
                count = cursor.fetchone()[0]
                print_success(f"{table_name}: {count} 条记录")
            else:
                print_error(f"{table_name}: 不存在")
        
        # 检查是否有缺失的表
        missing_tables = [t for t in EXPECTED_TABLES if t not in existing_tables]
        
        print("\n" + "=" * 70)
        if not missing_tables:
            print("✓ 数据库初始化完成！所有8个表均已创建并初始化。")
        else:
            print(f"✗ 以下表缺失: {', '.join(missing_tables)}")
        print("=" * 70)
        
        # 显示默认账号信息
        print("\n默认登录账号信息:")
        print("-" * 70)
        print("  管理员: admin_user / Admin@123")
        print("  研发:   dev_user   / Dev@123")
        print("  业务:   bus_user   / Bus@123")
        print("-" * 70)
        
        cursor.close()
        connection.close()
        
    except pymysql.Error as e:
        print(f"\n数据库错误: {str(e)}")
        sys.exit(1)
    except Exception as e:
        print(f"\n未知错误: {str(e)}")
        sys.exit(1)


if __name__ == '__main__':
    init_database()
