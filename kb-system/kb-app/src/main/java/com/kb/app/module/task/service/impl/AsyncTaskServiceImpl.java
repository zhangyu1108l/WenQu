package com.kb.app.module.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kb.app.context.TenantContext;
import com.kb.app.module.task.dto.TaskStatusVO;
import com.kb.app.module.task.entity.AsyncTaskDO;
import com.kb.app.module.task.mapper.AsyncTaskMapper;
import com.kb.app.module.task.service.AsyncTaskService;
import com.kb.common.enums.TaskStatus;
import com.kb.common.enums.TaskType;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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

    private static final int ERROR_MSG_MAX_LENGTH = 500;
    private static final int STALE_TASK_TIMEOUT_MINUTES = 60;
    private static final String STALE_TASK_ERROR_MSG = "任务超时：服务重启或处理超时，请重新上传";

    private final AsyncTaskMapper asyncTaskMapper;

    /**
     * 创建异步任务记录。
     * <p>
     * 插入一条 status=PENDING、progress=0 的记录到 async_task 表。
     * 该方法在触发接口中同步调用，保证接口可以立即拿到 taskId 返回给前端。
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
        log.info("异步任务已创建：任务ID={}，任务类型={}，业务ID={}，租户ID={}",
                task.getId(), taskType, bizId, tenantId);
        return task;
    }

    /**
     * 更新任务状态为 RUNNING，并同步更新进度。
     * <p>
     * running 同时修改 status 和 progress，updateProgress 只修改 progress。
     * 使用 LambdaUpdateWrapper 精准更新必要字段，不查询全量数据，减少异步流程中的数据库开销。
     * updated_at 由数据库 ON UPDATE CURRENT_TIMESTAMP 自动刷新。
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
                        .set(AsyncTaskDO::getProgress, progress)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.info("任务状态更新：任务ID={}，状态=运行中，进度={}", taskId, progress);
    }

    /**
     * 仅更新进度字段，不改变状态。
     * <p>
     * running 同时修改 status 和 progress，updateProgress 只修改 progress。
     * 本方法用于任务已处于 RUNNING 状态时刷新进度，不改变当前状态。
     *
     * @param taskId   任务ID
     * @param progress 当前进度（0~100）
     */
    @Override
    public void updateProgress(Long taskId, int progress) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getProgress, progress)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.info("任务进度更新：任务ID={}，进度={}", taskId, progress);
    }

    /**
     * 标记任务完成。
     * <p>
     * 更新 status=DONE、progress=100。
     * progress 固定设为 100，保证前端进度条满格展示。
     *
     * @param taskId 任务ID
     */
    @Override
    public void done(Long taskId) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.DONE.name())
                        .set(AsyncTaskDO::getProgress, 100)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.info("任务已完成：任务ID={}", taskId);
    }

    /**
     * 标记任务失败。
     * <p>
     * 更新 status=FAILED，写入错误信息。
     * errorMsg 超过 500 字符时截断，避免异常堆栈过长超出字段限制导致写库失败。
     *
     * @param taskId   任务ID
     * @param errorMsg 失败原因描述
     */
    @Override
    public void fail(Long taskId, String errorMsg) {
        // async_task.error_msg 字段最大 500 字符，异常堆栈可能很长，必须截断后再入库。
        String truncatedMsg = errorMsg != null && errorMsg.length() > ERROR_MSG_MAX_LENGTH
                ? errorMsg.substring(0, ERROR_MSG_MAX_LENGTH)
                : errorMsg;
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.FAILED.name())
                        .set(AsyncTaskDO::getErrorMsg, truncatedMsg)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.error("任务失败：任务ID={}，错误信息={}", taskId, truncatedMsg);
    }

    /**
     * 查询任务状态。
     * <p>
     * 此方法是前端轮询 GET /api/tasks/{taskId}/status 的直接依赖。
     *
     * @param taskId 任务ID
     * @return 任务状态展示 VO
     */
    @Override
    public TaskStatusVO getStatus(Long taskId) {
        AsyncTaskDO task = asyncTaskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.of(7001, "任务不存在");
        }
        return toStatusVO(task);
    }

    /**
     * 根据业务ID和任务类型查询最新任务状态。
     * <p>
     * 使用场景：文档上传后页面刷新，前端可根据 docId 恢复最近一次处理任务的进度。
     * 不存在时返回 null，由调用方自行判断。
     *
     * @param bizId    关联业务ID（文档ID 或 eval_batch_id）
     * @param taskType 任务类型（DOC_PROCESS / RAGAS_EVAL）
     * @return 最新任务状态；不存在时返回 null
     */
    @Override
    public TaskStatusVO getByBizId(Long bizId, String taskType) {
        AsyncTaskDO task = asyncTaskMapper.selectByBizId(bizId, taskType);
        return task == null ? null : toStatusVO(task);
    }

    /**
     * 标记超时的 RUNNING 任务为失败。
     * <p>
     * 服务重启后，内存中的 @Async 线程会全部消失，任务可能永久卡在 RUNNING 状态，
     * 用户无法感知任务已经失败，因此需要在 @PostConstruct 或定时任务中调用本方法做恢复清理。
     * <p>
     * 超时阈值使用 60 分钟：正常文档处理通常不超过 10 分钟，60 分钟可确认是异常任务。
     */
    @Override
    public void markStaleTasksFailed() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_TASK_TIMEOUT_MINUTES);
        int checkedCount = 0;
        int failedCount = 0;

        for (TaskType taskType : TaskType.values()) {
            List<AsyncTaskDO> runningTasks = asyncTaskMapper.selectStaleRunningTasks(taskType.name(), cutoff);
            checkedCount += runningTasks.size();

            for (AsyncTaskDO task : runningTasks) {
                LocalDateTime updatedAt = task.getUpdatedAt();
                failWithTaskTenant(task, STALE_TASK_ERROR_MSG);
                failedCount++;
                log.warn("RUNNING 超时任务已标记失败：任务ID={}，任务类型={}，业务ID={}，租户ID={}，最后更新时间={}",
                        task.getId(), task.getTaskType(), task.getBizId(), task.getTenantId(), updatedAt);
            }
        }

        log.info("RUNNING 超时任务扫描完成：检查数量={}，标记失败数量={}，超时阈值={}分钟",
                checkedCount, failedCount, STALE_TASK_TIMEOUT_MINUTES);
    }

    private TaskStatusVO toStatusVO(AsyncTaskDO task) {
        return TaskStatusVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .progress(task.getProgress() == null ? 0 : task.getProgress())
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private void failWithTaskTenant(AsyncTaskDO task, String errorMsg) {
        Long previousTenantId = TenantContext.getTenantId();
        Long previousUserId = TenantContext.getUserId();
        try {
            TenantContext.clear();
            TenantContext.setTenantId(task.getTenantId());
            fail(task.getId(), errorMsg);
        } finally {
            TenantContext.clear();
            if (previousTenantId != null) {
                TenantContext.setTenantId(previousTenantId);
            }
            if (previousUserId != null) {
                TenantContext.setUserId(previousUserId);
            }
        }
    }
}
