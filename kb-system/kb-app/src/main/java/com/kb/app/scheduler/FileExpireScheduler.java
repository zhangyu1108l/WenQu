package com.kb.app.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kb.app.module.document.entity.DocumentDO;
import com.kb.app.module.document.entity.DocumentVersionDO;
import com.kb.app.module.document.mapper.DocumentMapper;
import com.kb.app.module.document.mapper.DocumentVersionMapper;
import com.kb.app.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件过期自动清理定时任务 — 定期清理已过期文档的 MinIO 原始文件。
 * <p>
 * <b>执行时机：</b>每天凌晨 2:00 执行一次。
 * <p>
 * <b>业务目的：</b>
 * 租户管理员可以为文档设置过期时间（PUT /api/docs/{id}/expire），
 * 到期后本定时任务自动清理 MinIO 中存储的原始文件，释放对象存储空间。
 * <p>
 * <b>为什么只删 MinIO 文件，不删 Milvus 向量和 doc_chunk 记录？</b>
 * <ul>
 *     <li>过期的目的是<b>清理存储空间</b>（MinIO 中的 PDF/DOCX 原始文件通常较大），
 *         而非彻底废弃文档的知识价值</li>
 *     <li>Milvus 向量和 doc_chunk 记录占用空间极小，保留后用户仍然可以
 *         基于这些向量进行 RAG 问答，文档的知识仍然可用</li>
 *     <li>如果用户需要彻底删除文档（包括向量），应使用 DELETE /api/docs/{id} 接口</li>
 * </ul>
 * <p>
 * <b>租户隔离说明：</b>
 * 定时任务运行在后台线程中，没有 HTTP 请求上下文，TenantContext.getTenantId() 为 null。
 * 此时 TenantInterceptor 返回 NullValue，不追加 tenant_id 条件，
 * 允许跨租户扫描所有过期文档 — 这是定时任务的预期行为。
 *
 * @author kb-system
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class FileExpireScheduler {

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final MinioUtil minioUtil;

    /**
     * 定时清理过期文档的 MinIO 原始文件。
     * <p>
     * <b>cron 表达式 "0 0 2 * * ?" 各字段含义：</b>
     * <pre>
     *   秒  分  时  日  月  星期
     *   0   0   2   *   *   ?
     *   ↓   ↓   ↓   ↓   ↓   ↓
     *   第0秒 第0分 2时 每日 每月 不指定星期
     * </pre>
     * 即：每天凌晨 02:00:00 执行一次。
     * <p>
     * <b>处理逻辑：</b>
     * <ol>
     *     <li>查询所有 expire_at 不为 null 且 expire_at 小于当前时间的文档（跨租户）</li>
     *     <li>对每个过期文档，查出所有版本的 minio_bucket / minio_key</li>
     *     <li>调用 MinioUtil 删除每个版本的 MinIO 文件</li>
     *     <li>将 document.expire_at 置为 null，标记已处理，避免下次重复执行</li>
     * </ol>
     * <p>
     * 单个文档清理失败不影响其他文档的清理，异常被捕获并记录日志。
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void cleanExpiredFiles() {
        log.info("===== 文件过期清理任务开始 =====");

        // ① 查询所有过期文档：expire_at IS NOT NULL AND expire_at < NOW()
        // 定时任务无 TenantContext，拦截器不追加 tenant_id 条件，实现跨租户扫描
        List<DocumentDO> expiredDocs = documentMapper.selectList(
                new LambdaQueryWrapper<DocumentDO>()
                        .isNotNull(DocumentDO::getExpireAt)
                        .lt(DocumentDO::getExpireAt, LocalDateTime.now()));

        if (expiredDocs.isEmpty()) {
            log.info("没有过期文档需要清理");
            log.info("===== 文件过期清理任务结束 =====");
            return;
        }

        log.info("发现 {} 个过期文档需要清理", expiredDocs.size());

        int successCount = 0;
        int failCount = 0;

        for (DocumentDO doc : expiredDocs) {
            try {
                cleanSingleDocument(doc);
                successCount++;
            } catch (Exception e) {
                // 单个文档清理失败不影响其他文档，记录日志后继续
                failCount++;
                log.error("文档过期清理失败: docId={}, title={}, error={}",
                        doc.getId(), doc.getTitle(), e.getMessage(), e);
            }
        }

        log.info("===== 文件过期清理任务结束: 成功={}, 失败={} =====", successCount, failCount);
    }

    /**
     * 清理单个过期文档的 MinIO 文件。
     * <p>
     * 步骤：
     * <ol>
     *     <li>查出该文档所有版本的存储信息</li>
     *     <li>依次删除每个版本在 MinIO 中的原始文件</li>
     *     <li>将 expire_at 置为 null，标记已处理</li>
     * </ol>
     *
     * @param doc 过期文档实体
     */
    private void cleanSingleDocument(DocumentDO doc) {
        log.info("开始清理过期文档: docId={}, title={}, expireAt={}",
                doc.getId(), doc.getTitle(), doc.getExpireAt());

        // ② 查出该文档所有版本
        List<DocumentVersionDO> versions = documentVersionMapper.selectVersionList(doc.getId());

        // ③ 删除每个版本的 MinIO 文件
        for (DocumentVersionDO version : versions) {
            try {
                minioUtil.deleteFile(version.getMinioBucket(), version.getMinioKey());
                log.info("MinIO 文件已删除: docId={}, versionNo={}, objectKey={}",
                        doc.getId(), version.getVersionNo(), version.getMinioKey());
            } catch (Exception e) {
                // 文件可能已被手动删除或不存在，记录警告但不中断流程
                log.warn("MinIO 文件删除失败（可能已不存在）: docId={}, versionNo={}, error={}",
                        doc.getId(), version.getVersionNo(), e.getMessage());
            }
        }

        // ④ 将 expire_at 置为 null，标记已处理，避免下次定时任务重复清理
        documentMapper.update(null,
                new LambdaUpdateWrapper<DocumentDO>()
                        .eq(DocumentDO::getId, doc.getId())
                        .set(DocumentDO::getExpireAt, null));
        log.info("文档过期标记已清除: docId={}", doc.getId());
    }
}

