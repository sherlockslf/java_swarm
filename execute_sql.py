import pymysql
import sys
from datetime import datetime

# 数据库连接配置
config = {
    'host': 'rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com',
    'port': 3306,
    'user': 'aim_user',
    'password': 'Aim@2026',
    'database': 'aim_product_dev',
    'charset': 'utf8mb4'
}

# SQL语句列表
sql_statements = [
    # 1. 创建Prompt片段表
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
    
    # 2. 创建版本审核表
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
    
    # 3. 创建系统配置表
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
    
    # 4. 创建审计日志表
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
    
    # 5. 创建测试数据集表
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
    """,
    
    # 6. 插入系统配置默认数据
    """
    INSERT IGNORE INTO ai_prompt_system_configs (config_key, config_value, config_type) 
    VALUES ('default_api_key', 'encrypted_value_placeholder', 'API_KEY')
    """,
    
    # 7. 验证所有表
    "SHOW TABLES LIKE 'ai_prompt_%'"
]

def main():
    print("=" * 60)
    print("MySQL数据库操作开始")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)
    
    try:
        # 连接数据库
        print("\n正在连接数据库...")
        connection = pymysql.connect(**config)
        print("数据库连接成功!")
        
        cursor = connection.cursor()
        
        # 执行前5个CREATE TABLE语句
        for i in range(5):
            sql = sql_statements[i]
            table_name = sql.split('CREATE TABLE IF NOT EXISTS ')[1].split(' ')[0]
            print(f"\n[{i+1}/7] 创建表: {table_name}")
            try:
                cursor.execute(sql)
                connection.commit()
                print(f"  ✓ 表 {table_name} 创建成功")
            except Exception as e:
                print(f"  ✗ 创建表 {table_name} 失败: {str(e)}")
        
        # 执行INSERT语句
        print(f"\n[6/7] 插入默认配置数据...")
        try:
            cursor.execute(sql_statements[5])
            connection.commit()
            print("  ✓ 默认配置数据插入成功")
        except Exception as e:
            print(f"  ✗ 插入默认配置数据失败: {str(e)}")
        
        # 执行验证查询
        print(f"\n[7/7] 验证所有ai_prompt_开头的表...")
        cursor.execute(sql_statements[6])
        tables = cursor.fetchall()
        
        print(f"\n找到的表 ({len(tables)} 个):")
        print("-" * 40)
        for table in tables:
            print(f"  ✓ {table[0]}")
        
        # 预期表列表
        expected_tables = [
            'ai_prompt_prompt_fragments',
            'ai_prompt_version_reviews',
            'ai_prompt_system_configs',
            'ai_prompt_audit_logs',
            'ai_prompt_test_datasets'
        ]
        
        # 检查是否所有预期表都存在
        existing_tables = [t[0] for t in tables]
        missing_tables = [t for t in expected_tables if t not in existing_tables]
        
        print("\n" + "=" * 60)
        if not missing_tables:
            print("✓ 所有5个表创建成功!")
        else:
            print(f"✗ 以下表创建失败: {', '.join(missing_tables)}")
        print("=" * 60)
        
        cursor.close()
        connection.close()
        
    except pymysql.Error as e:
        print(f"\n数据库错误: {str(e)}")
        sys.exit(1)
    except Exception as e:
        print(f"\n未知错误: {str(e)}")
        sys.exit(1)

if __name__ == '__main__':
    main()
