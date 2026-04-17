package com.kb.common.enums;

/**
 * 文档处理状态枚举。
 * <p>
 * 对应 document 表的 status 字段（VARCHAR 存储枚举名称字符串）。
 * 状态流转：PENDING → PARSING → EMBEDDING → READY / FAILED
 * <ul>
 *   <li>{@link #PENDING} — 待处理：文档刚上传到 MinIO，尚未开始解析</li>
 *   <li>{@link #PARSING} — 解析中：Python 侧车正在提取文本内容</li>
 *   <li>{@link #EMBEDDING} — 向量化中：文本已切块，正在调用 Embedding 模型生成向量</li>
 *   <li>{@link #READY} — 就绪：向量已入库 Milvus，文档可用于 RAG 检索</li>
 *   <li>{@link #FAILED} — 失败：处理过程中发生异常，需查看 async_task.error_msg</li>
 * </ul>
 */
public enum DocStatus {

    /** 待处理：文档刚上传，等待异步任务拾取 */
    PENDING,

    /** 解析中：Python 侧车正在执行 PDF/Word 文本提取 */
    PARSING,

    /** 向量化中：调用智谱 embedding-3 生成 2048 维向量 */
    EMBEDDING,

    /** 就绪：文档处理完成，可正常参与 RAG 检索问答 */
    READY,

    /** 失败：处理过程中出现异常 */
    FAILED
}
