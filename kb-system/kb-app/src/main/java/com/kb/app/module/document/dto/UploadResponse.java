package com.kb.app.module.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档上传接口响应 DTO — 对应 POST /api/docs/upload 的返回数据。
 * <p>
 * 前端拿到响应后的处理流程：
 * <ol>
 *     <li>保存 docId，用于后续查看文档详情</li>
 *     <li>使用 taskId 每 2 秒轮询 GET /api/tasks/{taskId}/status，
 *         获取异步处理进度（progress: 10→30→60→90→100）和状态（RUNNING/DONE/FAILED）</li>
 *     <li>轮询到 status=DONE 后停止轮询，展示"处理完成"</li>
 *     <li>轮询到 status=FAILED 后停止轮询，展示错误信息</li>
 * </ol>
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    /** 文档ID（新建文档的 ID 或已存在同名文档的 ID） */
    private Long docId;

    /** 异步任务ID，前端凭此 ID 轮询处理进度 */
    private Long taskId;
}
