package com.kb.app.rag.embedding;

import com.kb.app.module.document.entity.DocumentDO;
import com.kb.app.module.document.entity.DocumentVersionDO;
import com.kb.app.rag.dto.ChunkDTO;
import com.kb.app.rag.dto.MilvusInsertDTO;

import java.util.List;

/**
 * Embedding 写入 Service。
 * <p>
 * 负责将解析后的 chunk 批量向量化，并同步写入 Milvus 与 MySQL doc_chunk。
 *
 * @author kb-system
 */
public interface EmbeddingService {

    /**
     * 批量向量化 chunk，并写入 Milvus 与 MySQL。
     *
     * @param chunks  解析侧车返回的 chunk 列表
     * @param doc     文档实体
     * @param version 当前文档版本实体
     */
    void batchEmbedAndStore(List<ChunkDTO> chunks, DocumentDO doc, DocumentVersionDO version);

    /**
     * 批量插入向量到 Milvus。
     *
     * @param tenantId 租户 ID
     * @param data     Milvus 写入数据
     * @return Milvus 自动生成的主键 ID 列表
     */
    List<String> insertToMilvus(Long tenantId, List<MilvusInsertDTO> data);

    /**
     * 根据 Milvus 主键 ID 批量删除向量。
     *
     * @param tenantId  租户 ID
     * @param milvusIds Milvus 自动生成的主键 ID 列表
     */
    void deleteByMilvusIds(Long tenantId, List<String> milvusIds);
}
