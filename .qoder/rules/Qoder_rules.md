---
trigger: always_on
---
# Qoder Project Rules — 企业智能知识库问答系统

## 0. 首要原则（最高优先级）

你是一个严格的代码实现者，不是架构设计者。
架构已经在《企业智能知识库_架构设计文档.docx》中完整定义。
你的唯一任务是：**按文档实现，不增加，不替换，不简化**。

当你不确定某个细节时，**停下来问我**，而不是自己猜测或补全。
输出代码前，先用一句话说明你要做什么，确认后再写。

---

## 1. 技术栈白名单（只能用这些，一个都不能换）

### 后端（Java）
- Spring Boot 3.2+（Java 17）
- Spring Cloud Gateway 4.x
- Spring AI 1.0+
- MyBatis-Plus（含租户拦截器 TenantLineInnerInterceptor）
- MySQL 8.0 + Redis 7.x
- Milvus Java SDK 2.4+
- MinIO Java SDK
- JWT：jjwt 0.12+
- Lombok（必须用，减少样板代码）

### Python 侧车
- FastAPI 0.111+
- pdfplumber（PDF文字提取）
- pytesseract + Pillow（OCR，仅扫描版）
- python-docx（Word解析）
- ragas（评估服务专用）
- uvicorn（服务器）

### 前端
- Vue 3.4+（Composition API + <script setup>）
- Vite 5+
- Element Plus（UI组件库）
- Pinia（状态管理）
- Axios（HTTP，统一封装）
- 原生 EventSource（SSE，禁止引入 socket.io 或其他库）

### 容器化
- Docker + Docker Compose
- Nginx（唯一对外入口）

### 禁止引入的依赖（黑名单）
- ❌ WebSocket / socket.io（用 SSE 代替）
- ❌ XXL-Job / Quartz / Spring Batch（用 @Async + async_task 表代替）
- ❌ Elasticsearch（用 Milvus 代替）
- ❌ RabbitMQ / Kafka（当前规模不需要）
- ❌ MyBatis（用 MyBatis-Plus 代替）
- ❌ Spring Security（手写 JWT 过滤器，不用 Security 框架）
- ❌ Flyway / Liquibase（手动管理 SQL 文件）
- ❌ OpenAI Java SDK（用 Spring AI 统一对接）

---

## 2. 数据库规范（严格遵守，不得新增或修改表结构）

### 已定义的 11 张表（不可新增、不可改字段名）
```
tenant / user / document / document_version / doc_chunk
conversation / message / async_task
eval_case / eval_batch / eval_result
```

### 字段命名规范
- 全部使用 snake_case（如 tenant_id，不是 tenantId）
- 主键统一命名 id，类型 BIGINT AUTO_INCREMENT
- 时间字段统一 created_at / updated_at，类型 DATETIME
- 状态字段用 VARCHAR 存枚举字符串（如 "PENDING"），不用数字

### 多租户强制规则
- 所有业务表的查询、插入、更新，必须带 tenant_id 条件
- 禁止写任何不带 tenant_id 的跨租户查询
- TenantContext.getTenantId() 从 ThreadLocal 获取，不允许从参数传入

### 枚举值（不得自行扩展）
```
UserRole:    0=SUPER_ADMIN  1=TENANT_ADMIN  2=USER
DocStatus:   PENDING / PARSING / EMBEDDING / READY / FAILED
TaskType:    DOC_PROCESS / RAGAS_EVAL
TaskStatus:  PENDING / RUNNING / DONE / FAILED
MessageRole: 0=user  1=assistant
```

---

## 3. API 规范（严格遵守路径和响应格式）

### 统一响应格式（任何接口都必须用这个）
```json
{"code": 0, "msg": "ok", "data": {}}
{"code": 1001, "msg": "错误描述", "data": null}
```

### 已定义的接口路径（不可改路径，不可新增未定义接口）
```
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/logout
POST   /api/auth/refresh

POST   /api/docs/upload
GET    /api/docs
GET    /api/docs/{id}
GET    /api/docs/{id}/versions
GET    /api/docs/{id}/download
PUT    /api/docs/{id}/expire
DELETE /api/docs/{id}

POST   /api/chat/conversations
GET    /api/chat/conversations
DELETE /api/chat/conversations/{id}
GET    /api/chat/conversations/{id}/messages
POST   /api/chat/conversations/{id}/ask       ← SSE接口

GET    /api/tasks/{taskId}/status

POST   /api/admin/tenants
GET    /api/admin/tenants
PUT    /api/admin/tenants/{id}/status
GET    /api/admin/users
PUT    /api/admin/users/{id}/role

GET    /api/eval/cases
POST   /api/eval/cases
DELETE /api/eval/cases/{id}
POST   /api/eval/run
GET    /api/eval/batches
GET    /api/eval/batches/{id}
```

### SSE 接口强制规范
- Content-Type 必须是 text/event-stream
- 只有两种事件：event:token 和 event:done
- event:token 的 data 是单个 token 字符串
- event:done 的 data 是 source_chunks JSON 数组
- 禁止用 WebSocket 替代

---

## 4. 核心业务逻辑规范（禁止改动）

### 4.1 文档版本管理
- 相同 tenant_id + 相同文件名 → 视为新版本，version_no 递增
- 每个文档最多保留 5 个版本，超出时删除 version_no 最小的版本
- 删除旧版本必须同步：① 删 MinIO 文件 ② 删 Milvus 向量 ③ 删 doc_chunk 记录 ④ 删 document_version 记录
- 删除顺序严格按上述顺序，不得颠倒

### 4.2 异步任务（不得用其他方案替代）
```
触发接口 → 写 async_task 表（PENDING）→ 立即返回 taskId
         → @Async 线程池异步执行
         → 每步完成更新 progress（10/30/60/90/100）
         → 成功：status=DONE；失败：status=FAILED + error_msg
前端每 2 秒轮询 GET /api/tasks/{taskId}/status
```

### 4.3 RAG 链路（步骤顺序不可调整）
```
1. 从 Redis 拉取最近5轮对话历史（滑动窗口）
2. DeepSeek 改写问题（消解指代词）
3. 智谱 embedding-3 向量化改写后的问题
4. Milvus 检索 Top-10（指定 tenant_{tenantId}_docs Collection）
5. 重排序取 Top-5
6. 构建 Prompt（系统提示 + 检索段落 + 历史 + 当前问题）
7. DeepSeek 流式生成，SSE 推送每个 token
8. 生成完毕，SSE 推送 event:done + source_chunks
9. 异步写 MySQL（message 表，含 source_chunks JSON）
10. 更新 Redis 滑动窗口
```

### 4.4 Redis 对话历史
- Key 格式：conv:{conversationId}:history
- 数据结构：Redis List（RPUSH 追加，LTRIM 保留最新10条）
- TTL：24小时，每次问答 EXPIRE 续期
- 冷启动：Redis 过期后从 MySQL message 表重建最近5轮

### 4.5 Milvus 租户隔离
- Collection 命名：tenant_{tenantId}_docs
- 每个租户建库时自动创建 Collection
- 向量维度：2048（智谱 embedding-3 固定输出）
- 禁止跨 Collection 检索

### 4.6 MinIO 租户隔离
- Bucket 命名：tenant-{tenantId}（小写，短横线，符合 MinIO 规范）
- 每个租户创建时自动创建 Bucket
- 禁止跨 Bucket 操作

---

## 5. 代码风格规范

### Java
```java
// 包名结构（严格遵守）
com.kb.gateway.filter.*
com.kb.common.dto.*
com.kb.common.enums.*
com.kb.app.config.*
com.kb.app.context.*
com.kb.app.interceptor.*
com.kb.app.module.auth.*
com.kb.app.module.document.*
com.kb.app.module.chat.*
com.kb.app.module.admin.*
com.kb.app.module.eval.*
com.kb.app.module.task.*
com.kb.app.rag.*

// 命名规范
- Controller 后缀：XxxController
- Service 接口：XxxService，实现类：XxxServiceImpl
- MyBatis-Plus Mapper：XxxMapper
- DTO 后缀：XxxDTO（请求）/ XxxVO（响应）
- 枚举类放 com.kb.common.enums

// 必须使用 Lombok
@Data @Builder @NoArgsConstructor @AllArgsConstructor

// 异常处理
- 业务异常统一抛出 BusinessException(code, message)
- GlobalExceptionHandler 统一捕获，返回 Result.fail()

// 事务
- Service 层写操作加 @Transactional
- 异步方法（@Async）不加 @Transactional（事务在异步线程中无效）
```

### Python
```python
# 文件命名：snake_case
# 函数命名：snake_case
# 类命名：PascalCase
# 每个函数必须有类型注解和 docstring
# FastAPI 路由函数必须写 response_model

# 禁止裸 except，必须捕获具体异常
# 日志用 logging 模块，不用 print
```

### Vue3
```javascript
// 全部使用 <script setup> 语法，禁止 Options API
// 组件命名：PascalCase（如 ChatWindow.vue）
// Composable 命名：use 前缀（如 useChat.js）
// API 调用统一走 src/api/ 目录封装，禁止在组件内直接写 axios
// 环境变量用 import.meta.env.VITE_*，不用 process.env
```

---

## 6. 安全规范（不得省略）

- 所有接口（除 /api/auth/register 和 /api/auth/login）必须鉴权
- JWT accessToken 有效期 1 小时，refreshToken 有效期 7 天
- 登出时 accessToken 加入 Redis 黑名单（Key: jwt:blacklist:{token}，TTL=剩余有效期）
- 密码必须 BCrypt 加密存储，禁止明文
- MinIO 下载不返回永久链接，返回预签名 URL（有效期 15 分钟）
- 所有用户输入做基础校验（@Valid + jakarta.validation 注解）
- SQL 禁止字符串拼接，全部用 MyBatis-Plus 参数绑定

---

## 7. 禁止行为清单（Cursor 必须遵守）

```
❌ 禁止新增文档中未定义的数据库表或字段
❌ 禁止替换已定义的技术组件（如用 ES 替换 Milvus）
❌ 禁止在业务代码中硬编码 API Key（必须读环境变量）
❌ 禁止生成 Mock 数据或 TODO 占位代码作为最终交付
❌ 禁止用 System.out.println 调试，用 Slf4j @Slf4j + log.info
❌ 禁止跨租户查询（任何 SELECT 必须带 tenant_id）
❌ 禁止在 Controller 层写业务逻辑
❌ 禁止在 @Async 方法内使用 @Transactional
❌ 禁止用轮询替代 SSE（对话接口必须是 SSE）
❌ 禁止生成与当前 Step 无关的代码（一次只做一个模块）
❌ 禁止自行决定"这个功能可以简化"，简化前必须告知我
```

---

## 8. 每次编码前的自检清单

在生成任何代码前，Cursor 必须默认完成以下检查：

```
□ 这个功能在架构文档中是否有定义？
□ 使用的技术是否在白名单内？
□ 涉及数据库操作是否带了 tenant_id？
□ 涉及文件操作是否校验了 Bucket 归属？
□ 接口路径是否与文档定义完全一致？
□ 响应格式是否是 {"code","msg","data"} 结构？
□ 是否有未处理的异常（空指针、类型转换等）？
□ API Key 是否从环境变量读取？
```

---

## 9. 遇到不确定的情况怎么办

按以下优先级处理：

1. **查架构文档** — 答案通常在文档里
2. **停下来问我** — 说清楚"我在做XX，遇到了YY，我有两个方案A和B，你选哪个"
3. **绝对不允许** — 自己猜测后直接生成代码，然后假装没问题

不确定的信号包括：
- 文档里没有提到这个细节
- 有多种实现方式，不知道选哪个
- 新的依赖需要引入
- 需要修改已有的表结构或接口

---

## 10. 开发顺序（严格按此顺序，不得跳步）

```
Step 1  建表 SQL + MyBatis-Plus Mapper + 租户拦截器
Step 2  用户认证：注册/登录/JWT/Gateway过滤器
Step 3  文档上传 + MinIO存储 + 版本管理
Step 4  Python解析侧车（kb-parser）
Step 5  Embedding接入 + Milvus向量存储
Step 6  RAG问答链路 + SSE推流
Step 7  异步任务：async_task表 + @Async + 状态轮询
Step 8  前端Vue3：登录→对话→文档管理→评估面板
Step 9  Python Ragas评估侧车（kb-ragas）
Step 10 Docker Compose容器化
```

**当前应该做哪个 Step，由我来告诉你。你不要主动跳到下一个 Step。**
