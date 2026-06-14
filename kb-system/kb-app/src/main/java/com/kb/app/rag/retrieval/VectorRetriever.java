package com.kb.app.rag.retrieval;

import com.kb.app.rag.dto.ChunkResult;

import java.util.List;

/**
 * 向量检索 Service。
 *
 * @author 问渠系统
 */
public interface VectorRetriever {

    /**
     * 根据查询向量在租户 Collection 中检索最相关的 chunk。
     *
     * @param queryVector 查询向量
     * @param tenantId    租户 ID
     * @param topK        向量检索召回数量
     * @return 按相似度得分降序排列的检索结果
     */
    List<ChunkResult> search(float[] queryVector, Long tenantId, int topK);

    /**
     * 对向量检索候选结果做简化重排序。
     *
     * @param candidates 候选 chunk
     * @param query      原始问题
     * @param topN       重排序后保留数量，默认取 5
     * @return 重排序后的 Top-N chunk
     */
    List<ChunkResult> rerank(List<ChunkResult> candidates, String query, int topN);

    /**
     * RAG 链路中的向量化、检索、重排序组合入口。
     *
     * @param queryText 原始问题文本
     * @param tenantId  租户 ID
     * @return 最终用于构建 RAG Prompt 的 Top-5 chunk
     */
    List<ChunkResult> searchAndRerank(String queryText, Long tenantId);
}
