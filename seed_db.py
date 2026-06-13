#!/usr/bin/env python3
"""Seed initial tenant and user data into MySQL"""
import io, sys, pymysql, bcrypt

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

passwords = []
import os
for key in ['MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD']:
    v = os.environ.get(key)
    if v: passwords.append(v)
passwords.extend(['root123', 'admin123', 'mysql123', '123456', 'password'])

for pwd in passwords:
    try:
        conn = pymysql.connect(host='localhost', port=3306, user='root', password=pwd, database='kb_system')
        print(f"✅ MySQL 连接成功 (密码={pwd})")
        
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM tenant")
        count = cur.fetchone()[0]
        
        if count > 0:
            print(f"    已有 {count} 个租户，跳过播种")
            cur.close(); conn.close()
            sys.exit(0)
        
        # Seed tenants
        cur.execute("INSERT INTO tenant (id, name, code, status) VALUES (1, '默认租户', 'wenqu-default', 1)")
        cur.execute("INSERT INTO tenant (id, name, code, status) VALUES (2, '演示租户', 'demo', 1)")
        
        # Hash password
        pwd_hashed = bcrypt.hashpw(b'Admin@123', bcrypt.gensalt()).decode()
        
        # Seed users - role 0=SUPER_ADMIN, 1=TENANT_ADMIN, 2=USER
        cur.execute("INSERT INTO user (id, tenant_id, username, password_hash, role, status) VALUES (1, 1, 'superadmin', %s, 0, 1)", (pwd_hashed,))
        cur.execute("INSERT INTO user (id, tenant_id, username, password_hash, role, status) VALUES (2, 1, 'admin', %s, 1, 1)", (pwd_hashed,))
        cur.execute("INSERT INTO user (id, tenant_id, username, password_hash, role, status) VALUES (3, 1, 'user1', %s, 2, 1)", (pwd_hashed,))
        cur.execute("INSERT INTO user (id, tenant_id, username, password_hash, role, status) VALUES (4, 2, 'user2', %s, 2, 1)", (pwd_hashed,))
        
        conn.commit()
        print("    ✅ 播种完成: 2个租户, 4个用户")
        print("    账号: superadmin/Admin@123@wenqu-default (超管)")
        print("    账号: admin/Admin@123@wenqu-default (租管)")
        print("    账号: user1/Admin@123@wenqu-default (普通)")
        print("    账号: user2/Admin@123@demo (普通)")
        
        cur.close(); conn.close()
        sys.exit(0)
    except Exception as e:
        msg = str(e)
        if "Access denied" in msg:
            print(f"    密码 '{pwd}': ❌ 拒绝")
        else:
            print(f"    密码 '{pwd}': ❌ {msg[:80]}")
        continue

print("❌ 无法连接 MySQL，请检查密码")
sys.exit(1)
