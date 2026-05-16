package com.kb.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智谱 Embedding 配置属性。
 *
 * @author kb-system
 */
@Data
@ConfigurationProperties(prefix = "zhipu.embedding")
public class EmbeddingProperties {

    /**
     * Embedding 模型名称，固定使用智谱 embedding-3。
     */
    private String baseUrl;

    private String apiKey;

    private String embeddingsPath = "/embeddings";

    private String model = "embedding-3";

    private Integer dimensions = 2048;

    /**
     * 批量请求大小，默认每批 16 条。
     * <p>
     * 文档 chunk 数量通常较多，单条循环调用 API 太慢；批量请求可以显著减少网络开销。
     */
    private int batchSize = 16;
}
