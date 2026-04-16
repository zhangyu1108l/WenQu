package com.kb.app.module.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档版本实体类 — 对应数据库 document_version 表。
 * <p>
 * 每个文档最多保留最近 5 个版本，超出时后台自动删除最旧版本，
 * 删除顺序严格为：① MinIO 文件 → ② Milvus 向量 → ③ doc_chunk 记录 → ④ document_version 记录。
 * <p>
 * is_active = 1 表示当前对话检索使用的激活版本，每个文档同时只有一个激活版本。
 * <p>
 * 注意：document_version 表不包含 tenant_id 字段，
 * 需要在租户拦截器中将此表加入忽略列表。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_version")
public class DocumentVersionDO {

    /**
     * 版本主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文档ID，关联 document.id
     */
    private Long documentId;

    /**
     * 版本号，从 1 开始递增，同一文档最多保留 5 个版本
     */
    private Integer versionNo;

    /**
     * MinIO Bucket 名称，格式：tenant-{tenantId}（小写+短横线）
     */
    private String minioBucket;

    /**
     * MinIO 对象路径，格式：docs/{docId}/v{versionNo}/{filename}
     */
    private String minioKey;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 是否为激活版本：1=是  0=否
     * 每个文档同时只有一个激活版本，新版本上传后自动激活
     */
    private Integer isActive;

    /**
     * 上传时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
