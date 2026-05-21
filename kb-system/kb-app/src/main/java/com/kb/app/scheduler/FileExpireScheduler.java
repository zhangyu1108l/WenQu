package com.kb.app.scheduler;

import com.kb.app.module.document.entity.DocumentDO;
import com.kb.app.module.document.entity.DocumentVersionDO;
import com.kb.app.module.document.mapper.DocumentMapper;
import com.kb.app.module.document.mapper.DocumentVersionMapper;
import com.kb.app.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件过期自动清理定时任务。
 * <p>
 * 文件过期只删除 MinIO 原始文件，不删除 Milvus 向量和 doc_chunk 记录。
 * 这样做是为了节省对象存储空间，同时保留向量数据，让用户仍可基于文档知识进行问答。
 * <p>
 * 本任务是平台级操作，需要跨所有租户处理过期文件，因此查询时使用跳过租户拦截的方法，
 * 不追加 tenant_id 过滤条件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileExpireScheduler {

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final MinioUtil minioUtil;

    /**
     * 每天凌晨 2 点清理已过期的 MinIO 原始文件。
     * <p>
     * cron 表达式逐字段说明：
     * <pre>
     * 秒 分 时 日 月 周
     * 0  0  2  *  *  ?  = 每天凌晨2点0分0秒
     * </pre>
     * <p>
     * 将 document.expire_at 置为 null 的目的是标记该文档的原始文件已处理，
     * 防止下次定时任务重复处理同一文档。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredFiles() {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("文件过期自动清理任务开始：startTime={}", startTime);

        // 跨租户查询：定时任务是平台级操作，需要处理所有租户的过期文件。
        List<DocumentDO> expiredDocs = documentMapper.selectExpiredIgnoreTenant(startTime);

        int successCount = 0;
        int failCount = 0;
        for (DocumentDO document : expiredDocs) {
            try {
                cleanSingleDocument(document);
                successCount++;
            } catch (Exception e) {
                // 单个失败继续处理：防止一个文档异常导致所有文件都不被清理。
                failCount++;
                log.error("过期文件清理失败：documentId={}，title={}，expireAt={}",
                        document.getId(), document.getTitle(), document.getExpireAt(), e);
            }
        }

        log.info("文件过期自动清理任务结束：endTime={}，processedCount={}，successCount={}，failCount={}",
                LocalDateTime.now(), expiredDocs.size(), successCount, failCount);
    }

    private void cleanSingleDocument(DocumentDO document) {
        DocumentVersionDO activeVersion = documentVersionMapper.selectActiveVersion(document.getId());
        if (activeVersion == null) {
            throw new IllegalStateException("文档当前激活版本不存在");
        }

        minioUtil.deleteFile(activeVersion.getMinioBucket(), activeVersion.getMinioKey());

        // 只清除过期标记，不删除向量：文件过期是为了节省存储空间，向量保留让用户仍可问答。
        documentMapper.clearExpireAtIgnoreTenant(document.getId());
        log.info("过期原始文件已删除：documentId={}，title={}，bucket={}，objectKey={}",
                document.getId(), document.getTitle(),
                activeVersion.getMinioBucket(), activeVersion.getMinioKey());
    }
}
