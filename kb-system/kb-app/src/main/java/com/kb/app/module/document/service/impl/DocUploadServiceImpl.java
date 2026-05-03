package com.kb.app.module.document.service.impl;

import com.kb.app.module.document.entity.DocumentDO;
import com.kb.app.module.document.mapper.DocumentMapper;
import com.kb.app.module.document.service.DocUploadService;
import com.kb.app.module.document.service.DocumentVersionService;
import com.kb.app.module.task.entity.AsyncTaskDO;
import com.kb.app.module.task.service.AsyncTaskService;
import com.kb.app.rag.client.ParserClient;
import com.kb.app.rag.dto.ChunkDTO;
import com.kb.app.util.MinioUtil;
import com.kb.common.enums.DocStatus;
import com.kb.common.enums.TaskType;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档上传 Service 实现类 — 同步创建 + 异步处理。
 * <p>
 * <b>整体设计：</b>
 * <ul>
 *     <li>{@link #upload} — 同步入口，完成校验和记录创建后立即返回 {docId, taskId}</li>
 *     <li>{@link #processAsync} — 异步执行，由 @Async 线程池处理文件存储、解析、Embedding 等耗时操作</li>
 * </ul>
 * <p>
 * <b>为什么分成两个方法：</b>
 * 文档处理全流程（MinIO 存储 → Python 解析 → Embedding → Milvus 写入）耗时几秒到几十秒，
 * 如果同步等待会导致 HTTP 连接超时。拆成"同步创建 + 异步处理"后：
 * <ul>
 *     <li>用户立即获得 taskId，无需等待</li>
 *     <li>前端每 2 秒轮询 GET /api/tasks/{taskId}/status 展示进度</li>
 *     <li>异步线程失败时自动标记任务和文档状态为 FAILED</li>
 * </ul>
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocUploadServiceImpl implements DocUploadService {

    private final DocumentMapper documentMapper;
    private final DocumentVersionService versionService;
    private final AsyncTaskService asyncTaskService;
    private final ParserClient parserClient;
    private final MinioUtil minioUtil;

    /** 允许上传的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx");

    /**
     * 上传文档（同步入口）。
     * <p>
     * <b>执行步骤：</b>
     * <ol>
     *     <li>校验文件类型（只允许 pdf / docx）</li>
     *     <li>根据 tenantId + 文件名判断是否存在同名文档（新建 or 新版本）</li>
     *     <li>创建 async_task 记录（PENDING 状态）</li>
     *     <li>在主线程读取文件字节（MultipartFile 在异步线程中可能已关闭）</li>
     *     <li>触发异步处理（不等待）</li>
     *     <li>立即返回 {docId, taskId}</li>
     * </ol>
     * <p>
     * <b>注意：</b>步骤 ④ 必须在主线程完成！
     * Spring MVC 在请求结束后会关闭 MultipartFile 的输入流，
     * 如果在异步线程中调用 file.getBytes()，可能抛出 "Stream closed" 异常。
     *
     * @param file     上传的文件
     * @param tenantId 租户ID
     * @param userId   上传用户ID
     * @return {docId, taskId}
     */
    @Override
    public Map<String, Long> upload(MultipartFile file, Long tenantId, Long userId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw BusinessException.of(3001, "文件名不能为空");
        }

        // ① 校验文件类型，只允许 pdf / docx
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BusinessException.of(3002, "不支持的文件类型，仅支持 pdf / docx");
        }

        // ② 根据 tenantId + 文件名（去扩展名）查是否存在同名文档
        String title = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        DocumentDO existingDoc = documentMapper.selectByTenantIdAndTitle(tenantId, title);

        DocumentDO doc;
        if (existingDoc != null) {
            // 同名文档已存在，使用已有的 documentId（后续创建新版本）
            doc = existingDoc;
            log.info("检测到同名文档，将创建新版本: docId={}, title={}", doc.getId(), title);
        } else {
            // 不存在同名文档，插入新 document 记录
            doc = DocumentDO.builder()
                    .tenantId(tenantId)
                    .uploaderId(userId)
                    .title(title)
                    .fileType(extension)
                    .status(DocStatus.PENDING.name())
                    .build();
            documentMapper.insert(doc);
            log.info("新文档记录已创建: docId={}, title={}, fileType={}", doc.getId(), title, extension);
        }

        // ③ 创建 async_task 记录（task_type=DOC_PROCESS，biz_id=documentId）
        AsyncTaskDO task = asyncTaskService.create(
                TaskType.DOC_PROCESS.name(), doc.getId(), tenantId);

        // ④ 在主线程读取文件字节
        // 重要：MultipartFile 的输入流绑定在当前 HTTP 请求上，
        // 请求结束后 Spring MVC 会自动清理临时文件，
        // 异步线程中再调用 file.getBytes() 会抛出 "Stream closed" 或 "File not found"。
        // 因此必须在主线程（同步方法）中提前读取字节数组。
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取文件字节失败: filename={}, error={}", originalFilename, e.getMessage());
            asyncTaskService.fail(task.getId(), "读取上传文件失败: " + e.getMessage());
            throw BusinessException.of(3003, "读取上传文件失败");
        }

        // ⑤ 调用异步方法执行后续处理（不等待）
        processAsync(doc, fileBytes, originalFilename, task.getId(), tenantId);

        // ⑥ 立即返回 {docId, taskId}
        Map<String, Long> result = new HashMap<>(4);
        result.put("docId", doc.getId());
        result.put("taskId", task.getId());
        return result;
    }

    /**
     * 异步执行文档处理管道。
     * <p>
     * <b>@Async("docProcessPool") 说明：</b>
     * 使用 AsyncConfig 中配置的 docProcessPool 线程池执行，
     * 线程池参数通过 application-dev.yml 的 async.thread-pool 配置。
     * 该方法在独立线程中运行，不会阻塞上传接口的响应。
     * <p>
     * <b>注意：</b>@Async 方法不加 @Transactional，
     * 因为事务在异步线程中与调用方线程的事务无关，
     * 且本方法中的数据库操作分散在多个步骤间，
     * 每步独立提交更符合进度追踪的需求。
     * <p>
     * <b>处理步骤：</b>
     * <ol>
     *     <li>10% — 开始处理</li>
     *     <li>确保 MinIO Bucket 存在</li>
     *     <li>30% — 存文件到 MinIO + 创建版本记录</li>
     *     <li>更新 document.status = PARSING（前端可据此展示"解析中"阶段）</li>
     *     <li>60% — 调用 Python 侧车解析文档</li>
     *     <li>更新 document.status = EMBEDDING（前端可据此展示"向量化中"阶段）</li>
     *     <li>90% — 【TODO - Step 5 实现】批量 Embedding + 写 Milvus + 写 doc_chunk</li>
     *     <li>更新 document.status = READY（文档可用于 RAG 检索）</li>
     *     <li>100% — 任务完成</li>
     *     <li>清理超出 5 个的旧版本</li>
     * </ol>
     *
     * @param doc          文档实体
     * @param fileBytes    文件字节数组（已在主线程读取）
     * @param filename     原始文件名（含扩展名）
     * @param taskId       异步任务ID
     * @param tenantId     租户ID
     */
    @Async("docProcessPool")
    public void processAsync(DocumentDO doc, byte[] fileBytes,
                             String filename, Long taskId, Long tenantId) {
        try {
            // ① 开始处理，更新进度 10%
            asyncTaskService.running(taskId, 10);

            // ② 确保 MinIO Bucket 存在（命名规则 tenant-{tenantId}）
            String bucket = "tenant-" + tenantId;
            minioUtil.createBucketIfNotExists(bucket);

            // ③ 存文件到 MinIO + 创建版本记录
            String contentType = resolveContentType(doc.getFileType());
            versionService.createVersion(doc.getId(), bucket, fileBytes, filename, contentType);
            asyncTaskService.running(taskId, 30);

            // ④ 更新 document.status = PARSING
            // 作用：前端轮询时可根据文档状态展示当前处理阶段（"解析中"）
            documentMapper.updateStatus(doc.getId(), DocStatus.PARSING.name());

            // ⑤ 调用 Python 解析侧车获取 chunk 列表
            List<ChunkDTO> chunks = parserClient.parse(fileBytes, doc.getFileType());
            asyncTaskService.running(taskId, 60);
            log.info("文档解析完成: docId={}, chunkCount={}", doc.getId(), chunks.size());

            // ⑥ 更新 document.status = EMBEDDING
            // 作用：前端轮询时可根据文档状态展示当前处理阶段（"向量化中"）
            documentMapper.updateStatus(doc.getId(), DocStatus.EMBEDDING.name());

            // ⑦ 【TODO - Step 5 实现】批量 Embedding + 写 Milvus + 写 doc_chunk
            // 依赖 Step 5 的 Milvus 配置和智谱 Embedding 集成：
            //   a. 对每个 chunk.content 调用智谱 embedding-3 生成 2048 维向量
            //   b. 将向量写入 Milvus Collection（tenant_{tenantId}_docs）
            //   c. 将 chunk 信息 + milvus_id 写入 doc_chunk 表
            // 当前跳过此步骤，直接进入下一步
            asyncTaskService.running(taskId, 90);

            // ⑧ 更新 document.status = READY
            // 作用：标记文档处理完成，可以参与 RAG 检索问答
            documentMapper.updateStatus(doc.getId(), DocStatus.READY.name());

            // ⑨ 任务完成
            asyncTaskService.done(taskId);
            log.info("文档处理完成: docId={}, taskId={}", doc.getId(), taskId);

            // ⑩ 清理旧版本（超过 5 个时自动删除最旧版本）
            versionService.cleanOldVersions(doc.getId());

        } catch (Exception e) {
            // 异步处理失败：标记任务和文档状态为 FAILED
            log.error("文档处理失败: docId={}, taskId={}, error={}",
                    doc.getId(), taskId, e.getMessage(), e);
            asyncTaskService.fail(taskId, e.getMessage());
            documentMapper.updateStatus(doc.getId(), DocStatus.FAILED.name());
        }
    }

    /**
     * 从文件名中提取扩展名（小写）。
     *
     * @param filename 原始文件名
     * @return 扩展名（如 pdf、docx），没有扩展名时返回空字符串
     */
    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 根据文件类型获取 MIME 类型。
     *
     * @param fileType 文件类型（pdf / docx）
     * @return MIME 类型字符串
     */
    private String resolveContentType(String fileType) {
        return switch (fileType) {
            case "pdf" -> "application/pdf";
            case "docx" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
