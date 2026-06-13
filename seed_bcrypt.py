#!/usr/bin/env python3
"""生成 BCrypt 密码哈希（用于 SQL 种子数据）"""
import bcrypt
password = "Admin@123"
hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
print(f"密码: {password}")
print(f"Hash: {hashed}")
print(f"\nSQL INSERT 示例:")
print(f"INSERT INTO user (id, tenant_id, username, password_hash, role, status) VALUES")
print(f"  (1, 1, 'superadmin', '{hashed}', 0, 1);")
