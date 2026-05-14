package com.kb.app.rag.embedding.impl;

import com.kb.app.context.TenantContext;
import com.kb.app.module.document.entity.DocChunkDO;
import com.kb.app.module.document.entity.DocumentDO;
import com.kb.app.module.document.entity.DocumentVersionDO;
import com.kb.app.module.document.mapper.DocChunkMapper;
import com.kb.app.rag.dto.ChunkDTO;
import com.kb.app.rag.dto.MilvusInsertDTO;
import com.kb.app.rag.embedding.EmbeddingService;
import com.kb.app.rag.embedding.ZhipuEmbeddingClient;
import com.kb.app.rag.milvus.MilvusCollectionHelper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.response.MutationResultWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Embedding 写入 Service 实现。
 * <p>
 * 本类只负责 Step 5-C：批量生成向量并写入 Milvus / doc_chunk，
 * 不包含检索、重排、RAG 问答或 Controller 逻辑。
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final String FIELD_ID = "id";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_TENANT_ID = "tenant_id";
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_VERSION_ID = "version_id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final long TEMP_CHUNK_INDEX_FACTOR = 1_000_000L;

    private final ZhipuEmbeddingClient embeddingClient;
    private final MilvusCollectionHelper collectionHelper;
    private final MilvusServiceClient milvusServiceClient;
    private final DocChunkMapper docChunkMapper;

    /**
     * 批量向量化并写入 Milvus 和 MySQL。
     * <p>
     * 步骤 5-8 的顺序设计原因：
     * 必须先把向量写入 Milvus，拿到 Milvus 自动生成的 milvus_id，
     * 再把 milvus_id 写入 MySQL doc_chunk.milvus_id 字段，否则后续删除版本时无法通过 MySQL 记录反查向量。
     * <p>
     * 风险说明：如果步骤 8 写入 MySQL 失败，Milvus 中已经插入的向量会成为孤儿向量。
     * 简历项目暂不处理该补偿逻辑，生产环境需要引入分布式事务、可靠消息或补偿任务清理。
     *
     * @param chunks  解析侧车返回的 chunk 列表
     * @param doc     文档实体
     * @param version 当前文档版本实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchEmbedAndStore(List<ChunkDTO> chunks, DocumentDO doc, DocumentVersionDO version) {
        if (doc == null || doc.getTenantId() == null || doc.getId() == null) {
            throw new IllegalArgumentException("document and tenantId must not be null");
        }
        if (version == null || version.getId() == null) {
            throw new IllegalArgumentException("document version must not be null");
        }

        Long tenantId = doc.getTenantId();
        Long previousTenantId = TenantContext.getTenantId();
        Long previousUserId = TenantContext.getUserId();
        TenantContext.setTenantId(tenantId);
        try {
            // 1. 确保租户的 Milvus Collection 存在。
            collectionHelper.createCollectionIfNotExists(tenantId);

            if (CollectionUtils.isEmpty(chunks)) {
                log.info("文档无可写入 chunk，跳过 Embedding 写入: docId={}, versionId={}",
                        doc.getId(), version.getId());
                return;
            }

            // 2. 提取所有 chunk 的 content 文本列表。
            List<String> texts = chunks.stream()
                    .map(ChunkDTO::getContent)
                    .collect(Collectors.toList());

            // 3. 调用智谱 API 批量向量化；此步骤是整个流程中最耗时的部分。
            List<float[]> embeddings = embeddingClient.embedBatch(texts);
            if (embeddings.size() != chunks.size()) {
                throw new IllegalStateException("Embedding result size mismatch, chunks="
                        + chunks.size() + ", embeddings=" + embeddings.size());
            }

            // 4. 归一化所有向量。
            List<float[]> normalizedEmbeddings = embeddings.stream()
                    .map(embeddingClient::normalizeVector)
                    .collect(Collectors.toList());

            // 5. 构建 Milvus 插入数据列表。
            // 此时 MySQL doc_chunk 还没插入，真实 chunkId 尚未生成；
            // 因此 chunk_id 列先使用 documentId + chunkIndex 生成临时标识，占位满足 Milvus schema。
            List<MilvusInsertDTO> insertList = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                ChunkDTO chunk = chunks.get(i);
                Integer chunkIndex = chunk.getChunkIndex() == null ? i : chunk.getChunkIndex();
                insertList.add(MilvusInsertDTO.builder()
                        .chunkId(buildTemporaryChunkId(doc.getId(), chunkIndex))
                        .tenantId(tenantId)
                        .documentId(doc.getId())
                        .versionId(version.getId())
                        .content(chunk.getContent())
                        .embedding(normalizedEmbeddings.get(i))
                        .build());
            }

            // 6. 批量插入 Milvus，获取返回的 milvus_id 列表。
            List<String> milvusIds = insertToMilvus(tenantId, insertList);
            if (milvusIds.size() != chunks.size()) {
                throw new IllegalStateException("Milvus id size mismatch, chunks="
                        + chunks.size() + ", milvusIds=" + milvusIds.size());
            }

            // 7. 将 milvus_id 回填到对应的 chunk 数据中。
            List<DocChunkDO> docChunks = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                ChunkDTO chunk = chunks.get(i);
                Integer chunkIndex = chunk.getChunkIndex() == null ? i : chunk.getChunkIndex();
                docChunks.add(DocChunkDO.builder()
                        .documentId(doc.getId())
                        .versionId(version.getId())
                        .tenantId(tenantId)
                        .chunkIndex(chunkIndex)
                        .content(chunk.getContent())
                        .headingPath(chunk.getHeadingPath())
                        .pageNo(chunk.getPageNo())
                        .milvusId(milvusIds.get(i))
                        .build());
            }

            // 8. 批量插入 MySQL doc_chunk 表，写入 milvus_id 以便后续版本删除时同步清理向量。
            for (DocChunkDO docChunk : docChunks) {
                docChunkMapper.insert(docChunk);
            }

            // 9. 完成。
            log.info("Embedding 写入完成: tenantId={}, docId={}, versionId={}, chunkCount={}",
                    tenantId, doc.getId(), version.getId(), chunks.size());
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

    /**
     * 批量插入向量到 Milvus。
     * <p>
     * Milvus Java SDK 的普通 insert 要求按列组织数据，即每个 {@link InsertParam.Field}
     * 表示一整列的值；这和业务代码里常见的"一行一个对象"写法不同。
     * 常见踩坑点是把 {@code List<MilvusInsertDTO>} 直接当作行式数据传入，
     * 但当前 SDK 需要先拆成 chunk_id、tenant_id、document_id、version_id、content、embedding 六列。
     * <p>
     * 返回的 ID 是 Milvus autoID 主键，对应 MySQL doc_chunk.milvus_id 字段。
     *
     * @param tenantId 租户 ID
     * @param data     Milvus 写入数据
     * @return Milvus 自动生成的主键 ID 列表
     */
    @Override
    public List<String> insertToMilvus(Long tenantId, List<MilvusInsertDTO> data) {
        if (CollectionUtils.isEmpty(data)) {
            return List.of();
        }

        // 1. 获取 Collection 名称。
        String collectionName = collectionHelper.getCollectionName(tenantId);

        // 2. 构建各字段的列数据。Milvus SDK 按列插入，不按行插入。
        List<Long> chunkIds = data.stream().map(MilvusInsertDTO::getChunkId).collect(Collectors.toList());
        List<Long> tenantIds = data.stream().map(MilvusInsertDTO::getTenantId).collect(Collectors.toList());
        List<Long> documentIds = data.stream().map(MilvusInsertDTO::getDocumentId).collect(Collectors.toList());
        List<Long> versionIds = data.stream().map(MilvusInsertDTO::getVersionId).collect(Collectors.toList());
        List<String> contents = data.stream().map(MilvusInsertDTO::getContent).collect(Collectors.toList());
        List<List<Float>> embeddings = data.stream()
                .map(item -> toFloatList(item.getEmbedding()))
                .collect(Collectors.toList());

        List<InsertParam.Field> fields = Arrays.asList(
                InsertParam.Field.builder().name(FIELD_CHUNK_ID).values(chunkIds).build(),
                InsertParam.Field.builder().name(FIELD_TENANT_ID).values(tenantIds).build(),
                InsertParam.Field.builder().name(FIELD_DOCUMENT_ID).values(documentIds).build(),
                InsertParam.Field.builder().name(FIELD_VERSION_ID).values(versionIds).build(),
                InsertParam.Field.builder().name(FIELD_CONTENT).values(contents).build(),
                InsertParam.Field.builder().name(FIELD_EMBEDDING).values(embeddings).build()
        );

        // 3. 调用 MilvusServiceClient.insert()。
        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();
        R<MutationResult> response = milvusServiceClient.insert(insertParam);
        assertSuccess(response, "insert vectors into " + collectionName);

        // 4. 获取返回的 ID 列表，即 Milvus 自动生成的主键。
        MutationResultWrapper wrapper = new MutationResultWrapper(response.getData());
        List<Long> longIds = wrapper.getLongIDs();

        // 5. 将 Long 类型 ID 转为 String 返回，对应 MySQL doc_chunk.milvus_id 字段。
        return longIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 根据 milvus_id 列表批量删除向量。
     * <p>
     * 使用场景：删除文档版本时清理对应向量。
     * 删除表达式使用 Milvus DSL 语法，格式为 {@code id in [1, 2, 3]}。
     * 该操作是物理清理流程的一部分，业务上不可恢复。
     * <p>
     * 注意：Milvus 删除本质上先写入删除标记，属于软删除；删除前需要先 flush Collection，
     * 确保前序插入已经落盘，删除后也需要 flush 才能尽快让删除标记真正生效。
     *
     * @param tenantId  租户 ID
     * @param milvusIds Milvus 自动生成的主键 ID 列表
     */
    @Override
    public void deleteByMilvusIds(Long tenantId, List<String> milvusIds) {
        if (CollectionUtils.isEmpty(milvusIds)) {
            return;
        }

        // 1. 获取 Collection 名称。
        String collectionName = collectionHelper.getCollectionName(tenantId);

        flushCollection(collectionName);

        // 2. 构建删除表达式：id in [id1, id2, ...]。
        String idExpr = milvusIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(Long::valueOf)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        if (idExpr.isBlank()) {
            return;
        }
        String expr = FIELD_ID + " in [" + idExpr + "]";

        // 3. 调用 MilvusServiceClient.delete()。
        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .build();
        assertSuccess(milvusServiceClient.delete(deleteParam),
                "delete vectors from " + collectionName);

        flushCollection(collectionName);
        log.info("Milvus 向量已删除: tenantId={}, collection={}, count={}",
                tenantId, collectionName, milvusIds.size());
    }

    private Long buildTemporaryChunkId(Long documentId, Integer chunkIndex) {
        long safeChunkIndex = chunkIndex == null ? 0L : chunkIndex;
        return documentId * TEMP_CHUNK_INDEX_FACTOR + safeChunkIndex;
    }

    private List<Float> toFloatList(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("embedding vector must not be null");
        }

        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private void flushCollection(String collectionName) {
        FlushParam flushParam = FlushParam.newBuilder()
                .addCollectionName(collectionName)
                .withSyncFlush(Boolean.TRUE)
                .build();
        assertSuccess(milvusServiceClient.flush(flushParam),
                "flush Milvus collection " + collectionName);
    }

    private void assertSuccess(R<?> response, String operation) {
        if (response == null) {
            throw new IllegalStateException("Milvus " + operation + " failed: empty response");
        }
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus " + operation + " failed: " + response.getMessage());
        }
    }
}
