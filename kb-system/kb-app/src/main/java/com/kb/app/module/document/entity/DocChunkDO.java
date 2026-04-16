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
 * 文档分块实体类 — 对应数据库 doc_chunk 表。
 * <p>
 * 文档解析后被切分为若干 chunk，每个 chunk 独立向量化存入 Milvus。
 * <p>
 * headingPath 记录该 chunk 所在的标题路径，用于前端来源引用展示。
 * 示例：第5章 薪酬福利 > 5.3 年假制度
 * <p>
 * milvusId 是 Milvus 中对应向量的 ID，删除文档时通过此字段同步删除向量。
 * <p>
 * doc_chunk 表包含 tenant_id（冗余字段，加速按租户查询），
 * 所有查询会被租户拦截器自动追加租户隔离条件。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("doc_chunk")
public class DocChunkDO {

    /**
     * chunk 主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文档ID，关联 document.id
     */
    private Long documentId;

    /**
     * 所属版本ID，关联 document_version.id
     */
    private Long versionId;

    /**
     * 所属租户ID（冗余字段，加速按租户查询）
     */
    private Long tenantId;

    /**
     * 在文档中的顺序，从 0 开始
     */
    private Integer chunkIndex;

    /**
     * 原始文本内容，用于前端来源引用段落高亮展示
     */
    private String content;

    /**
     * 标题路径，如：第5章>5.3节>年假规定；无标题结构时为 NULL
     */
    private String headingPath;

    /**
     * PDF 页码；Word 文档无页码概念，为 NULL
     */
    private Integer pageNo;

    /**
     * Milvus 中的向量 ID，文档删除时通过此 ID 同步删除向量
     */
    private String milvusId;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
