package com.kb.app.module.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 版本信息响应 VO — 用于文档详情和版本列表接口的响应数据。
 * <p>
 * 对应 document_version 表中前端需要的字段，
 * 隐藏 minio_bucket / minio_key 等内部存储细节。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionVO {

    /** 版本主键 */
    private Long id;

    /** 版本号（从 1 开始递增，最多保留 5 个） */
    private Integer versionNo;

    /** 文件大小（字节），前端可据此格式化为 KB/MB 展示 */
    private Long fileSize;

    /** 是否为激活版本：1=是 0=否 */
    private Integer isActive;

    /** 上传时间 */
    private LocalDateTime createdAt;
}
