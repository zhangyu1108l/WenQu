package com.kb.app.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 配置类，负责构建并注册 {@link MilvusServiceClient} Bean。
 * <p>
 * 将 MilvusServiceClient 注册为 Spring Bean 的原因：
 * <ul>
 *     <li>MilvusServiceClient 是线程安全客户端，全局单例即可支撑并发调用。</li>
 *     <li>客户端内部维护连接资源，复用单例可以避免业务方法中反复创建连接。</li>
 *     <li>由 Spring 统一管理生命周期，后续文档向量写入、检索、删除逻辑可直接注入使用。</li>
 * </ul>
 *
 * @author 问渠系统
 */
@Configuration
@EnableConfigurationProperties(MilvusProperties.class)
public class MilvusConfig {

    /**
     * 构建 MilvusServiceClient Bean。
     * <p>
     * 连接参数只包含当前架构需要的 host/port，认证、TLS 等高级配置不在步骤 5-A 范围内。
     *
     * @param milvusProperties Milvus 配置属性
     * @return 全局复用的 MilvusServiceClient 实例
     */
    @Bean(destroyMethod = "")
    public MilvusServiceClient milvusServiceClient(MilvusProperties milvusProperties) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .build();
        return new MilvusServiceClient(connectParam);
    }
}
