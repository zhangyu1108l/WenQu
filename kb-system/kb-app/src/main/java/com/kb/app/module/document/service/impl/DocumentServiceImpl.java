package com.kb.app.module.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.app.module.document.dto.DocumentVO;
import com.kb.app.module.document.dto.VersionVO;
import com.kb.app.module.document.entity.DocChunkDO;
import com.kb.app.module.document.entity.DocumentDO;
import com.kb.app.module.document.entity.DocumentVersionDO;
import com.kb.app.module.document.mapper.DocChunkMapper;
import com.kb.app.module.document.mapper.DocumentMapper;
import com.kb.app.module.document.mapper.DocumentVersionMapper;
import com.kb.app.module.document.service.DocumentService;
import com.kb.app.util.MinioUtil;
import com.kb.common.dto.PageDTO;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档管理 Service 实现类 — 文档查询、下载、过期设置、删除。
 * <p>
 * <b>核心设计约束：</b>
 * <ul>
 *     <li>所有查询/修改操作必须校验文档的 tenant_id 归属，防止跨租户访问</li>
 *     <li>删除文档时必须同步清理所有关联资源（Milvus 向量、doc_chunk、MinIO 文件、document_version）</li>
 *     <li>删除顺序不可颠倒：先删外部存储（Milvus/MinIO），再删数据库记录</li>
 * </ul>
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final DocChunkMapper docChunkMapper;
    private final MinioUtil minioUtil;

    /** 预签名 URL 默认有效期（分钟） */
    private static final int PRESIGNED_EXPIRE_MINUTES = 15;

    /**
     * 分页查询文档列表。
     * <p>
     * <b>MyBatis-Plus 分页用法说明：</b>
     * <ol>
     *     <li>创建 {@link Page} 对象，指定页码和每页条数</li>
     *     <li>构建 {@link LambdaQueryWrapper} 设置查询条件</li>
     *     <li>调用 {@code documentMapper.selectPage(page, wrapper)} 执行分页查询</li>
     *     <li>MyBatis-Plus 的 {@code PaginationInnerInterceptor} 会自动追加 LIMIT 语句</li>
     *     <li>tenant_id 条件由 {@code TenantLineInnerInterceptor} 自动注入，无需手写</li>
     * </ol>
     * <p>
     * keyword 不为空时，对 title 字段执行 LIKE '%keyword%' 模糊搜索。
     *
     * @param tenantId 租户ID（拦截器自动处理，此处仅做日志记录）
     * @param keyword  搜索关键词，可为 null 或空字符串
     * @param page     页码（从 1 开始）
     * @param size     每页条数
     * @return 分页结果，包含 total / page / size / list
     */
    @Override
    public PageDTO<DocumentVO> getDocumentList(Long tenantId, String keyword, int page, int size) {
        // ① 创建 MyBatis-Plus 分页对象（页码从 1 开始）
        Page<DocumentDO> pageParam = new Page<>(page, size);

        // ② 构建查询条件
        LambdaQueryWrapper<DocumentDO> wrapper = new LambdaQueryWrapper<>();
        // keyword 不为空时，对 title 字段 LIKE 模糊搜索
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(DocumentDO::getTitle, keyword);
        }
        // 按创建时间降序（最新文档在前）
        wrapper.orderByDesc(DocumentDO::getCreatedAt);

        // ③ 执行分页查询（tenant_id 条件由拦截器自动注入）
        Page<DocumentDO> resultPage = documentMapper.selectPage(pageParam, wrapper);

        // ④ DO → VO 转换
        List<DocumentVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageDTO.of(resultPage.getTotal(), page, size, voList);
    }

    /**
     * 查询文档详情（含当前激活版本信息）。
     *
     * @param id       文档ID
     * @param tenantId 租户ID
     * @return 文档详情 VO（含激活版本）
     * @throws BusinessException 文档不存在（code=2001）或跨租户访问（code=2002）时抛出
     */
    @Override
    public DocumentVO getDocumentDetail(Long id, Long tenantId) {
        // ① 查文档基础信息
        DocumentDO doc = documentMapper.selectById(id);
        if (doc == null) {
            throw BusinessException.of(2001, "文档不存在");
        }

        // ② 校验 tenant_id 是否匹配，防止跨租户查看
        checkTenantOwnership(doc, tenantId);

        // ③ 查当前激活版本信息
        DocumentVersionDO activeVersion = documentVersionMapper.selectActiveVersion(id);

        // ④ 组装 VO 返回
        DocumentVO vo = convertToVO(doc);
        if (activeVersion != null) {
            vo.setActiveVersion(convertToVersionVO(activeVersion));
        }
        return vo;
    }

    /**
     * 查询文档版本列表。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID
     * @return 版本列表（按 version_no 降序，最新版本在前）
     */
    @Override
    public List<VersionVO> getVersionList(Long documentId, Long tenantId) {
        // ① 校验文档归属
        DocumentDO doc = getAndCheckDocument(documentId, tenantId);

        // ② 查所有版本列表（Mapper 已按 version_no DESC 排序）
        List<DocumentVersionDO> versions = documentVersionMapper.selectVersionList(documentId);

        // ③ DO → VO 转换
        return versions.stream()
                .map(this::convertToVersionVO)
                .collect(Collectors.toList());
    }

    /**
     * 生成预签名下载 URL。
     * <p>
     * <b>预签名 URL 时效性说明：</b>
     * URL 默认有效期为 15 分钟，过期后自动失效。
     * 这是安全规范的强制要求 — 不返回永久链接，
     * 防止 URL 泄露后被长期滥用。
     * <p>
     * 前端拿到 URL 后应立即使用（或在 15 分钟内使用），
     * 如果过期需要重新请求下载接口获取新的 URL。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID
     * @return MinIO 预签名 URL 字符串
     */
    @Override
    public String getDownloadUrl(Long documentId, Long tenantId) {
        // ① 校验文档归属
        getAndCheckDocument(documentId, tenantId);

        // ② 查当前激活版本的 minio_bucket 和 minio_key
        DocumentVersionDO activeVersion = documentVersionMapper.selectActiveVersion(documentId);
        if (activeVersion == null) {
            throw BusinessException.of(2001, "文档激活版本不存在");
        }

        // ③ 生成预签名 URL（有效期 15 分钟）
        String url = minioUtil.getPresignedUrl(
                activeVersion.getMinioBucket(),
                activeVersion.getMinioKey(),
                PRESIGNED_EXPIRE_MINUTES);
        log.info("预签名下载 URL 已生成: docId={}, expireMinutes={}",
                documentId, PRESIGNED_EXPIRE_MINUTES);

        return url;
    }

    /**
     * 设置文档过期时间。
     * <p>
     * expireAt 为 null 时表示永不过期（清除已有的过期时间设置）。
     * 过期时间仅作为标记，实际的过期清理逻辑需要定时任务配合（后续实现）。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID
     * @param expireAt   过期时间，null = 永不过期
     */
    @Override
    @Transactional
    public void setExpireAt(Long documentId, Long tenantId, LocalDateTime expireAt) {
        // ① 校验文档归属
        getAndCheckDocument(documentId, tenantId);

        // ② 更新 document.expire_at 字段
        documentMapper.update(null,
                new LambdaUpdateWrapper<DocumentDO>()
                        .eq(DocumentDO::getId, documentId)
                        .set(DocumentDO::getExpireAt, expireAt));
        log.info("文档过期时间已更新: docId={}, expireAt={}",
                documentId, expireAt != null ? expireAt : "永不过期");
    }

    /**
     * 删除文档及其所有关联资源。
     * <p>
     * <b>删除顺序说明（不可颠倒）：</b>
     * 对每个版本依次执行以下步骤（顺序与架构文档 5.3 一致）：
     * <ol>
     *     <li><b>删除 MinIO 文件</b> — 通过 document_version 中的 minio_bucket + minio_key 定位，
     *         必须最先执行，否则存储路径丢失后文件变成孤儿</li>
     *     <li><b>删除 Milvus 向量</b>（TODO - Step 5 实现）— 通过 doc_chunk 中的 milvus_id 批量删除，
     *         必须在删 doc_chunk 之前执行，因为 milvus_id 存在 doc_chunk 表中</li>
     *     <li><b>删除 doc_chunk 记录</b> — Milvus 向量已清理完毕后再删 MySQL 记录</li>
     *     <li><b>删除 document_version 记录</b> — 以上资源全部清理完毕后，安全删除版本记录</li>
     * </ol>
     * 最后删除 document 记录本身。
     * <p>
     * <b>为什么这个顺序至关重要：</b>
     * 如果先删 MySQL 记录（doc_chunk / document_version），那么 milvus_id 和 minio_key 就丢失了，
     * MinIO 中的文件和 Milvus 中的向量会变成"孤儿"数据，永远无法被追踪和清理。
     *
     * @param documentId 文档ID
     * @param tenantId   租户ID
     */
    @Override
    @Transactional
    public void deleteDocument(Long documentId, Long tenantId) {
        // ① 校验文档归属
        getAndCheckDocument(documentId, tenantId);

        // ② 查出该文档所有版本
        List<DocumentVersionDO> versions = documentVersionMapper.selectVersionList(documentId);
        log.info("开始删除文档: docId={}, 版本数={}", documentId, versions.size());

        // ③ 对每个版本依次清理关联资源
        for (DocumentVersionDO version : versions) {
            log.info("清理版本资源: docId={}, versionId={}, versionNo={}",
                    documentId, version.getId(), version.getVersionNo());

            // ③-a 删除 MinIO 文件（第 1 步）
            // 必须最先执行：如果先删 MySQL 记录，minio_bucket / minio_key 信息丢失，
            // MinIO 中的文件会变成无法追踪的"孤儿文件"
            minioUtil.deleteFile(version.getMinioBucket(), version.getMinioKey());

            // ③-b 查出该版本下所有 doc_chunk 的 milvus_id 列表
            // 必须在删 doc_chunk 之前查出，否则 milvus_id 丢失后向量无法清理
            List<DocChunkDO> chunks = docChunkMapper.selectByVersionId(version.getId());
            List<String> milvusIds = chunks.stream()
                    .map(DocChunkDO::getMilvusId)
                    .collect(Collectors.toList());

            // ③-c 【TODO - Step 5 实现】批量删除 Milvus 向量（第 2 步）
            // 通过 milvus_id 列表在 tenant_{tenantId}_docs Collection 中批量删除向量
            // 必须在删除 doc_chunk 之前执行，否则 milvus_id 信息丢失
            if (!milvusIds.isEmpty()) {
                // TODO: Step 5 实现 Milvus 向量删除
                // MilvusUtil.deleteVectors("tenant_" + tenantId + "_docs", milvusIds)
                log.info("待清理 Milvus 向量: versionId={}, milvusIds 数量={}",
                        version.getId(), milvusIds.size());
            }

            // ③-d 删除 doc_chunk 记录（第 3 步）
            int deletedChunks = docChunkMapper.deleteByVersionId(version.getId());
            log.info("doc_chunk 记录已删除: versionId={}, 删除数量={}",
                    version.getId(), deletedChunks);

            // ③-e 删除 document_version 记录（第 4 步）
            documentVersionMapper.deleteById(version.getId());
            log.info("版本记录已删除: versionId={}", version.getId());
        }

        // ④ 删除 document 记录本身
        documentMapper.deleteById(documentId);
        log.info("文档删除完成: docId={}", documentId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 查询文档并校验租户归属。
     * <p>
     * 虽然租户拦截器会自动追加 tenant_id 条件，
     * 但此处做二次校验作为防御性编程，防止意外的跨租户访问。
     *
     * @param documentId 文档ID
     * @param tenantId   期望的租户ID
     * @return 文档实体
     * @throws BusinessException 文档不存在或不属于当前租户时抛出
     */
    private DocumentDO getAndCheckDocument(Long documentId, Long tenantId) {
        DocumentDO doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw BusinessException.of(2001, "文档不存在");
        }
        checkTenantOwnership(doc, tenantId);
        return doc;
    }

    /**
     * 校验文档的 tenant_id 是否与当前租户匹配。
     *
     * @param doc      文档实体
     * @param tenantId 当前租户ID
     * @throws BusinessException 不匹配时抛出（code=2002）
     */
    private void checkTenantOwnership(DocumentDO doc, Long tenantId) {
        if (!doc.getTenantId().equals(tenantId)) {
            throw BusinessException.of(2002, "无权操作此文档");
        }
    }

    /**
     * DocumentDO → DocumentVO 转换（不含版本信息）。
     */
    private DocumentVO convertToVO(DocumentDO doc) {
        return DocumentVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .fileType(doc.getFileType())
                .status(doc.getStatus())
                .expireAt(doc.getExpireAt())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    /**
     * DocumentVersionDO → VersionVO 转换。
     */
    private VersionVO convertToVersionVO(DocumentVersionDO version) {
        return VersionVO.builder()
                .id(version.getId())
                .versionNo(version.getVersionNo())
                .fileSize(version.getFileSize())
                .isActive(version.getIsActive())
                .createdAt(version.getCreatedAt())
                .build();
    }
}

