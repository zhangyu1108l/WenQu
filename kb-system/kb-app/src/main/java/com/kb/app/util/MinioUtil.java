package com.kb.app.util;

import com.kb.app.config.MinioProperties;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 操作工具类 — 封装文件上传、下载、预签名 URL 生成、删除等常用操作。
 * <p>
 * <b>命名规则说明：</b>
 * <ul>
 *     <li><b>Bucket 命名</b>：tenant-{tenantId}（小写 + 短横线，符合 MinIO 规范）。
 *         每个租户创建时自动创建对应 Bucket，实现租户间文件物理隔离。</li>
 *     <li><b>ObjectKey 命名</b>：docs/{docId}/v{versionNo}/{filename}。
 *         通过路径层级组织文件，便于按文档和版本管理。</li>
 * </ul>
 * <p>
 * 所有方法均委托给 {@link MinioClient} 执行，异常统一捕获并记录日志后抛出 RuntimeException，
 * 避免业务层处理底层 MinIO 异常细节。
 *
 * @author kb-system
 */
@Slf4j
@Component
public class MinioUtil {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioUtil(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    /**
     * 创建 Bucket（如果不存在）。
     * <p>
     * 调用时机：新租户注册时调用，为租户创建专属 Bucket（命名规则 tenant-{tenantId}）。
     * 每个租户一个 Bucket，实现文件层面的物理隔离。
     *
     * @param bucketName Bucket 名称，必须符合 MinIO 命名规范（小写、短横线）
     */
    public void createBucketIfNotExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Bucket 创建成功: {}", bucketName);
            }
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            log.error("创建 Bucket 失败: bucketName={}, error={}", bucketName, e.getMessage());
            throw new RuntimeException("创建 Bucket 失败: " + bucketName, e);
        }
    }

    /**
     * 上传文件字节数组到指定 Bucket。
     * <p>
     * 调用时机：文档上传接口中，文件解析完成后存入 MinIO。
     * <p>
     * <b>contentType 的作用：</b>
     * 影响浏览器通过预签名 URL 下载时的文件类型识别（Content-Type 响应头），
     * 如 application/pdf、application/vnd.openxmlformats-officedocument.wordprocessingml.document。
     * 正确的 contentType 能让浏览器自动调用对应程序打开文件。
     *
     * @param bucketName  目标 Bucket（租户隔离，命名规则 tenant-{tenantId}）
     * @param objectKey   存储路径（格式 docs/{docId}/v{versionNo}/{filename}）
     * @param bytes       文件字节数组
     * @param contentType MIME 类型，如 application/pdf，影响浏览器下载行为
     * @return 上传成功后的 objectKey（与入参相同，便于链式调用）
     */
    public String uploadFile(String bucketName, String objectKey, byte[] bytes, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .build());
            log.info("文件上传成功: bucketName={}, objectKey={}, size={} bytes",
                    bucketName, objectKey, bytes.length);
            return objectKey;
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            log.error("文件上传失败: bucketName={}, objectKey={}, error={}",
                    bucketName, objectKey, e.getMessage());
            throw new RuntimeException("文件上传失败: " + objectKey, e);
        }
    }

    /**
     * 下载文件，返回字节数组。
     * <p>
     * 调用时机：供内部调用（如文档解析时读取原始文件内容），不直接返回给前端。
     * 前端下载应使用 {@link #getPresignedUrl(String, String, int)} 获取预签名 URL，减轻后端带宽压力。
     *
     * @param bucketName 源 Bucket
     * @param objectKey  文件存储路径
     * @return 文件字节数组
     */
    public byte[] downloadFile(String bucketName, String objectKey) {
        try (var stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build())) {
            byte[] bytes = stream.readAllBytes();
            log.info("文件下载成功: bucketName={}, objectKey={}, size={} bytes",
                    bucketName, objectKey, bytes.length);
            return bytes;
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            log.error("文件下载失败: bucketName={}, objectKey={}, error={}",
                    bucketName, objectKey, e.getMessage());
            throw new RuntimeException("文件下载失败: " + objectKey, e);
        }
    }

    /**
     * 生成预签名下载 URL。
     * <p>
     * 调用时机：前端请求下载文档时，后端返回预签名 URL，前端直接通过此 URL 从 MinIO 下载文件。
     * <p>
     * <b>为什么使用预签名 URL 而不是后端代理下载：</b>
     * <ul>
     *     <li>减轻后端带宽压力 — 文件直接从 MinIO 流向浏览器，不经过 Java 服务</li>
     *     <li>降低后端内存消耗 — 不需要在后端缓冲文件字节数组</li>
     *     <li>安全性 — URL 带有时效性（默认 15 分钟），过期后自动失效，无法被长期滥用</li>
     * </ul>
     *
     * @param bucketName     源 Bucket
     * @param objectKey      文件存储路径
     * @param expireMinutes  URL 有效期（分钟），传入 0 则使用配置默认值
     * @return 预签名下载 URL 字符串，可直接给前端使用
     */
    public String getPresignedUrl(String bucketName, String objectKey, int expireMinutes) {
        int minutes = expireMinutes > 0 ? expireMinutes : minioProperties.getPresignedExpireMinutes();
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(minutes, TimeUnit.MINUTES)
                            .build());
            log.debug("预签名 URL 生成成功: bucketName={}, objectKey={}, expireMinutes={}",
                    bucketName, objectKey, minutes);
            return url;
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            log.error("预签名 URL 生成失败: bucketName={}, objectKey={}, error={}",
                    bucketName, objectKey, e.getMessage());
            throw new RuntimeException("预签名 URL 生成失败: " + objectKey, e);
        }
    }

    /**
     * 删除指定文件。
     * <p>
     * 调用时机：删除文档版本时调用。
     * <p>
     * <b>删除顺序要求（必须遵守）：</b>
     * 必须先删 MinIO 文件，再删数据库记录（document_version、doc_chunk）。
     * 如果先删数据库，MinIO 中的文件会变成无法追踪的"孤儿文件"。
     *
     * @param bucketName 源 Bucket
     * @param objectKey  文件存储路径
     */
    public void deleteFile(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            log.info("文件删除成功: bucketName={}, objectKey={}", bucketName, objectKey);
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            log.error("文件删除失败: bucketName={}, objectKey={}, error={}",
                    bucketName, objectKey, e.getMessage());
            throw new RuntimeException("文件删除失败: " + objectKey, e);
        }
    }

    /**
     * 判断文件是否存在于 MinIO 中。
     * <p>
     * 调用时机：版本管理、文件校验等场景，确认对象是否已存储。
     *
     * @param bucketName 源 Bucket
     * @param objectKey  文件存储路径
     * @return true=文件存在，false=文件不存在
     */
    public boolean doesObjectExist(String bucketName, String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            return true;
        } catch (ErrorResponseException e) {
            // MinIO 返回 404 NoSuchKey，表示文件不存在，属于正常场景
            return false;
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            log.error("检查文件存在性失败: bucketName={}, objectKey={}, error={}",
                    bucketName, objectKey, e.getMessage());
            throw new RuntimeException("检查文件存在性失败: " + objectKey, e);
        }
    }
}
