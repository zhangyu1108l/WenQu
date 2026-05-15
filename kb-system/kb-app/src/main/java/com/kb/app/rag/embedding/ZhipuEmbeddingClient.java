package com.kb.app.rag.embedding;

import com.kb.app.config.EmbeddingProperties;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 智谱 Embedding 客户端封装。
 * <p>
 * 职责：统一封装智谱 embedding-3 的单条向量化、批量向量化和向量归一化逻辑。
 * 业务代码不直接使用 Spring AI，是为了把模型供应商和框架 API 隔离在这一层；
 * 后续如果切换 Embedding 模型，只需要调整本类或配置，业务代码不需要修改。
 *
 * @author kb-system
 */
@Component
public class ZhipuEmbeddingClient {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties embeddingProperties;

    public ZhipuEmbeddingClient(EmbeddingModel embeddingModel, EmbeddingProperties embeddingProperties) {
        this.embeddingModel = embeddingModel;
        this.embeddingProperties = embeddingProperties;
    }

    /**
     * 对单条文本做向量化。
     * <p>
     * 使用场景：用户提问时对 query 做向量化。该方法用于实时检索，对延迟敏感，
     * 因此不做批量，直接调用 Spring AI EmbeddingModel.embed(text) 获取 2048 维向量。
     *
     * @param text 待向量化文本
     * @return 2048 维向量
     */
    public float[] embedSingle(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 批量对文本做向量化。
     * <p>
     * 使用场景：文档上传后批量 Embedding chunk 列表。
     * <p>
     * 分批原因：Embedding API 单次请求存在 token 上限，文档 chunk 数量较多时必须拆成多个批次。
     * 返回顺序必须与输入顺序严格一致，否则会导致向量和 chunk 内容对应关系错误。
     * <p>
     * 批量切分示意：
     * <pre>
     * 输入 32 条文本，batchSize=16
     * 第一批：texts[0~15]   -> API -> 16 个向量
     * 第二批：texts[16~31]  -> API -> 16 个向量
     * 合并 -> 32 个向量，顺序不变
     * </pre>
     *
     * @param texts 待向量化文本列表
     * @return 与输入顺序一致的向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (CollectionUtils.isEmpty(texts)) {
            return Collections.emptyList();
        }

        int batchSize = Math.max(1, embeddingProperties.getBatchSize());
        List<float[]> vectors = new ArrayList<>(texts.size());

        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batchTexts = texts.subList(start, end);
            EmbeddingResponse response = embeddingModel.embedForResponse(batchTexts);
            List<Embedding> embeddings = response.getResults();
            List<float[]> batchVectors = new ArrayList<>(Collections.nCopies(batchTexts.size(), null));

            for (int i = 0; i < embeddings.size(); i++) {
                Embedding embedding = embeddings.get(i);
                Integer index = embedding.getIndex();
                int batchIndex = index == null ? i : index;
                if (batchIndex < 0 || batchIndex >= batchTexts.size()) {
                    throw new IllegalStateException("向量化响应索引越界：" + batchIndex);
                }
                batchVectors.set(batchIndex, embedding.getOutput());
            }

            for (float[] vector : batchVectors) {
                if (vector == null) {
                    throw new IllegalStateException("向量化响应缺少批次项对应的向量");
                }
                vectors.add(vector);
            }
        }

        return vectors;
    }

    /**
     * 对向量做 L2 归一化。
     * <p>
     * 归一化目的：Milvus 使用 IP 内积作为相似度时，向量归一化后内积等价于余弦相似度。
     * 智谱 embedding-3 输出的向量通常已经归一化，本方法作为保险措施，防止特殊情况下向量未归一化。
     * <p>
     * L2 归一化公式：
     * <pre>
     * norm = sqrt(sum(x_i^2))
     * y_i = x_i / norm
     * </pre>
     *
     * @param vector 原始向量
     * @return 归一化后的向量
     */
    public float[] normalizeVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }

        double sum = 0.0D;
        for (float value : vector) {
            sum += value * value;
        }

        double norm = Math.sqrt(sum);
        if (norm == 0.0D) {
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
}
