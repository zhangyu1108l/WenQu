package com.kb.app.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量检索命中的文档分块结果。
 * <p>
 * score 表示 Milvus IP 内积相似度得分。文档向量和查询向量都做 L2 归一化后，
 * IP 内积等价于余弦相似度；本 DTO 中约定分值范围为 0~1，越高表示越相关。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResult {

    /**
     * MySQL doc_chunk.id，用于后续消息来源引用展示。
     */
    private Long chunkId;

    /**
     * Milvus 向量 ID，对应 doc_chunk.milvus_id。
     */
    private String milvusId;

    /**
     * 文档 ID，对应 document.id。
     */
    private Long documentId;

    /**
     * chunk 原始文本。
     */
    private String content;

    /**
     * 标题路径，可为 null。
     */
    private String headingPath;

    /**
     * PDF 页码，可为 null；Word 文档无页码。
     */
    private Integer pageNo;

    /**
     * 相似度得分，范围 0~1，越高越相关。
     */
    private float score;
}
