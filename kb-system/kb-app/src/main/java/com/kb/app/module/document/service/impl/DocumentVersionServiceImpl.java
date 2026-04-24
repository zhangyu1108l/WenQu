package com.kb.app.module.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kb.app.module.document.entity.DocChunkDO;
import com.kb.app.module.document.entity.DocumentVersionDO;
import com.kb.app.module.document.mapper.DocChunkMapper;
import com.kb.app.module.document.mapper.DocumentVersionMapper;
import com.kb.app.module.document.service.DocumentVersionService;
import com.kb.app.util.MinioUtil;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档版本管理 Service 实现类 — 版本创建、旧版本清理、版本查询。
 * <p>
 * <b>核心设计约束：</b>
 * <ul>
 *     <li>每个文档最多保留 5 个版本，超出时自动删除 version_no 最小的旧版本</li>
 *     <li>删除旧版本必须按严格顺序：① MinIO 文件 → ② Milvus 向量 → ③ doc_chunk 记录 → ④ document_version 记录。
 *         如果先删 MySQL，milvus_id 就找不到了，向量会变成孤儿永远无法清理</li>
 *     <li>每个文档同时只有一个激活版本（is_active=1），新版本上传后自动切换激活</li>
 * </ul>
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersionServiceImpl implements DocumentVersionService {

    private final DocumentVersionMapper documentVersionMapper;
    private final DocChunkMapper docChunkMapper;
    private final MinioUtil minioUtil;

    /** 每个文档最多保留的版本数量 */
    private static final int MAX_VERSION_COUNT = 5;

    /**
     * 创建新版本。
     * <p>
     * <b>objectKey 路径规则：</b>docs/{documentId}/v{versionNo}/{filename}
     * <ul>
     *     <li>docs/ — 固定前缀，与其他类型文件区分（如后续可能的头像、导出文件等）</li>
     *     <li>{documentId} — 按文档 ID 分组，便于按文档查找所有版本</li>
     *     <li>v{versionNo} — 版本号目录，支持同一文档多版本并存</li>
     *     <li>{filename} — 保留原始文件名，下载时可识别文件类型</li>
     * </ul>
     * <p>
     * <b>步骤 ⑤⑥ 顺序不能颠倒：</b>必须先取消旧版本激活（is_active=0），
     * 再插入新版本（is_active=1），保证任何时刻每个文档只有一个激活版本。
     * 如果先插入新版本再取消旧版本，会出现短暂的"两个激活版本"窗口期。
     *
     * @param documentId  文档ID
     * @param bucket      MinIO Bucket 名称（格式 tenant-{tenantId}）
     * @param fileBytes   文件字节数组
     * @param filename    原始文件名（含扩展名）
     * @param contentType MIME 类型，如 application/pdf
     * @return 新创建的版本实体
     */
    @Override
    @Transactional
    public DocumentVersionDO createVersion(Long documentId, String bucket, byte[] fileBytes,
                                           String filename, String contentType) {
        // ① 查询当前文档最大 version_no
        List<DocumentVersionDO> versions = documentVersionMapper.selectVersionList(documentId);
        int maxVersionNo = versions.isEmpty() ? 0 :
                versions.stream().mapToInt(DocumentVersionDO::getVersionNo).max().orElse(0);

        // ② version_no = 最大值 + 1
        int newVersionNo = maxVersionNo + 1;

        // ③ 构建 objectKey：docs/{documentId}/v{versionNo}/{filename}
        String objectKey = String.format("docs/%d/v%d/%s", documentId, newVersionNo, filename);

        // ④ 上传文件到 MinIO
        minioUtil.uploadFile(bucket, objectKey, fileBytes, contentType);
        log.info("新版本文件已上传: documentId={}, versionNo={}, objectKey={}",
                documentId, newVersionNo, objectKey);

        // ⑤ 将旧激活版本的 is_active 改为 0（必须在插入新版本之前）
        documentVersionMapper.update(null,
                new LambdaUpdateWrapper<DocumentVersionDO>()
                        .eq(DocumentVersionDO::getDocumentId, documentId)
                        .eq(DocumentVersionDO::getIsActive, 1)
                        .set(DocumentVersionDO::getIsActive, 0));

        // ⑥ 插入新版本记录，is_active = 1
        DocumentVersionDO newVersion = DocumentVersionDO.builder()
                .documentId(documentId)
                .versionNo(newVersionNo)
                .minioBucket(bucket)
                .minioKey(objectKey)
                .fileSize((long) fileBytes.length)
                .isActive(1)
                .build();
        documentVersionMapper.insert(newVersion);

        log.info("新版本记录已插入: documentId={}, versionNo={}, versionId={}",
                documentId, newVersionNo, newVersion.getId());

        // ⑦ 返回新版本的 DocumentVersionDO
        return newVersion;
    }

    /**
     * 清理旧版本。
     * <p>
     * 检查版本数是否超过 5 个，超出部分按 version_no 从小到大依次删除。
     * <p>
     * <b>删除顺序说明（不可颠倒）：</b>
     * <ol>
     *     <li><b>删 MinIO 文件</b> — 如果先删 MySQL 记录，minio_key 就找不到了，文件变成孤儿</li>
     *     <li><b>删 Milvus 向量</b> — 通过 doc_chunk 中的 milvus_id 批量删除，
     *         如果先删 doc_chunk，milvus_id 就丢失了，向量无法清理</li>
     *     <li><b>删 doc_chunk 记录</b> — Milvus 向量已清理完毕，安全删除 chunk 数据</li>
     *     <li><b>删 document_version 记录</b> — 最后删除版本记录，确保以上资源全部清理完毕</li>
     * </ol>
     * <p>
     * <b>为什么这个顺序至关重要：</b>
     * 如果先删 MySQL（doc_chunk / document_version），那么 milvus_id 和 minio_key 就丢失了，
     * MinIO 中的文件和 Milvus 中的向量会变成"孤儿"数据，永远无法被追踪和清理，
     * 随着时间推移会不断累积，浪费存储空间。
     *
     * @param documentId 文档ID
     */
    @Override
    @Transactional
    public void cleanOldVersions(Long documentId) {
        // ① 查询该文档所有版本，按 version_no 升序（最旧的在前）
        List<DocumentVersionDO> versions = documentVersionMapper.selectVersionList(documentId);
        // selectVersionList 返回降序，这里反转为升序（version_no 从小到大）
        List<DocumentVersionDO> ascVersions = versions.stream()
                .sorted((a, b) -> a.getVersionNo() - b.getVersionNo())
                .collect(Collectors.toList());

        // ② 如果版本数 <= 5，直接返回，无需清理
        if (ascVersions.size() <= MAX_VERSION_COUNT) {
            return;
        }

        // ③ 计算需要删除的版本数量（总数 - 5）
        int deleteCount = ascVersions.size() - MAX_VERSION_COUNT;
        log.info("版本数超过上限，需要清理: documentId={}, 总版本数={}, 需删除={}",
                documentId, ascVersions.size(), deleteCount);

        // ④ 对每个需要删除的旧版本，按顺序执行清理
        for (int i = 0; i < deleteCount; i++) {
            DocumentVersionDO oldVersion = ascVersions.get(i);
            log.info("开始清理旧版本: documentId={}, versionNo={}, versionId={}",
                    documentId, oldVersion.getVersionNo(), oldVersion.getId());

            // ④-a 从 MinIO 删除原始文件
            minioUtil.deleteFile(oldVersion.getMinioBucket(), oldVersion.getMinioKey());

            // ④-b 查出该版本下所有 doc_chunk 的 milvus_id 列表
            List<DocChunkDO> chunks = docChunkMapper.selectByVersionId(oldVersion.getId());
            List<String> milvusIds = chunks.stream()
                    .map(DocChunkDO::getMilvusId)
                    .collect(Collectors.toList());

            // ④-c 调用 Milvus 批量删除向量（Step 5 实现，当前留 TODO）
            if (!milvusIds.isEmpty()) {
                // TODO: Step 5 实现 Milvus 向量删除，调用 MilvusUtil.deleteVectors(collectionName, milvusIds)
                log.info("待清理 Milvus 向量: versionId={}, milvusIds 数量={}", oldVersion.getId(), milvusIds.size());
            }

            // ④-d 删除 doc_chunk 记录
            int deletedChunks = docChunkMapper.deleteByVersionId(oldVersion.getId());
            log.info("doc_chunk 记录已删除: versionId={}, 删除数量={}", oldVersion.getId(), deletedChunks);

            // ④-e 删除 document_version 记录
            documentVersionMapper.deleteById(oldVersion.getId());
            log.info("旧版本清理完成: documentId={}, versionNo={}", documentId, oldVersion.getVersionNo());
        }
    }

    /**
     * 查询文档所有版本列表，按 version_no 降序排列（最新版本在前）。
     * <p>
     * 调用时机：GET /api/docs/{id}/versions 接口，返回该文档的历史版本列表（最多 5 个）。
     *
     * @param documentId 文档ID
     * @return 版本列表，按 version_no 降序
     */
    @Override
    public List<DocumentVersionDO> getVersionList(Long documentId) {
        return documentVersionMapper.selectVersionList(documentId);
    }

    /**
     * 查询当前激活版本。
     * <p>
     * 调用时机：
     * <ul>
     *     <li>文档下载接口，获取激活版本的 minio_bucket + minio_key 生成预签名 URL</li>
     *     <li>文档详情接口，返回当前激活版本信息</li>
     * </ul>
     *
     * @param documentId 文档ID
     * @return 激活版本实体
     * @throws BusinessException 激活版本不存在时抛出（code=2001）
     */
    @Override
    public DocumentVersionDO getActiveVersion(Long documentId) {
        DocumentVersionDO activeVersion = documentVersionMapper.selectActiveVersion(documentId);
        if (activeVersion == null) {
            throw BusinessException.of(2001, "文档激活版本不存在");
        }
        return activeVersion;
    }
}

