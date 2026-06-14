package com.kb.app.rag.milvus;

import com.kb.app.config.MilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Milvus Collection 管理工具类。
 * <p>
 * Collection 是向量库层租户隔离的核心机制：每个租户独立使用一个 Collection，
 * 命名为 tenant_{tenantId}_docs，避免不同租户的文档向量在同一 Collection 中混写或误检索。
 * 本类只负责 Collection 的创建、初始化和整库删除，不包含向量写入和检索逻辑。
 *
 * @author 问渠系统
 */
@Component
public class MilvusCollectionHelper {

    private static final String COLLECTION_SUFFIX = "_docs";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_TENANT_ID = "tenant_id";
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_VERSION_ID = "version_id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_HEADING_PATH = "heading_path";
    private static final String FIELD_PAGE_NO = "page_no";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String EMBEDDING_INDEX_NAME = "idx_embedding_ivf_flat";
    private static final int EMBEDDING_DIMENSION = 2048;
    private static final int CONTENT_MAX_LENGTH = 65535;
    private static final int HEADING_PATH_MAX_LENGTH = 500;

    private final MilvusServiceClient milvusServiceClient;
    private final MilvusProperties milvusProperties;

    public MilvusCollectionHelper(MilvusServiceClient milvusServiceClient,
                                  MilvusProperties milvusProperties) {
        this.milvusServiceClient = milvusServiceClient;
        this.milvusProperties = milvusProperties;
    }

    /**
     * 获取租户对应的 Collection 名称。
     * <p>
     * 命名规则固定为 tenant_{tenantId}_docs。这里使用租户 ID 而不是租户 code，
     * 是因为 ID 是纯数字且稳定，tenant code 是字符串，可能包含短横线等字符；
     * Milvus Collection 名称对特殊字符有限制，使用数字 ID 可以降低命名非法风险。
     *
     * @param tenantId 租户 ID
     * @return 租户文档向量 Collection 名称
     */
    public String getCollectionName(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        return milvusProperties.getCollectionPrefix() + tenantId + COLLECTION_SUFFIX;
    }

    public boolean collectionExists(Long tenantId) {
        return hasCollection(getCollectionName(tenantId));
    }

    /**
     * 创建租户 Collection；如果已存在则直接返回。
     * <p>
     * 字段说明：
     * <ul>
     *     <li>id：Milvus INT64 主键，autoID=true，由 Milvus 自动生成，避免业务侧维护向量主键。</li>
     *     <li>chunk_id：对应 MySQL doc_chunk.id，用于把检索结果映射回原始 chunk 记录。</li>
     *     <li>tenant_id：租户 ID，虽然每租户独立 Collection，仍冗余保存，便于审计和防御性过滤。</li>
     *     <li>document_id：文档 ID，用于按文档定位、删除或排查向量数据。</li>
     *     <li>version_id：文档版本 ID，用于版本清理时同步定位旧版本向量。</li>
     *     <li>content：chunk 原始文本，用于检索结果来源引用展示。</li>
     *     <li>heading_path：标题路径，用于来源引用展示（无标题时存空串）。</li>
     *     <li>page_no：PDF 页码，Word 等无页码场景存 -1。</li>
     *     <li>embedding：智谱 embedding-3 生成的 FLOAT_VECTOR，维度固定为 2048。</li>
     * </ul>
     * 索引选择 IVF_FLAT 而不是 HNSW：IVF_FLAT 内存占用更低，适合当前小规模数据场景
     * （少于 1000 份文档）；HNSW 检索性能好但内存成本更高。
     * <p>
     * metric_type=IP 表示内积。向量归一化后，内积等价于余弦相似度，适合语义向量相似度排序。
     * nlist=128 表示 IVF 聚类中心数量，会影响检索速度和精度：nlist 越大候选簇越细，
     * 通常精度潜力更高但索引和检索开销也会增加。
     *
     * @param tenantId 租户 ID
     */
    public void createCollectionIfNotExists(Long tenantId) {
        String collectionName = getCollectionName(tenantId);
        if (hasCollection(collectionName)) {
            return;
        }

        List<FieldType> fields = Arrays.asList(
                FieldType.newBuilder()
                        .withName(FIELD_ID)
                        .withDescription("Milvus auto-generated primary key")
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(true)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_CHUNK_ID)
                        .withDescription("MySQL doc_chunk.id for source mapping")
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_TENANT_ID)
                        .withDescription("Tenant id for audit and defensive filtering")
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_DOCUMENT_ID)
                        .withDescription("Document id for document-level cleanup")
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_VERSION_ID)
                        .withDescription("Document version id for version cleanup")
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_CONTENT)
                        .withDescription("Original chunk text for source citation")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(CONTENT_MAX_LENGTH)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_HEADING_PATH)
                        .withDescription("Heading path for source citation")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(HEADING_PATH_MAX_LENGTH)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_PAGE_NO)
                        .withDescription("PDF page number, -1 means not applicable")
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(FIELD_EMBEDDING)
                        .withDescription("Zhipu embedding-3 vector")
                        .withDataType(DataType.FloatVector)
                        .withDimension(EMBEDDING_DIMENSION)
                        .build()
        );

        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .withFieldTypes(fields)
                .build();

        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("Tenant document chunks for RAG retrieval")
                .withSchema(schema)
                .build();
        assertSuccess(milvusServiceClient.createCollection(createCollectionParam),
                "创建 Milvus 集合 " + collectionName);

        CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_EMBEDDING)
                .withIndexName(EMBEDDING_INDEX_NAME)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"nlist\":128}")
                .build();
        assertSuccess(milvusServiceClient.createIndex(createIndexParam),
                "为 Milvus 集合 " + collectionName + " 创建向量索引");

        // Milvus 2.x：建表并建索引后必须 load 到内存，后续 insert/search 才能生效。
        loadCollectionInternal(collectionName);
    }

    /**
     * 若集合已存在则加载到内存（幂等；Milvus 进程重启后需重新 load）。
     * <p>
     * 写入与检索前调用，避免「集合存在但未加载」导致操作失败。
     *
     * @param tenantId 租户 ID
     */
    public void ensureCollectionLoaded(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        String collectionName = getCollectionName(tenantId);
        if (!hasCollection(collectionName)) {
            return;
        }
        loadCollectionInternal(collectionName);
    }

    private void loadCollectionInternal(String collectionName) {
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        assertSuccess(milvusServiceClient.loadCollection(loadParam),
                "加载 Milvus 集合 " + collectionName);
    }

    /**
     * 删除租户对应的整个 Collection。
     * <p>
     * 该操作会删除租户在 Milvus 中的全部文档向量数据，不可逆。
     * 仅允许在租户注销等明确场景调用，调用前必须完成二次确认。
     *
     * @param tenantId 租户 ID
     */
    public void deleteCollection(Long tenantId) {
        String collectionName = getCollectionName(tenantId);
        if (!hasCollection(collectionName)) {
            return;
        }

        DropCollectionParam dropCollectionParam = DropCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        assertSuccess(milvusServiceClient.dropCollection(dropCollectionParam),
                "删除 Milvus 集合 " + collectionName);
    }

    private boolean hasCollection(String collectionName) {
        HasCollectionParam hasCollectionParam = HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        R<Boolean> response = milvusServiceClient.hasCollection(hasCollectionParam);
        assertSuccess(response, "检查 Milvus 集合 " + collectionName);
        return Boolean.TRUE.equals(response.getData());
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
