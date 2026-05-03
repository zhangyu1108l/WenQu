package com.kb.app.module.document.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文档上传 Service 接口 — 定义文档上传入口。
 * <p>
 * 设计思路：upload 方法为同步入口，完成必要的校验和记录创建后立即返回 {docId, taskId}，
 * 不让用户等待耗时的解析、Embedding 等操作。耗时操作由 @Async 异步线程池执行，
 * 前端通过 GET /api/tasks/{taskId}/status 每 2 秒轮询进度。
 *
 * @author kb-system
 */
public interface DocUploadService {

    /**
     * 上传文档。
     * <p>
     * 同步执行：校验文件类型 → 判断同名文档 → 创建任务 → 读取文件字节 → 触发异步处理 → 立即返回。
     * <p>
     * <b>设计思路：</b>文档处理（解析、Embedding、写 Milvus）耗时较长（几秒到几十秒），
     * 如果同步等待会导致 HTTP 请求超时，用户体验极差。
     * 因此采用"同步创建 + 异步处理"模式：
     * <ul>
     *     <li>同步部分：立即返回 taskId，用户无需等待</li>
     *     <li>异步部分：后台线程池处理，通过 async_task 表记录进度</li>
     *     <li>前端轮询：每 2 秒查询 /api/tasks/{taskId}/status，展示处理阶段和进度条</li>
     * </ul>
     *
     * @param file     上传的文件（仅支持 pdf / docx）
     * @param tenantId 租户ID
     * @param userId   上传用户ID
     * @return 包含 docId 和 taskId 的 Map
     */
    Map<String, Long> upload(MultipartFile file, Long tenantId, Long userId);
}
