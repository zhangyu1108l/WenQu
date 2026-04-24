package com.kb.app.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置类 — 构建并注册 {@link MinioClient} Bean。
 * <p>
 * <b>为什么把 MinioClient 注册为 Spring Bean：</b>
 * <ul>
 *     <li>MinioClient 是线程安全的单例对象，全局复用一个实例即可，不需要每次操作都新建连接</li>
 *     <li>Spring 容器统一管理生命周期，便于后续注入到 {@link com.kb.app.util.MinioUtil} 等工具类中</li>
 *     <li>配置集中管理（endpoint、accessKey、secretKey），上线时只需修改配置文件，无需改动业务代码</li>
 * </ul>
 * <p>
 * 后续如需配置高级参数（如连接超时、区域设置），可在此类中扩展 MinioClient.builder() 的调用链。
 *
 * @author kb-system
 */
@Configuration
public class MinioConfig {

    /**
     * 构建 MinioClient Bean。
     * <p>
     * 从 {@link MinioProperties} 读取连接参数，使用 Builder 模式构建客户端实例。
     * MinioClient 内部维护 HTTP 连接池，适合高并发文件上传/下载场景。
     *
     * @param minioProperties MinIO 配置属性，由 Spring 容器注入
     * @return 配置好的 MinioClient 实例
     */
    @Bean
    public MinioClient minioClient(MinioProperties minioProperties) {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
