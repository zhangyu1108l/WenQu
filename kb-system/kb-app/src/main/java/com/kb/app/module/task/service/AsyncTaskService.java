package com.kb.app.module.task.service;

import com.kb.app.module.task.dto.TaskStatusVO;
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
     * 该方法在触发接口中同步调用，保证接口可以立即拿到 taskId 返回给前端。
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
     * 与 {@link #updateProgress(Long, int)} 的区别：
     * running 同时修改 status 和 progress，updateProgress 只修改 progress。
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
     * 与 {@link #running(Long, int)} 的区别：
     * running 同时修改 status 和 progress，updateProgress 只修改 progress。
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

    /**
     * 查询任务状态。
     * <p>
     * 此方法是前端轮询 GET /api/tasks/{taskId}/status 的直接依赖，
     * 需要返回状态、进度和失败原因等展示字段。
     *
     * @param taskId 任务ID
     * @return 任务状态展示 VO
     */
    TaskStatusVO getStatus(Long taskId);

    /**
     * 根据业务ID和任务类型查询最新任务状态。
     * <p>
     * 使用场景：文档上传后页面刷新，前端可根据 docId 恢复最近一次处理任务的进度。
     * 不存在时返回 null，由调用方自行判断是否需要展示进度。
     *
     * @param bizId    关联业务ID（文档ID 或 eval_batch_id）
     * @param taskType 任务类型（DOC_PROCESS / RAGAS_EVAL）
     * @return 最新任务状态；不存在时返回 null
     */
    TaskStatusVO getByBizId(Long bizId, String taskType);

    /**
     * 标记超时的 RUNNING 任务为失败。
     * <p>
     * 服务重启后，内存中的 @Async 线程会全部消失，任务可能永久卡在 RUNNING 状态，
     * 用户无法感知任务已经失败，因此需要在 @PostConstruct 或定时任务中调用本方法做恢复清理。
     * 超时阈值使用 60 分钟：正常文档处理通常不超过 10 分钟，60 分钟可确认是异常任务。
     */
    void markStaleTasksFailed();
}
