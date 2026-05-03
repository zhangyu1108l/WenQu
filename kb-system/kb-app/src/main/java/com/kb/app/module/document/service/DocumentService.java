package com.kb.app.module.document.service;

import com.kb.app.module.document.dto.DocumentVO;
import com.kb.app.module.document.dto.VersionVO;
import com.kb.common.dto.PageDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档管理 Service 接口 — 定义文档查询、删除、过期设置等操作。
 * <p>
 * 职责：
 * <ul>
 *     <li>文档列表分页查询（支持关键词搜索）</li>
 *     <li>文档详情查询（含激活版本信息）</li>
 *     <li>版本历史列表查询</li>
 *     <li>预签名下载 URL 生成</li>
 *     <li>文档过期时间设置</li>
 *     <li>文档删除（同步清理 MinIO + Milvus + doc_chunk + document_version）</li>
 * </ul>
 * <p>
 * 注意：文档上传由 {@link DocUploadService} 负责，本接口不包含上传逻辑。
 *
 * @author kb-system
 */
public interface DocumentService {

    /**
     * 分页查询文档列表。
     *
     * @param tenantId 租户ID
     * @param keyword  搜索关键词（对 title 模糊匹配），可为 null
     * @param page     页码（从 1 开始）
     * @param size     每页条数
     * @return 分页结果
     */
    PageDTO<DocumentVO> getDocumentList(Long tenantId, String keyword, int page, int size);

    /**
     * 查询文档详情（含当前激活版本信息）。
     *
     * @param id       文档ID
     * @param tenantId 租户ID（用于校验归属）
     * @return 文档详情 VO
     * @throws com.kb.common.exception.BusinessException 文档不存在或跨租户访问时抛出
     */
    DocumentVO getDocumentDetail(Long id, Long tenantId);

    /**
     * 查询文档版本列表。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID（用于校验归属）
     * @return 版本列表（按 version_no 降序）
     */
    List<VersionVO> getVersionList(Long documentId, Long tenantId);

    /**
     * 生成预签名下载 URL。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID（用于校验归属）
     * @return MinIO 预签名 URL（默认有效期 15 分钟）
     */
    String getDownloadUrl(Long documentId, Long tenantId);

    /**
     * 设置文档过期时间。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID（用于校验归属）
     * @param expireAt   过期时间，传 null 表示永不过期
     */
    void setExpireAt(Long documentId, Long tenantId, LocalDateTime expireAt);

    /**
     * 删除文档及其所有关联资源。
     * <p>
     * 删除顺序：Milvus 向量 → doc_chunk → MinIO 文件 → document_version → document。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID（用于校验归属）
     */
    void deleteDocument(Long documentId, Long tenantId);
}
