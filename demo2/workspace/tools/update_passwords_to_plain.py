#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PSU全生命周期管理平台 - 密码更新工具
功能：将数据库中所有用户的BCrypt加密密码更新为明文密码
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

# 默认账号密码映射
DEFAULT_PASSWORDS = {
    'admin_user': 'Admin@123',
    'dev_user': 'Dev@123',
    'bus_user': 'Bus@123'
}


def print_header():
    """打印工具头部信息"""
    print("=" * 70)
    print("PSU全生命周期管理平台 - 密码更新工具")
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


def is_bcrypt_hash(password):
    """判断密码是否为BCrypt哈希值"""
    return password.startswith('$2a$') or password.startswith('$2b$') or password.startswith('$2y$')


def update_passwords():
    """执行密码更新操作"""
    print_header()
    
    try:
        # 连接数据库
        print("\n[步骤 1/3] 连接数据库...")
        connection = pymysql.connect(**DB_CONFIG)
        print_success(f"数据库连接成功: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")
        
        cursor = connection.cursor()
        
        # 查询所有用户
        print("\n[步骤 2/3] 查询用户数据...")
        cursor.execute("SELECT id, username, password FROM ai_prompt_users")
        users = cursor.fetchall()
        
        if not users:
            print_info("数据库中暂无用户数据")
            cursor.close()
            connection.close()
            return
        
        print_success(f"共找到 {len(users)} 个用户")
        
        # 更新密码
        print("\n[步骤 3/3] 更新密码为明文...")
        updated_count = 0
        skipped_count = 0
        error_count = 0
        
        for user_id, username, current_password in users:
            try:
                # 判断是否为加密密码
                if is_bcrypt_hash(current_password):
                    # 是加密密码，需要更新
                    if username in DEFAULT_PASSWORDS:
                        new_password = DEFAULT_PASSWORDS[username]
                    else:
                        # 非默认账号，保持原密码不变（假设已经是明文）
                        print_info(f"用户 {username}: 非默认账号，跳过更新")
                        skipped_count += 1
                        continue
                    
                    # 更新为明文密码
                    cursor.execute(
                        "UPDATE ai_prompt_users SET password = %s WHERE id = %s",
                        (new_password, user_id)
                    )
                    connection.commit()
                    print_success(f"用户 {username}: 已更新为明文密码")
                    updated_count += 1
                else:
                    # 已经是明文密码，跳过
                    print_info(f"用户 {username}: 已是明文密码，跳过")
                    skipped_count += 1
                    
            except Exception as e:
                print_error(f"用户 {username} 更新失败: {str(e)}")
                error_count += 1
        
        # 显示统计结果
        print("\n" + "=" * 70)
        print("密码更新统计:")
        print("-" * 70)
        print(f"  总用户数: {len(users)}")
        print(f"  已更新: {updated_count}")
        print(f"  已跳过: {skipped_count}")
        print(f"  失败: {error_count}")
        print("=" * 70)
        
        # 验证更新结果
        print("\n当前用户密码状态:")
        print("-" * 70)
        cursor.execute("SELECT username, password FROM ai_prompt_users")
        current_users = cursor.fetchall()
        
        for username, password in current_users:
            if is_bcrypt_hash(password):
                print_error(f"{username}: 仍为加密密码")
            else:
                print_success(f"{username}: 明文密码 - {password}")
        
        print("-" * 70)
        
        cursor.close()
        connection.close()
        
        print("\n✓ 密码更新完成！")
        
    except pymysql.Error as e:
        print(f"\n数据库错误: {str(e)}")
        sys.exit(1)
    except Exception as e:
        print(f"\n未知错误: {str(e)}")
        sys.exit(1)


if __name__ == '__main__':
    update_passwords()
