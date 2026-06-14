package com.kb.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 配置属性类，从 application.yml / application-{profile}.yml 的 milvus.* 配置读取。
 * <p>
 * host、port 由环境变量占位符注入，例如：
 * <pre>
 * milvus:
 *   host: ${MILVUS_HOST:milvus}
 *   port: ${MILVUS_PORT:19530}
 * </pre>
 *
 * @author 问渠系统
 */
@Data
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    /**
     * Milvus 服务地址。
     * Docker 网络内使用服务名 milvus，本地开发可使用 localhost 或远程开发环境地址。
     */
    private String host;

    /**
     * Milvus gRPC 端口，默认 19530。
     */
    private int port = 19530;

    /**
     * Collection 命名前缀，架构固定为 tenant_。
     * 完整命名规则为 tenant_{tenantId}_docs。
     */
    private String collectionPrefix = "tenant_";

    /**
     * 向量维度，固定为 2048。
     * <p>
     * 2048 是智谱 embedding-3 的固定输出维度。不同 Embedding 模型的输出维度不同，
     * Milvus Collection 的 FLOAT_VECTOR 维度必须与写入向量一致，不可随意修改。
     */
    private int vectorDimension = 2048;

    /**
     * 默认检索 Top-K 数量，RAG 检索链路默认取 Top-10。
     */
    private int topK = 10;
}
