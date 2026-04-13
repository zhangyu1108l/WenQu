# 问渠（WenQu）— 企业智能知识库问答系统
# 架构设计文档 v2.0 | Qoder 上下文专用

> 本文档是 Qoder AI 的上下文参考文件，放置于 `.qoder/rules/architecture.md`。
> 编码时严格按照本文档实现，不得自行更改任何技术选型、数据库结构和接口定义。

---

## 一、技术栈（白名单，不可替换）

| 层次 | 技术 | 版本 |
|------|------|------|
| 前端 | Vue3 + Vite + Element Plus | Vue 3.4+ |
| 网关 | Spring Cloud Gateway | 4.x |
| 主服务 | Spring Boot 3 | 3.2+，Java 17 |
| AI框架 | Spring AI | 1.0+ |
| 文档解析侧车 | Python FastAPI | 端口 8090 |
| Ragas评估侧车 | Python FastAPI | 端口 8091 |
| 对话LLM | DeepSeek Chat | deepseek-chat |
| Embedding | 智谱 embedding-3 | 维度 2048 |
| 向量库 | Milvus | 2.4+ |
| 关系库 | MySQL | 8.0+ |
| 缓存 | Redis | 7.x |
| 对象存储 | MinIO | 最新版 |
| 容器化 | Docker + Docker Compose | 10个容器 |

### 禁止引入的依赖
- ❌ WebSocket / socket.io（用 SSE 代替）
- ❌ XXL-Job / Quartz / Spring Batch（用 @Async + async_task 表代替）
- ❌ Elasticsearch（用 Milvus 代替）
- ❌ RabbitMQ / Kafka（当前规模不需要）
- ❌ Spring Security（手写 JWT 过滤器）
- ❌ MyBatis（用 MyBatis-Plus 代替）

---

## 二、系统分层架构

```
前端层      Vue3 SPA — 对话界面 / 文档管理 / 管理员评估面板
网关层      Spring Cloud Gateway — JWT校验 / X-Tenant-Id注入 / 限流
Java主服务  Spring Boot 3 单体 — 用户/文档/对话/管理/评估 五个模块
Python侧车  FastAPI x2 — 文档解析:8090 + Ragas评估:8091
AI引擎层    Spring AI — Embedding向量化 / RAG检索链 / DeepSeek调用
存储层      MySQL + Redis + Milvus + MinIO
```

### 多租户隔离规则（强制）
- **数据库层**：MyBatis-Plus `TenantLineInnerInterceptor` 自动追加 `AND tenant_id = ?`
- **向量库层**：每租户独立 Collection，命名规则 `tenant_{tenantId}_docs`
- **文件存储层**：每租户独立 Bucket，命名规则 `tenant-{tenantId}`（小写+短横线）
- `tenantId` 从 `ThreadLocal`（`TenantContext`）读取，由 Gateway Filter 注入，业务层不传参

### 角色权限
| 角色 | 标识 | 权限 |
|------|------|------|
| 超级管理员 | SUPER_ADMIN | 创建/禁用租户、查看所有数据 |
| 租户管理员 | TENANT_ADMIN | 管理本租户用户、上传删除文档、运行评估 |
| 普通用户 | USER | 本租户内对话问答、查看自己会话 |

---

## 三、数据库设计（11张表，禁止新增或修改）

### 枚举值（不得自行扩展）
```
UserRole:    0=SUPER_ADMIN  1=TENANT_ADMIN  2=USER
DocStatus:   PENDING / PARSING / EMBEDDING / READY / FAILED
TaskType:    DOC_PROCESS / RAGAS_EVAL
TaskStatus:  PENDING / RUNNING / DONE / FAILED
MessageRole: 0=user  1=assistant
```

### 3.1 租户与用户

```sql
CREATE TABLE tenant (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL COMMENT '租户名称',
    code       VARCHAR(50)  NOT NULL UNIQUE COMMENT '唯一标识，如 acme-tech',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL COMMENT 'bcrypt 加密',
    role          TINYINT      NOT NULL DEFAULT 2
                  COMMENT '0=超级管理员 1=租户管理员 2=普通用户',
    status        TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_username (tenant_id, username)
);
```

### 3.2 文档管理

```sql
CREATE TABLE document (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL,
    uploader_id BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL COMMENT '文件名（去扩展名）',
    file_type   VARCHAR(10)  NOT NULL COMMENT 'pdf / docx',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                COMMENT 'PENDING/PARSING/EMBEDDING/READY/FAILED',
    expire_at   DATETIME     NULL COMMENT '原始文件过期时间，NULL=永不过期',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_version (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id  BIGINT       NOT NULL,
    version_no   INT          NOT NULL COMMENT '从1递增，保留最近5个',
    minio_bucket VARCHAR(100) NOT NULL,
    minio_key    VARCHAR(500) NOT NULL,
    file_size    BIGINT       NOT NULL,
    is_active    TINYINT      NOT NULL DEFAULT 1 COMMENT '1=当前激活版本',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_ver (document_id, version_no)
);

CREATE TABLE doc_chunk (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id  BIGINT       NOT NULL,
    version_id   BIGINT       NOT NULL,
    tenant_id    BIGINT       NOT NULL,
    chunk_index  INT          NOT NULL,
    content      TEXT         NOT NULL COMMENT '原始文本，用于来源引用展示',
    heading_path VARCHAR(500) NULL     COMMENT '如：第5章>5.3节>年假规定',
    page_no      INT          NULL     COMMENT 'PDF页码，Word为NULL',
    milvus_id    VARCHAR(100) NOT NULL COMMENT 'Milvus向量ID，删除时同步清理',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 3.3 对话消息

```sql
CREATE TABLE conversation (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    tenant_id  BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '首条问题自动截取前20字',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT   NOT NULL,
    role            TINYINT  NOT NULL COMMENT '0=user  1=assistant',
    content         LONGTEXT NOT NULL,
    source_chunks   JSON     NULL
    COMMENT '格式：[{chunkId,content,headingPath,pageNo,score}]',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 3.4 异步任务

```sql
CREATE TABLE async_task (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type  VARCHAR(32)  NOT NULL COMMENT 'DOC_PROCESS / RAGAS_EVAL',
    biz_id     BIGINT       NOT NULL COMMENT '文档ID 或 eval_batch_id',
    tenant_id  BIGINT       NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
               COMMENT 'PENDING / RUNNING / DONE / FAILED',
    progress   TINYINT      NOT NULL DEFAULT 0 COMMENT '0~100',
    error_msg  VARCHAR(500) NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
               ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.5 Ragas 评估

```sql
CREATE TABLE eval_case (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id    BIGINT NOT NULL,
    question     TEXT   NOT NULL,
    ground_truth TEXT   NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE eval_batch (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id             BIGINT NOT NULL,
    case_count            INT    NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    avg_faithfulness      FLOAT NULL,
    avg_answer_relevancy  FLOAT NULL,
    avg_context_recall    FLOAT NULL,
    avg_context_precision FLOAT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE eval_result (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id          BIGINT NOT NULL,
    eval_case_id      BIGINT NOT NULL,
    model_answer      TEXT   NOT NULL,
    faithfulness      FLOAT  NULL,
    answer_relevancy  FLOAT  NULL,
    context_recall    FLOAT  NULL,
    context_precision FLOAT  NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 四、API 接口（路径已定义，不可新增或修改）

统一响应格式：`{"code":0,"msg":"ok","data":{}}`
鉴权：`Authorization: Bearer {jwt}`，网关注入 `X-User-Id` / `X-Tenant-Id`

### 认证 /api/auth
```
POST /api/auth/register     公开      body: {tenantCode, username, password}
POST /api/auth/login        公开      返回 accessToken + refreshToken
POST /api/auth/logout       登录用户   JWT加入Redis黑名单
POST /api/auth/refresh      登录用户   换新 accessToken
```

### 文档 /api/docs
```
POST   /api/docs/upload              租户管理员  multipart上传，返回 {docId, taskId}
GET    /api/docs                     登录用户    分页列表 ?keyword=&page=&size=
GET    /api/docs/{id}                登录用户    文档详情+当前激活版本
GET    /api/docs/{id}/versions       登录用户    历史版本列表（最多5个）
GET    /api/docs/{id}/download       登录用户    MinIO预签名URL，有效期15分钟
PUT    /api/docs/{id}/expire         租户管理员  设置过期时间
DELETE /api/docs/{id}                租户管理员  删除文档+同步清理Milvus向量
```

### 对话 /api/chat
```
POST   /api/chat/conversations                   登录用户  新建会话
GET    /api/chat/conversations                   登录用户  会话列表
DELETE /api/chat/conversations/{id}              登录用户  删除会话
GET    /api/chat/conversations/{id}/messages     登录用户  完整消息历史
POST   /api/chat/conversations/{id}/ask          登录用户  SSE流式问答 ⚠️
```
> ⚠️ /ask 接口：Content-Type: text/event-stream，event:token 推每个字符，event:done 推 source_chunks JSON

### 任务状态 /api/tasks
```
GET /api/tasks/{taskId}/status    登录用户  返回 {status, progress, errorMsg}
```

### 管理 /api/admin
```
POST /api/admin/tenants                超级管理员  创建租户
GET  /api/admin/tenants                超级管理员  租户列表
PUT  /api/admin/tenants/{id}/status    超级管理员  启用/禁用
GET  /api/admin/users                  管理员      用户列表
PUT  /api/admin/users/{id}/role        租户管理员  修改角色
```

### 评估 /api/eval
```
GET    /api/eval/cases           管理员  用例列表
POST   /api/eval/cases           管理员  新增用例 {question, groundTruth}
DELETE /api/eval/cases/{id}      管理员  删除用例
POST   /api/eval/run             管理员  触发评估，返回 {batchId, taskId}
GET    /api/eval/batches         管理员  批次历史列表
GET    /api/eval/batches/{id}    管理员  批次详情+各用例指标
```

---

## 五、核心业务逻辑

### 5.1 文档上传异步流程
```
① 接口接收文件 → 判断同名文档（新建 or 新版本）
② 写 async_task 表（status=PENDING）
③ 立即返回 {docId, taskId}
④ @Async 线程池执行：
   10% → 存 MinIO（tenant-{tenantId} Bucket）
   30% → 调 Python 解析侧车 POST http://parser:8090/parse
   60% → 批量调智谱 Embedding → 写 Milvus + 写 doc_chunk 表
   90% → 更新 document.status = READY
  100% → async_task.status = DONE
⑤ 检查版本数 > 5 → 删除最旧版本（MinIO + Milvus + doc_chunk + document_version）
```

### 5.2 RAG 问答链路（步骤顺序不可调整）
```
① Redis 拉取最近5轮对话历史（Key: conv:{convId}:history）
② DeepSeek 改写问题（消解指代词）
③ 智谱 embedding-3 向量化改写后的问题
④ Milvus 检索 Top-10（Collection: tenant_{tenantId}_docs）
⑤ 重排序取 Top-5
⑥ 构建 Prompt = 系统提示 + 检索段落 + 历史 + 当前问题
⑦ DeepSeek 流式生成 → SSE event:token 逐字推送
⑧ 生成完毕 → SSE event:done 携带 source_chunks JSON
⑨ 异步写 MySQL（message 表，含 source_chunks）
⑩ 更新 Redis 滑动窗口（RPUSH + LTRIM 保留10条 + EXPIRE 24h）
```

### 5.3 版本删除规则
删除旧版本必须按顺序：
1. 删 MinIO 文件
2. 删 Milvus 向量（通过 milvus_id 批量删除）
3. 删 doc_chunk 记录
4. 删 document_version 记录

### 5.4 Redis 滑动窗口
```
Key:    conv:{conversationId}:history
结构:   Redis List
操作:   RPUSH 追加 → LTRIM 保留最新10条（5轮）→ EXPIRE 86400
冷启动: Redis过期后从 MySQL message 表重建最近5轮
```

---

## 六、Python 侧车接口

### 文档解析侧车（:8090）
```
POST /parse
Request:  multipart/form-data  file=<bytes>  file_type=pdf|docx
Response: {"chunks": [{
    "content":      "文本内容",
    "chunk_index":  0,
    "heading_path": "第5章>5.3节",   // 无标题则 null
    "page_no":      12,               // Word文档则 null
    "char_count":   248,
    "chunk_type":   "heading|paragraph|length"
}]}
```

**分块策略（三级降级，不可修改）**
1. 标题分块：有 Word 标题样式 / PDF 书签 → 以 H1/H2 为边界
2. 段落分块：有双换行符 → 以双换行为边界
3. 长度兜底：512 token 切分，50 token 重叠

**PDF 特殊处理**
- 每页字符数 < 100 → 判定扫描版 → pytesseract chi_sim OCR
- 表格 → pdfplumber 提取 → 转 Markdown → 整表作为单个 chunk

### Ragas 评估侧车（:8091）
```
POST /evaluate
Request: {
    "batch_id": 123,
    "cases": [{"case_id":1, "question":"...", "ground_truth":"..."}],
    "callback_url": "http://app:8081/internal/eval/callback"
}
流程: 对每个case跑完整RAG → 计算4个Ragas指标 → POST callback_url写回MySQL
```

**四个评估指标**
| 指标 | 含义 |
|------|------|
| Faithfulness | 回答是否忠实于检索内容 |
| Answer Relevancy | 回答是否切题 |
| Context Recall | ground truth 是否被检索覆盖 |
| Context Precision | 检索内容是否都有用 |

---

## 七、项目目录结构

```
kb-system/
├── kb-gateway/
│   └── filter/
│       ├── JwtAuthFilter.java        # JWT校验，写入 X-User-Id/X-Tenant-Id
│       └── TenantRouteFilter.java
├── kb-common/
│   ├── dto/                          # Result<T>、PageDTO
│   ├── enums/                        # TaskType、TaskStatus、UserRole
│   └── exception/                    # GlobalExceptionHandler、BusinessException
└── kb-app/
    ├── config/
    │   ├── SpringAiConfig.java       # 智谱Embedding + DeepSeek Chat 双配置
    │   ├── MilvusConfig.java
    │   ├── MinioConfig.java
    │   └── AsyncConfig.java          # @Async 线程池
    ├── interceptor/
    │   └── TenantInterceptor.java    # MyBatis-Plus 租户拦截器
    ├── context/
    │   └── TenantContext.java        # ThreadLocal 存储 tenantId/userId
    └── module/
        ├── auth/                     # 注册、登录、JWT工具
        ├── document/                 # 上传、版本管理、MinIO
        ├── chat/                     # 会话管理、SSE推流、消息存储
        ├── admin/                    # 租户管理、用户管理
        ├── eval/                     # 评估用例、触发评估、报告
        ├── task/                     # 任务状态查询
        └── rag/
            ├── DocumentPipeline.java  # 文档处理管道
            ├── QueryRewriter.java     # 问题改写
            ├── VectorRetriever.java   # Milvus检索+重排
            └── RagChain.java          # 完整RAG链路

kb-parser/                            # Python 文档解析侧车
├── main.py                           # FastAPI 入口 :8090
├── parser/
│   ├── pdf_parser.py
│   └── word_parser.py
└── chunker/
    └── smart_chunker.py

kb-ragas/                             # Python Ragas评估侧车
├── main.py                           # FastAPI 入口 :8091
└── evaluator.py
```

---

## 八、Docker Compose 服务

| 服务名 | 镜像 | 端口 |
|--------|------|------|
| nginx | nginx:alpine | 80:80（唯一对外） |
| gateway | 自建Java镜像 | 内部8080 |
| app | 自建Java镜像 | 内部8081 |
| frontend | 自建Vue镜像 | 内部3000 |
| parser | 自建Python镜像 | 内部8090 |
| ragas-svc | 自建Python镜像 | 内部8091 |
| mysql | mysql:8.0 | 内部3306 |
| redis | redis:7-alpine | 内部6379 |
| milvus | milvusdb/milvus:v2.4 | 内部19530 |
| minio | minio/minio | 内部9000 |

所有服务在 Docker 网络 `kb-net` 中通过服务名互访，如 `http://parser:8090`。

---

## 九、开发顺序（严格按此顺序）

```
Step 1  建表SQL + MyBatis-Plus Mapper + 租户拦截器
Step 2  用户认证：注册/登录/JWT/Gateway过滤器
Step 3  文档上传 + MinIO + 版本管理
Step 4  Python 解析侧车（kb-parser）
Step 5  Embedding接入 + Milvus向量存储
Step 6  RAG问答链路 + SSE推流
Step 7  异步任务：async_task + @Async + 状态轮询
Step 8  前端Vue3：登录→对话→文档管理→评估面板
Step 9  Python Ragas评估侧车（kb-ragas）
Step 10 Docker Compose容器化
```

**当前做哪个Step由用户指定，不得主动跳步。**
