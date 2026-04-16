-- ============================================================
-- 问渠（WenQu）企业智能知识库问答系统
-- 数据库初始化脚本 v2.0
-- 说明：所有业务表均含 tenant_id 字段，通过 MyBatis-Plus
--       TenantLineInnerInterceptor 自动注入租户隔离条件
-- 执行顺序：按文件从上到下顺序执行，不可乱序
-- ============================================================

CREATE DATABASE IF NOT EXISTS kb_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kb_system;


-- ============================================================
-- 模块一：租户与用户
-- ============================================================

-- ------------------------------------------------------------
-- 租户表
-- 每个租户代表一个独立的公司或组织
-- 租户之间数据完全隔离，互不可见
-- ------------------------------------------------------------
CREATE TABLE tenant
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '租户主键',
    name       VARCHAR(100) NOT NULL COMMENT '租户名称，如：阿里巴巴',
    code       VARCHAR(50)  NOT NULL UNIQUE COMMENT '租户唯一标识（英文），如：alibaba，用于登录时指定租户',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用  0=禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '租户表 — 每行代表一个独立的公司或组织';


-- ------------------------------------------------------------
-- 用户表
-- 用户必须归属于某个租户，不同租户下可以有同名用户
-- 角色说明：
--   0 = 超级管理员（平台级，管理所有租户，全局唯一）
--   1 = 租户管理员（租户级，管理本租户的用户和文档）
--   2 = 普通用户（只能在本租户内进行对话问答）
-- ------------------------------------------------------------
CREATE TABLE user
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户主键',
    tenant_id     BIGINT       NOT NULL COMMENT '所属租户ID，关联 tenant.id',
    username      VARCHAR(50)  NOT NULL COMMENT '用户名（租户内唯一）',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值，使用 BCrypt 加密，禁止存明文',
    role          TINYINT      NOT NULL DEFAULT 2 COMMENT '角色：0=超级管理员  1=租户管理员  2=普通用户',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用  0=禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_tenant_username (tenant_id, username) COMMENT '同一租户内用户名唯一'
) COMMENT '用户表 — 用户归属租户，角色决定操作权限';


-- ============================================================
-- 模块二：文档管理
-- 设计说明：
--   document       = 逻辑文档（一个文件名对应一条记录）
--   document_version = 物理版本（同一文档可有多个版本，最多保留5个）
--   doc_chunk      = 文档分块（向量化的最小单元，存储原文和向量ID）
-- ============================================================

-- ------------------------------------------------------------
-- 逻辑文档表
-- 同一租户下，相同文件名的多次上传视为同一文档的不同版本
-- status 字段驱动异步处理状态机：
--   PENDING   → 已创建，等待处理
--   PARSING   → Python 侧车正在解析文档
--   EMBEDDING → 正在调用智谱 Embedding 并写入 Milvus
--   READY     → 处理完成，可以参与对话检索
--   FAILED    → 处理失败，error 信息记录在 async_task 表
-- ------------------------------------------------------------
CREATE TABLE document
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档主键',
    tenant_id   BIGINT       NOT NULL COMMENT '所属租户ID',
    uploader_id BIGINT       NOT NULL COMMENT '上传用户ID，关联 user.id',
    title       VARCHAR(255) NOT NULL COMMENT '文档标题（文件名去掉扩展名）',
    file_type   VARCHAR(10)  NOT NULL COMMENT '文件类型：pdf / docx',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/PARSING/EMBEDDING/READY/FAILED',
    expire_at   DATETIME     NULL COMMENT '原始文件过期时间，到期后自动删除 MinIO 文件；NULL 表示永不过期',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_status (tenant_id, status) COMMENT '按租户+状态查询的索引'
) COMMENT '逻辑文档表 — 一行代表一个文档，多次上传同名文件会创建新版本';


-- ------------------------------------------------------------
-- 文档版本表
-- 每个文档最多保留最近 5 个版本
-- 超出时，后台任务自动删除最旧版本（同步清理 MinIO + Milvus + doc_chunk）
-- is_active = 1 表示当前对话检索使用的激活版本
-- ------------------------------------------------------------
CREATE TABLE document_version
(
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '版本主键',
    document_id  BIGINT       NOT NULL COMMENT '所属文档ID，关联 document.id',
    version_no   INT          NOT NULL COMMENT '版本号，从 1 开始递增',
    minio_bucket VARCHAR(100) NOT NULL COMMENT 'MinIO Bucket 名称，格式：tenant-{tenantId}',
    minio_key    VARCHAR(500) NOT NULL COMMENT 'MinIO 对象路径，格式：docs/{docId}/v{versionNo}/{filename}',
    file_size    BIGINT       NOT NULL COMMENT '文件大小（字节）',
    is_active    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否为激活版本：1=是  0=否，每个文档同时只有一个激活版本',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    UNIQUE KEY uk_doc_version (document_id, version_no) COMMENT '同一文档版本号唯一'
) COMMENT '文档版本表 — 同一文档可有多个版本，最多保留5个，超出自动删除最旧版本';


-- ------------------------------------------------------------
-- 文档分块表
-- 文档解析后被切分为若干 chunk，每个 chunk 独立向量化存入 Milvus
-- heading_path：记录该 chunk 所在的标题路径，用于来源引用展示
--   示例：第5章 薪酬福利 > 5.3 年假制度
-- milvus_id：Milvus 中对应向量的 ID，删除文档时通过此字段同步删除向量
-- ------------------------------------------------------------
CREATE TABLE doc_chunk
(
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'chunk 主键',
    document_id  BIGINT       NOT NULL COMMENT '所属文档ID',
    version_id   BIGINT       NOT NULL COMMENT '所属版本ID，关联 document_version.id',
    tenant_id    BIGINT       NOT NULL COMMENT '所属租户ID（冗余字段，加速按租户查询）',
    chunk_index  INT          NOT NULL COMMENT '在文档中的顺序，从 0 开始',
    content      TEXT         NOT NULL COMMENT '原始文本内容，用于前端来源引用段落高亮展示',
    heading_path VARCHAR(500) NULL COMMENT '标题路径，如：第5章>5.3节>年假规定；无标题结构时为 NULL',
    page_no      INT          NULL COMMENT 'PDF 页码；Word 文档无页码概念，为 NULL',
    milvus_id    VARCHAR(100) NOT NULL COMMENT 'Milvus 中的向量 ID，文档删除时通过此 ID 同步删除向量',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_doc (tenant_id, document_id) COMMENT '按租户+文档查询的索引'
) COMMENT '文档分块表 — 文档切分后的最小单元，每个 chunk 对应 Milvus 中的一条向量';


-- ============================================================
-- 模块三：对话消息
-- 设计说明：
--   conversation = 一次对话会话（每次点击"新建对话"创建一条）
--   message      = 会话内的单条消息（用户问 or AI答）
-- ============================================================

-- ------------------------------------------------------------
-- 会话表
-- 每个会话是独立的对话上下文，会话之间历史不互通
-- title 默认为"新对话"，首条问题发出后自动截取前20字更新
-- ------------------------------------------------------------
CREATE TABLE conversation
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话主键',
    user_id    BIGINT       NOT NULL COMMENT '创建该会话的用户ID',
    tenant_id  BIGINT       NOT NULL COMMENT '所属租户ID',
    title      VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题，首条问题发送后自动截取前20字更新',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_tenant (user_id, tenant_id) COMMENT '按用户+租户查询会话列表的索引'
) COMMENT '会话表 — 每行代表一次独立的对话，会话间历史完全隔离';


-- ------------------------------------------------------------
-- 消息表
-- 存储会话内的完整对话历史（永久保存到 MySQL）
-- Redis 只缓存最近5轮（10条）用于 RAG 上下文构建，过期后从此表重建
-- source_chunks：AI 回答时引用的原文 chunk 列表，JSON 格式
--   [{chunkId, content, headingPath, pageNo, score}]
--   前端根据此字段渲染来源引用卡片和段落高亮
-- ------------------------------------------------------------
CREATE TABLE message
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息主键',
    conversation_id BIGINT   NOT NULL COMMENT '所属会话ID，关联 conversation.id',
    role            TINYINT  NOT NULL COMMENT '消息角色：0=用户提问  1=AI回答',
    content         LONGTEXT NOT NULL COMMENT '消息正文内容',
    source_chunks   JSON     NULL COMMENT 'AI回答时引用的原文chunk列表，格式：[{chunkId,content,headingPath,pageNo,score}]；用户消息此字段为 NULL',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation (conversation_id) COMMENT '按会话查询消息历史的索引'
) COMMENT '消息表 — 存储会话内所有对话记录，AI回答附带来源引用信息';


-- ============================================================
-- 模块四：异步任务
-- 统一管理两类慢任务：
--   DOC_PROCESS = 文档处理（解析 → Embedding → 入库）
--   RAGAS_EVAL  = Ragas 质量评估
-- 设计思路：接口触发后立即返回 taskId，前端每2秒轮询此表状态
-- ============================================================

-- ------------------------------------------------------------
-- 异步任务表
-- 文档处理和 Ragas 评估共用此表，通过 task_type 区分
-- biz_id：关联业务主键
--   task_type=DOC_PROCESS 时，biz_id = document.id
--   task_type=RAGAS_EVAL  时，biz_id = eval_batch.id
-- progress：0~100 的进度百分比，前端用于展示进度条
--   文档处理各阶段进度：存MinIO=30 / 解析=60 / Embedding=90 / 完成=100
-- ------------------------------------------------------------
CREATE TABLE async_task
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务主键',
    task_type  VARCHAR(32)  NOT NULL COMMENT '任务类型：DOC_PROCESS=文档处理  RAGAS_EVAL=评估任务',
    biz_id     BIGINT       NOT NULL COMMENT '关联业务ID：文档处理时为 document.id，评估时为 eval_batch.id',
    tenant_id  BIGINT       NOT NULL COMMENT '所属租户ID',
    status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING=待执行  RUNNING=执行中  DONE=成功  FAILED=失败',
    progress   TINYINT      NOT NULL DEFAULT 0 COMMENT '执行进度：0~100，前端用于展示进度条',
    error_msg  VARCHAR(500) NULL COMMENT '失败时的错误信息，成功时为 NULL',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，每次状态变更自动刷新',
    INDEX idx_tenant_type (tenant_id, task_type) COMMENT '按租户+类型查询任务的索引'
) COMMENT '异步任务表 — 统一管理文档处理和Ragas评估两类慢任务，前端2秒轮询状态';


-- ============================================================
-- 模块五：Ragas 质量评估
-- 设计说明：
--   eval_case   = 评估用例（管理员手动录入问题和标准答案）
--   eval_batch  = 评估批次（每次点击"运行评估"创建一条）
--   eval_result = 评估结果明细（每个用例对应一条结果）
-- 指标说明：
--   faithfulness      = 忠实度：AI回答是否忠实于检索内容（0~1，越高越好）
--   answer_relevancy  = 答案相关性：AI回答是否切题（0~1，越高越好）
--   context_recall    = 上下文召回率：ground truth 是否被检索覆盖（0~1，越高越好）
--   context_precision = 上下文精准度：检索内容是否都有用（0~1，越高越好）
-- ============================================================

-- ------------------------------------------------------------
-- 评估用例表
-- 管理员手动录入，每条用例包含一个问题和对应的标准答案
-- 标准答案（ground_truth）是 Ragas 计算 context_recall 等指标的依据
-- ------------------------------------------------------------
CREATE TABLE eval_case
(
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用例主键',
    tenant_id    BIGINT   NOT NULL COMMENT '所属租户ID',
    question     TEXT     NOT NULL COMMENT '评估问题，如：员工年假天数如何计算？',
    ground_truth TEXT     NOT NULL COMMENT '标准答案，由管理员手动编写，作为评估基准',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '评估用例表 — 管理员手动录入的问答对，用于衡量RAG系统回答质量';


-- ------------------------------------------------------------
-- 评估批次表
-- 每次运行评估创建一条批次记录
-- 四个 avg_* 字段存储本次批次所有用例的指标均值，用于管理员面板展示趋势
-- ------------------------------------------------------------
CREATE TABLE eval_batch
(
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '批次主键',
    tenant_id             BIGINT      NOT NULL COMMENT '所属租户ID',
    case_count            INT         NOT NULL COMMENT '本次参与评估的用例数量',
    status                VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '批次状态：PENDING/RUNNING/DONE/FAILED',
    avg_faithfulness      FLOAT       NULL COMMENT '本批次忠实度均值，评估完成后写入',
    avg_answer_relevancy  FLOAT       NULL COMMENT '本批次答案相关性均值',
    avg_context_recall    FLOAT       NULL COMMENT '本批次上下文召回率均值',
    avg_context_precision FLOAT       NULL COMMENT '本批次上下文精准度均值',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '评估批次表 — 每次运行评估对应一条记录，存储本批次的汇总指标均值';


-- ------------------------------------------------------------
-- 评估结果明细表
-- 每个用例在每次批次中对应一条明细记录
-- model_answer：RAG 系统对该用例问题给出的实际回答
-- 四个指标字段：Ragas 对该用例计算得出的各项得分
-- ------------------------------------------------------------
CREATE TABLE eval_result
(
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '结果主键',
    batch_id          BIGINT   NOT NULL COMMENT '所属批次ID，关联 eval_batch.id',
    eval_case_id      BIGINT   NOT NULL COMMENT '对应用例ID，关联 eval_case.id',
    model_answer      TEXT     NOT NULL COMMENT 'RAG 系统对该问题的实际回答',
    faithfulness      FLOAT    NULL COMMENT '忠实度得分：0~1，衡量回答是否忠实于检索内容',
    answer_relevancy  FLOAT    NULL COMMENT '答案相关性得分：0~1，衡量回答是否切题',
    context_recall    FLOAT    NULL COMMENT '上下文召回率得分：0~1，衡量标准答案是否被检索覆盖',
    context_precision FLOAT    NULL COMMENT '上下文精准度得分：0~1，衡量检索内容是否都有用',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_batch (batch_id) COMMENT '按批次查询明细的索引'
) COMMENT '评估结果明细表 — 每个用例在每次评估中的详细得分，供管理员面板逐条查看';
