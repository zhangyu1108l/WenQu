package com.kb.app.module.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kb.app.module.task.entity.AsyncTaskDO;
import com.kb.app.module.task.mapper.AsyncTaskMapper;
import com.kb.app.module.task.service.AsyncTaskService;
import com.kb.common.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 异步任务 Service 实现类 — 管理任务的创建与状态流转。
 * <p>
 * <b>状态机：</b>PENDING → RUNNING → DONE / FAILED
 * <p>
 * <b>进度约定（文档处理任务）：</b>
 * <ul>
 *     <li>10% — 开始处理</li>
 *     <li>30% — MinIO 存储 + 版本记录完成</li>
 *     <li>60% — Python 侧车解析完成</li>
 *     <li>90% — Embedding + Milvus 写入完成</li>
 *     <li>100% — 全部完成（status=DONE）</li>
 * </ul>
 * <p>
 * <b>注意：</b>本类的方法不加 @Transactional，因为调用方通常在 @Async 线程中，
 * 且每次更新都是独立的单条 UPDATE，无需事务保护。
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskServiceImpl implements AsyncTaskService {

    private final AsyncTaskMapper asyncTaskMapper;

    /**
     * 创建异步任务记录。
     * <p>
     * 插入一条 status=PENDING、progress=0 的记录到 async_task 表。
     * MyBatis-Plus insert 后会自动回填 id，调用方可通过 task.getId() 获取 taskId。
     *
     * @param taskType 任务类型（DOC_PROCESS / RAGAS_EVAL）
     * @param bizId    关联业务ID（文档ID 或 eval_batch_id）
     * @param tenantId 租户ID
     * @return 新创建的任务实体（含自增 id）
     */
    @Override
    public AsyncTaskDO create(String taskType, Long bizId, Long tenantId) {
        AsyncTaskDO task = AsyncTaskDO.builder()
                .taskType(taskType)
                .bizId(bizId)
                .tenantId(tenantId)
                .status(TaskStatus.PENDING.name())
                .progress(0)
                .build();
        asyncTaskMapper.insert(task);
        log.info("异步任务已创建: taskId={}, taskType={}, bizId={}, tenantId={}",
                task.getId(), taskType, bizId, tenantId);
        return task;
    }

    /**
     * 更新任务状态为 RUNNING，并同步更新进度。
     * <p>
     * 使用 LambdaUpdateWrapper 精准更新 status 和 progress 两个字段，
     * 避免构建完整实体对象。
     *
     * @param taskId   任务ID
     * @param progress 当前进度（0~100）
     */
    @Override
    public void running(Long taskId, int progress) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.RUNNING.name())
                        .set(AsyncTaskDO::getProgress, progress));
        log.info("任务状态更新: taskId={}, status=RUNNING, progress={}", taskId, progress);
    }

    /**
     * 仅更新进度字段，不改变状态。
     *
     * @param taskId   任务ID
     * @param progress 当前进度（0~100）
     */
    @Override
    public void updateProgress(Long taskId, int progress) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getProgress, progress));
        log.info("任务进度更新: taskId={}, progress={}", taskId, progress);
    }

    /**
     * 标记任务完成。
     * <p>
     * 更新 status=DONE、progress=100。
     *
     * @param taskId 任务ID
     */
    @Override
    public void done(Long taskId) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.DONE.name())
                        .set(AsyncTaskDO::getProgress, 100));
        log.info("任务已完成: taskId={}", taskId);
    }

    /**
     * 标记任务失败。
     * <p>
     * 更新 status=FAILED，写入错误信息。
     * errorMsg 超过 500 字符时截断，避免写入数据库失败。
     *
     * @param taskId   任务ID
     * @param errorMsg 失败原因描述
     */
    @Override
    public void fail(Long taskId, String errorMsg) {
        // async_task.error_msg 字段最大 500 字符，截断防止入库异常
        String truncatedMsg = errorMsg != null && errorMsg.length() > 500
                ? errorMsg.substring(0, 500)
                : errorMsg;
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.FAILED.name())
                        .set(AsyncTaskDO::getErrorMsg, truncatedMsg));
        log.error("任务失败: taskId={}, errorMsg={}", taskId, truncatedMsg);
    }
}
