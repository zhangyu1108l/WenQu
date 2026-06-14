# 问渠部署与端到端验证指南

本文档面向第一次接触问渠项目的部署人员。部署前请确认本机已安装 Docker Compose，并且 80 端口未被其他程序占用。

## 1. 启动步骤

```bash
git clone <项目仓库地址>
cd WenQu
cp .env.example .env
```

打开 `.env`，填写或修改以下关键配置：

- `JWT_SECRET`：网关和主服务共用的 JWT 密钥，建议使用 32 位以上随机字符串。
- `DEEPSEEK_API_KEY`：DeepSeek Chat API Key。
- `ZHIPU_API_KEY`：智谱 embedding-3 API Key。
- `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`MINIO_ROOT_PASSWORD`：数据库和对象存储密码。

填写完成后启动：

```bash
./start.sh
```

如果当前环境不能直接执行 shell 脚本，也可以执行：

```bash
docker compose up -d --build
```

## 2. 端到端验证清单

请按顺序执行以下检查，前一项未通过时先排查再继续。

- [ ] `docker compose ps` 中全部服务状态为 `healthy` 或 `running`。
- [ ] 浏览器访问 `http://localhost`，能看到登录页。
- [ ] 用超级管理员账号登录。

  超级管理员账号需要手动在数据库初始化。下面 SQL 仅为示例，密码哈希请替换为实际 bcrypt 值：

  ```sql
  INSERT INTO tenant (id, name, code, status)
  VALUES (1, '系统租户', 'system', 1);

  INSERT INTO user (tenant_id, username, password_hash, role, status)
  VALUES (1, 'superadmin', '$2a$10$replace_with_real_bcrypt_hash', 0, 1);
  ```

- [ ] 创建一个新租户。
- [ ] 用租户管理员登录。
- [ ] 上传一个 PDF 文档，观察进度条从 `PENDING` 到 `RUNNING` 再到 `DONE`。
- [ ] 文档状态变为“可用”。
- [ ] 新建对话，提问文档相关问题。
- [ ] 观察 SSE 流式输出是否逐字显示，而不是等待很久后一次性出现。
- [ ] 回答下方是否显示来源引用卡片。
- [ ] 追问一个带指代词的问题，验证 Query 改写是否生效。
- [ ] 进入评估中心，添加评估用例。
- [ ] 点击运行评估，观察任务状态变化。
- [ ] 评估完成后查看四项指标报告。

## 3. 常见问题排查表

| 现象 | 可能原因 | 排查方法 |
|------|---------|---------|
| milvus 容器一直 unhealthy | milvus-etcd 或 milvus-minio 未就绪 | 执行 `docker compose logs milvus`，查看连接错误 |
| app 容器反复重启 | 等待 MySQL 初始化未完成就连接 | 检查 `depends_on` 的 `condition` 是否是 `service_healthy` |
| SSE 没有逐字效果，等很久后一次性返回 | nginx 缓冲未关闭 | 检查 `nginx/nginx.conf` 的 `proxy_buffering off` 是否生效，检查 `location` 正则是否匹配到了 `/api/chat/conversations/{id}/ask` 路径 |
| 文档上传后状态一直 PENDING | parser 服务未就绪或地址错误 | 执行 `docker compose logs parser`，检查 `kb-system/kb-app/src/main/resources/application-docker.yml` 中 parser 地址 |
| 评估一直 RUNNING 不结束 | ragas-svc 无法回调 app | 执行 `docker compose logs ragas-svc`，检查 `JAVA_APP_BASE_URL` 是否是 `http://app:8081` |
| 前端 404 或空白页 | Vue Router history 模式未配置 `try_files` | 检查 `kb-frontend/nginx.conf` |
| 登录后 401 | `JWT_SECRET` 在 gateway 和 app 配置不一致 | 检查两个 `application-docker.yml` 的 `jwt.secret` 是否都引用同一个 `${JWT_SECRET}`；如果日志仍无法定位，需要根据实际日志进一步排查 |

## 4. 数据卷管理说明

Docker Compose 会创建以下数据卷：

- `mysql-data`：存储租户、用户、文档元数据、对话、任务和评估等业务数据。
- `minio-data`：存储用户上传的文档原文件。
- `milvus-data`：存储 Milvus 向量库运行数据。
- `milvus-etcd-data`：存储 Milvus 使用的 etcd 元数据。
- `milvus-minio-data`：存储 Milvus 内部对象数据。

如果需要清空所有容器和数据卷，执行：

```bash
docker compose down -v
```

该命令会删除业务数据、上传文件和向量数据。执行前请确认已经备份重要数据。
