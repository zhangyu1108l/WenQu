package com.kb.app.rag.retrieval.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kb.app.context.TenantContext;
import com.kb.app.module.document.entity.DocChunkDO;
import com.kb.app.module.document.mapper.DocChunkMapper;
import com.kb.app.rag.dto.ChunkResult;
import com.kb.app.rag.embedding.ZhipuEmbeddingClient;
import com.kb.app.rag.milvus.MilvusCollectionHelper;
import com.kb.app.rag.retrieval.VectorRetriever;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Milvus 向量检索 Service 实现。
 * <p>
 * 本类只负责 Step 5-D：向量检索、简单重排、组合查询入口；
 * Prompt 构建和 LLM 流式调用属于 Step 6。
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRetrieverImpl implements VectorRetriever {

    private static final String FIELD_ID = "id";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_HEADING_PATH = "heading_path";
    private static final String FIELD_PAGE_NO = "page_no";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final int DEFAULT_RETRIEVE_TOP_K = 10;
    private static final int DEFAULT_RERANK_TOP_N = 5;
    private static final float VECTOR_SCORE_WEIGHT = 0.7F;
    private static final float KEYWORD_SCORE_WEIGHT = 0.3F;
    private static final String SEARCH_PARAMS = "{\"nprobe\": 16}";
    private static final List<String> OUT_FIELDS = Arrays.asList(
            FIELD_CHUNK_ID,
            FIELD_DOCUMENT_ID,
            FIELD_CONTENT,
            FIELD_HEADING_PATH,
            FIELD_PAGE_NO
    );
    private static final List<String> FALLBACK_OUT_FIELDS = Arrays.asList(
            FIELD_CHUNK_ID,
            FIELD_DOCUMENT_ID,
            FIELD_CONTENT
    );

    private final MilvusServiceClient milvusServiceClient;
    private final MilvusCollectionHelper collectionHelper;
    private final ZhipuEmbeddingClient embeddingClient;
    private final DocChunkMapper docChunkMapper;

    /**
     * 向量检索主方法。
     * <p>
     * 1. 再次归一化 queryVector：写入 Milvus 时文档向量已经归一化，检索时也必须保持
     * 完全一致的向量格式，这样 IP 内积才等价于余弦相似度。
     * <p>
     * 2. nprobe=16 表示 IVF 检索时探测 16 个聚类中心。nprobe 越大，召回越准确但越慢；
     * 当前索引 nlist=128，nprobe/nlist = 16/128 = 12.5%，属于常用的折中配置。
     * <p>
     * 3. Milvus 检索结果结构：search() 返回 SearchResults，内部包含每个 query 对应的
     * Top-K 命中；SearchResultsWrapper#getIDScore(0) 取第 1 个 query 的命中列表，
     * 每个 IDScore 中包含 Milvus 主键、score，以及 outFields 指定的 chunk_id、content、
     * heading_path、page_no 等字段。
     *
     * @param queryVector 查询向量
     * @param tenantId    租户 ID
     * @param topK        向量检索召回数量
     * @return 按 score 降序排列的检索结果列表
     */
    @Override
    public List<ChunkResult> search(float[] queryVector, Long tenantId, int topK) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }

        float[] normalizedVector = embeddingClient.normalizeVector(queryVector);
        List<List<Float>> queryVectorList = List.of(toFloatList(normalizedVector));
        String collectionName = collectionHelper.getCollectionName(tenantId);

        SearchParam searchParam = buildSearchParam(collectionName, queryVectorList, topK, OUT_FIELDS);
        R<SearchResults> response = milvusServiceClient.search(searchParam);
        if (shouldRetryWithoutOptionalFields(response)) {
            log.warn("Milvus 集合缺少可选来源字段，降级使用基础输出字段检索：集合={}", collectionName);
            response = milvusServiceClient.search(
                    buildSearchParam(collectionName, queryVectorList, topK, FALLBACK_OUT_FIELDS)
            );
        }
        assertSuccess(response, "在集合 " + collectionName + " 中检索向量");

        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
        if (CollectionUtils.isEmpty(idScores)) {
            return List.of();
        }

        return idScores.stream()
                .map(idScore -> toChunkResult(idScore, tenantId))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(ChunkResult::getScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 从 Top-K 候选中选出最相关的 Top-N。
     * <p>
     * 这是简化版 rerank：通过 query 关键词在 content 中的出现次数作为辅助信号。
     * 生产环境建议使用 BGE-Reranker 等专用模型，能理解语义匹配、否定表达和上下文关系，
     * 精度会高于关键词计数。
     * <p>
     * 综合得分 = score * 0.7 + keywordScore * 0.3。0.7/0.3 的含义是：
     * 向量相似度权重更高，关键词匹配只作为辅助修正，避免纯关键词覆盖语义检索结果。
     * topN 默认取 5，对应 RAG 链路中最终进入 Prompt 的 Top-5 段落。
     * <p>
     * 局限性：该简化版无法识别同义词、长距离依赖和复杂语义蕴含，只适合简历项目或
     * 小规模演示场景。
     *
     * @param candidates 候选 chunk
     * @param query      原始问题
     * @param topN       重排序后保留数量，默认取 5
     * @return 重排序后的 Top-N chunk
     */
    @Override
    public List<ChunkResult> rerank(List<ChunkResult> candidates, String query, int topN) {
        if (CollectionUtils.isEmpty(candidates)) {
            return List.of();
        }

        int limit = topN <= 0 ? DEFAULT_RERANK_TOP_N : topN;
        List<String> keywords = extractKeywords(query);

        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.<ChunkResult>comparingDouble(
                        candidate -> combinedScore(candidate, keywords)
                ).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * RAG 链路中的核心入口：向量化 + 检索 + 重排序。
     * <p>
     * 整体流程和目的：
     * 1. 调用 embeddingClient.embedSingle(queryText)，把用户问题转成 embedding-3 查询向量；
     * 2. 调用 search(vector, tenantId, 10)，从租户独立 Collection 中召回 Top-10 候选；
     * 3. 调用 rerank(candidates, queryText, 5)，用关键词辅助信号重排并保留 Top-5；
     * 4. 返回最终 chunk 列表，供 Step 6 构建 Prompt 和来源引用。
     * <p>
     * 先取 Top-10 再重排到 Top-5 的原因：向量检索先召回更多候选，降低漏召回风险；
     * 随后重排序提升精度，让进入 Prompt 的上下文更集中。
     *
     * @param queryText 原始问题文本
     * @param tenantId  租户 ID
     * @return 最终用于构建 RAG Prompt 的 Top-5 chunk
     */
    @Override
    public List<ChunkResult> searchAndRerank(String queryText, Long tenantId) {
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }

        float[] vector = embeddingClient.embedSingle(queryText);
        List<ChunkResult> candidates = search(vector, tenantId, DEFAULT_RETRIEVE_TOP_K);
        return rerank(candidates, queryText, DEFAULT_RERANK_TOP_N);
    }

    private ChunkResult toChunkResult(SearchResultsWrapper.IDScore idScore, Long tenantId) {
        String milvusId = String.valueOf(idScore.getLongID());
        Long chunkId = toLong(getFieldValue(idScore, FIELD_CHUNK_ID));
        Long documentId = toLong(getFieldValue(idScore, FIELD_DOCUMENT_ID));
        String content = toStringValue(getFieldValue(idScore, FIELD_CONTENT));
        String headingPath = toStringValue(getFieldValue(idScore, FIELD_HEADING_PATH));
        Integer pageNo = toInteger(getFieldValue(idScore, FIELD_PAGE_NO));

        DocChunkDO docChunk = findDocChunkByMilvusId(milvusId, tenantId);
        if (docChunk != null) {
            chunkId = docChunk.getId();
            documentId = docChunk.getDocumentId();
            content = docChunk.getContent();
            headingPath = docChunk.getHeadingPath();
            pageNo = docChunk.getPageNo();
        }

        return ChunkResult.builder()
                .chunkId(chunkId)
                .milvusId(milvusId)
                .documentId(documentId)
                .content(content)
                .headingPath(headingPath)
                .pageNo(pageNo)
                .score(normalizeScore(idScore.getScore()))
                .build();
    }

    private SearchParam buildSearchParam(String collectionName,
                                         List<List<Float>> queryVectorList,
                                         int topK,
                                         List<String> outFields) {
        return SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withVectorFieldName(FIELD_EMBEDDING)
                .withVectors(queryVectorList)
                .withTopK(topK)
                .withMetricType(MetricType.IP)
                .withParams(SEARCH_PARAMS)
                .withOutFields(outFields)
                .build();
    }

    private DocChunkDO findDocChunkByMilvusId(String milvusId, Long tenantId) {
        if (!StringUtils.hasText(milvusId) || tenantId == null) {
            return null;
        }

        Long previousTenantId = TenantContext.getTenantId();
        Long previousUserId = TenantContext.getUserId();
        TenantContext.setTenantId(tenantId);
        try {
            return docChunkMapper.selectOne(Wrappers.<DocChunkDO>lambdaQuery()
                    .eq(DocChunkDO::getMilvusId, milvusId)
                    .last("LIMIT 1"));
        } catch (Exception ex) {
            log.warn("查询 doc_chunk 失败，将使用 Milvus 输出字段构建检索结果：Milvus ID={}", milvusId, ex);
            return null;
        } finally {
            TenantContext.clear();
            if (previousTenantId != null) {
                TenantContext.setTenantId(previousTenantId);
            }
            if (previousUserId != null) {
                TenantContext.setUserId(previousUserId);
            }
        }
    }

    private float combinedScore(ChunkResult candidate, List<String> keywords) {
        float vectorScore = candidate == null ? 0.0F : candidate.getScore();
        float keywordScore = keywordScore(candidate == null ? null : candidate.getContent(), keywords);
        return vectorScore * VECTOR_SCORE_WEIGHT + keywordScore * KEYWORD_SCORE_WEIGHT;
    }

    private float keywordScore(String content, List<String> keywords) {
        if (!StringUtils.hasText(content) || CollectionUtils.isEmpty(keywords)) {
            return 0.0F;
        }

        String lowerContent = content.toLowerCase(Locale.ROOT);
        int hitCount = 0;
        for (String keyword : keywords) {
            hitCount += countOccurrences(lowerContent, keyword);
        }

        return Math.min(1.0F, hitCount / (float) Math.max(1, keywords.size()));
    }

    private List<String> extractKeywords(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        String normalized = query.toLowerCase(Locale.ROOT).trim();
        String[] parts = normalized.split("[\\s\\p{Punct}，。！？；：、“”‘’（）【】《》]+");
        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                keywords.add(part);
            }
        }

        if (keywords.isEmpty() && StringUtils.hasText(normalized)) {
            keywords.add(normalized);
        }
        return keywords;
    }

    private int countOccurrences(String content, String keyword) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(keyword)) {
            return 0;
        }

        int count = 0;
        int fromIndex = 0;
        while (fromIndex < content.length()) {
            int index = content.indexOf(keyword, fromIndex);
            if (index < 0) {
                break;
            }
            count++;
            fromIndex = index + keyword.length();
        }
        return count;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private Object getFieldValue(SearchResultsWrapper.IDScore idScore, String fieldName) {
        try {
            if (idScore.contains(fieldName)) {
                return idScore.get(fieldName);
            }
        } catch (Exception ex) {
            log.debug("Milvus 检索结果字段解析失败：字段={}", fieldName, ex);
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Long.valueOf(text);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Integer.valueOf(text);
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private float normalizeScore(float score) {
        if (score < 0.0F) {
            return 0.0F;
        }
        if (score > 1.0F) {
            return 1.0F;
        }
        return score;
    }

    private boolean shouldRetryWithoutOptionalFields(R<?> response) {
        if (response == null || response.getStatus() == R.Status.Success.getCode()) {
            return false;
        }

        String message = response.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }

        String lowerMessage = message.toLowerCase(Locale.ROOT);
        return lowerMessage.contains(FIELD_HEADING_PATH)
                || lowerMessage.contains(FIELD_PAGE_NO)
                || lowerMessage.contains("field");
    }

    private void assertSuccess(R<?> response, String operation) {
        if (response == null) {
            throw new IllegalStateException("Milvus 操作失败：操作=" + operation + "，响应为空");
        }
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus 操作失败：操作=" + operation + "，原因=" + response.getMessage());
        }
    }
}
