package com.kb.app.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Milvus 向量写入 DTO。
 * <p>
 * 该对象只描述写入 Milvus 时需要按列拆分的数据，最终会被
 * {@code EmbeddingServiceImpl} 转换成 Milvus SDK 要求的列式结构。
 *
 * @author 问渠系统
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilvusInsertDTO {

    /**
     * 对应 MySQL doc_chunk.id。
     * <p>
     * 文档处理阶段先写 Milvus 再写 MySQL，因此真实 chunkId 暂时未知，
     * 写入时会使用 documentId + chunkIndex 生成的临时标识占位。
     */
    private Long chunkId;

    /**
     * 租户 ID。
     */
    private Long tenantId;

    /**
     * 文档 ID。
     */
    private Long documentId;

    /**
     * 文档版本 ID。
     */
    private Long versionId;

    /**
     * chunk 原始文本。
     */
    private String content;

    /**
     * 归一化后的向量数组。
     */
    private String headingPath;

    private Integer pageNo;

    private float[] embedding;
}
