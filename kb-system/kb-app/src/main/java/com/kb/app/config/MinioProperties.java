package com.kb.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置属性类 — 从 application.yml 中读取 minio.* 前缀的配置。
 * <p>
 * 配置项说明：
 * <ul>
 *     <li><b>endpoint</b> — MinIO 服务端地址，如 http://localhost:9000 或 Docker 内部 http://minio:9000</li>
 *     <li><b>accessKey</b> — 访问密钥，必须从环境变量 MINIO_ACCESS_KEY 注入，禁止硬编码</li>
 *     <li><b>secretKey</b> — 秘密密钥，必须从环境变量 MINIO_SECRET_KEY 注入，禁止硬编码</li>
 *     <li><b>presignedExpireMinutes</b> — 预签名 URL 有效期（分钟），默认 15 分钟</li>
 * </ul>
 * <p>
 * 对应 yml 配置：
 * <pre>
 * minio:
 *   endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
 *   access-key: ${MINIO_ACCESS_KEY:}
 *   secret-key: ${MINIO_SECRET_KEY:}
 *   presigned-expire-minutes: 15
 * </pre>
 *
 * @author kb-system
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * MinIO 服务端地址。
     * 本地开发使用 http://localhost:9000，Docker 内部使用 http://minio:9000。
     */
    private String endpoint;

    /**
     * MinIO 访问密钥（Access Key），对应 MinIO 的 Root User。
     * 必须从环境变量 MINIO_ACCESS_KEY 读取，不允许硬编码。
     */
    private String accessKey;

    /**
     * MinIO 秘密密钥（Secret Key），对应 MinIO 的 Root Password。
     * 必须从环境变量 MINIO_SECRET_KEY 读取，不允许硬编码。
     */
    private String secretKey;

    /**
     * 预签名 URL 有效期（分钟），默认 15 分钟。
     * 前端用此 URL 直接下载文件，过期后自动失效，保证安全性。
     */
    private int presignedExpireMinutes = 15;
}
