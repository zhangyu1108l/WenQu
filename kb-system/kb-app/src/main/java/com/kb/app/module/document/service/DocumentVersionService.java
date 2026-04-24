package com.kb.app.module.document.service;

import com.kb.app.module.document.entity.DocumentVersionDO;

import java.util.List;

/**
 * 文档版本管理 Service 接口 — 定义版本创建、清理、查询等核心操作。
 * <p>
 * 主要职责：
 * <ul>
 *     <li>新版本创建：上传文件到 MinIO → 切换激活版本 → 插入版本记录</li>
 *     <li>旧版本清理：版本数超过 5 个时自动删除最旧版本（MinIO + Milvus + MySQL）</li>
 *     <li>版本查询：列表查询、激活版本查询</li>
 * </ul>
 *
 * @author kb-system
 */
public interface DocumentVersionService {

    /**
     * 创建新版本。
     * <p>
     * 上传文件到 MinIO → 取消旧激活版本 → 插入新版本记录（is_active=1）。
     *
     * @param documentId  文档ID
     * @param bucket      MinIO Bucket 名称（格式 tenant-{tenantId}）
     * @param fileBytes   文件字节数组
     * @param filename    原始文件名（含扩展名）
     * @param contentType MIME 类型，如 application/pdf
     * @return 新创建的版本实体
     */
    DocumentVersionDO createVersion(Long documentId, String bucket, byte[] fileBytes,
                                    String filename, String contentType);

    /**
     * 清理旧版本。
     * <p>
     * 检查版本数是否超过 5 个，超出部分按 version_no 从小到大依次删除，
     * 每个版本删除顺序：MinIO 文件 → Milvus 向量 → doc_chunk → document_version。
     *
     * @param documentId 文档ID
     */
    void cleanOldVersions(Long documentId);

    /**
     * 查询文档所有版本列表，按 version_no 降序排列（最新版本在前）。
     *
     * @param documentId 文档ID
     * @return 版本列表
     */
    List<DocumentVersionDO> getVersionList(Long documentId);

    /**
     * 查询当前激活版本。
     *
     * @param documentId 文档ID
     * @return 激活版本实体
     * @throws com.kb.common.exception.BusinessException 激活版本不存在时抛出
     */
    DocumentVersionDO getActiveVersion(Long documentId);
}
