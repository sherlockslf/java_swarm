#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PSU全生命周期管理平台 - 数据库结构更新工具
功能：更新ai_prompt_psu表结构，添加版本号字段
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
    'database': 'aim_product_ai',
    'charset': 'utf8mb4'
}

def print_header():
    """打印工具头部信息"""
    print("=" * 70)
    print("PSU全生命周期管理平台 - 数据库结构更新工具")
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

def update_database_structure():
    """执行数据库结构更新操作"""
    print_header()
    
    try:
        # 连接数据库
        print("\n[步骤 1/2] 连接数据库...")
        connection = pymysql.connect(**DB_CONFIG)
        print_success(f"数据库连接成功: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")
        
        cursor = connection.cursor()
        
        # 检查PSU表是否已存在版本号字段
        print("\n[步骤 2/2] 检查并更新PSU表结构...")
        
        # 检查major_version字段是否存在
        cursor.execute("""
            SELECT COUNT(*) 
            FROM information_schema.COLUMNS 
            WHERE TABLE_SCHEMA = %s 
            AND TABLE_NAME = 'ai_prompt_psu' 
            AND COLUMN_NAME = 'major_version'
        """, (DB_CONFIG['database'],))
        
        major_version_exists = cursor.fetchone()[0] > 0
        
        if major_version_exists:
            print_info("PSU表已包含版本号字段，无需更新")
        else:
            print_info("正在添加版本号字段到ai_prompt_psu表...")
            
            # 添加版本号字段
            alter_sqls = [
                "ALTER TABLE ai_prompt_psu ADD COLUMN major_version INT NOT NULL DEFAULT 0 COMMENT '主版本号' AFTER creator_id;",
                "ALTER TABLE ai_prompt_psu ADD COLUMN minor_version INT NOT NULL DEFAULT 0 COMMENT '次版本号' AFTER major_version;",
                "ALTER TABLE ai_prompt_psu ADD COLUMN patch_version INT NOT NULL DEFAULT 0 COMMENT '修订版本号' AFTER minor_version;"
            ]
            
            for sql in alter_sqls:
                try:
                    cursor.execute(sql)
                    connection.commit()
                    print_success(f"执行成功: {sql[:50]}...")
                except Exception as e:
                    if "Duplicate column" in str(e):
                        print_info(f"字段已存在，跳过: {sql[:50]}...")
                    else:
                        print_error(f"执行失败: {sql[:50]}... - {str(e)}")
        
        # 验证更新结果
        print("\n验证PSU表结构...")
        cursor.execute("DESCRIBE ai_prompt_psu")
        columns = cursor.fetchall()
        
        version_fields = ['major_version', 'minor_version', 'patch_version']
        found_fields = [col[0] for col in columns if col[0] in version_fields]
        
        print_info(f"PSU表结构: 共{len(columns)}个字段")
        print_info(f"版本号字段: {found_fields}")
        
        if len(found_fields) == 3:
            print_success("✓ PSU表版本号字段更新成功！")
        else:
            missing = [f for f in version_fields if f not in found_fields]
            print_error(f"✗ PSU表版本号字段更新不完整，缺少: {missing}")
        
        # 显示PSU表的当前结构
        print("\nPSU表当前字段结构:")
        print("-" * 50)
        for col in columns:
            print(f"  {col[0]:<20} {col[1]:<20} {col[4] if col[4] else ''}")
        
        cursor.close()
        connection.close()
        
        print("\n" + "=" * 70)
        print("数据库结构更新完成！")
        print("=" * 70)
        
    except pymysql.Error as e:
        print(f"\n数据库错误: {str(e)}")
        sys.exit(1)
    except Exception as e:
        print(f"\n未知错误: {str(e)}")
        sys.exit(1)

if __name__ == '__main__':
    update_database_structure()