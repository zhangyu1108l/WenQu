package com.kb.app.module.task.service;

import com.kb.app.module.task.entity.AsyncTaskDO;

/**
 * 异步任务 Service 接口 — 定义任务生命周期管理操作。
 * <p>
 * 职责：
 * <ul>
 *     <li>创建任务记录（PENDING）</li>
 *     <li>状态流转：PENDING → RUNNING → DONE / FAILED</li>
 *     <li>进度更新：0 → 10 → 30 → 60 → 90 → 100</li>
 * </ul>
 * <p>
 * 调用方：
 * <ul>
 *     <li>DocUploadServiceImpl — 文档处理任务（DOC_PROCESS）</li>
 *     <li>EvalServiceImpl — Ragas 评估任务（RAGAS_EVAL，Step 9 实现）</li>
 * </ul>
 *
 * @author kb-system
 */
public interface AsyncTaskService {

    /**
     * 创建异步任务记录。
     * <p>
     * 插入一条 status=PENDING、progress=0 的记录到 async_task 表，
     * 调用方拿到返回的 AsyncTaskDO 后立即将 taskId 返回给前端，
     * 前端通过 GET /api/tasks/{taskId}/status 轮询进度。
     *
     * @param taskType 任务类型（DOC_PROCESS / RAGAS_EVAL）
     * @param bizId    关联业务ID（文档ID 或 eval_batch_id）
     * @param tenantId 租户ID
     * @return 新创建的任务实体（含自增 id）
     */
    AsyncTaskDO create(String taskType, Long bizId, Long tenantId);

    /**
     * 更新任务状态为 RUNNING，并同步更新进度。
     * <p>
     * 调用时机：异步处理流程中每个关键节点完成后调用，
     * 前端通过轮询可感知当前处理到哪一步。
     *
     * @param taskId   任务ID
     * @param progress 当前进度（0~100）
     */
    void running(Long taskId, int progress);

    /**
     * 仅更新进度字段，不改变状态。
     * <p>
     * 适用于状态已经是 RUNNING，只需更新进度值的场景。
     *
     * @param taskId   任务ID
     * @param progress 当前进度（0~100）
     */
    void updateProgress(Long taskId, int progress);

    /**
     * 标记任务完成。
     * <p>
     * 更新 status=DONE、progress=100，表示所有处理步骤执行成功。
     *
     * @param taskId 任务ID
     */
    void done(Long taskId);

    /**
     * 标记任务失败。
     * <p>
     * 更新 status=FAILED，写入错误信息，便于排查问题。
     * 前端轮询时可展示失败原因。
     *
     * @param taskId   任务ID
     * @param errorMsg 失败原因描述（最多 500 字符）
     */
    void fail(Long taskId, String errorMsg);
}
