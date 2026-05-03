package com.kb.app.module.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档详情响应 VO — 用于文档列表和文档详情接口的响应数据。
 * <p>
 * 包含文档基础信息及当前激活版本信息（嵌套 {@link VersionVO}）。
 * 隐藏 tenant_id、uploader_id 等内部字段，只暴露前端需要的数据。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVO {

    /** 文档主键 */
    private Long id;

    /** 文档标题（文件名去掉扩展名） */
    private String title;

    /** 文件类型：pdf / docx */
    private String fileType;

    /** 处理状态：PENDING / PARSING / EMBEDDING / READY / FAILED */
    private String status;

    /** 过期时间，NULL 表示永不过期 */
    private LocalDateTime expireAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 当前激活版本信息。
     * <p>
     * 文档列表接口中可能为 null（如文档刚创建、还未生成版本），
     * 文档详情接口中正常情况下不为 null。
     */
    private VersionVO activeVersion;
}
