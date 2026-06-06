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

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskServiceImpl implements AsyncTaskService {

    private static final int ERROR_MSG_MAX_LENGTH = 500;
    private static final int STALE_TASK_TIMEOUT_MINUTES = 60;
    private static final String STALE_TASK_ERROR_MSG = "任务超时：服务重启或处理超时，请重新触发";

    private final AsyncTaskMapper asyncTaskMapper;

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
        log.info("Async task created: taskId={}, taskType={}, bizId={}, tenantId={}",
                task.getId(), taskType, bizId, tenantId);
        return task;
    }

    @Override
    public void running(Long taskId, int progress) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.RUNNING.name())
                        .set(AsyncTaskDO::getProgress, progress)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.info("Async task running: taskId={}, progress={}", taskId, progress);
    }

    @Override
    public void updateProgress(Long taskId, int progress) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getProgress, progress)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.info("Async task progress updated: taskId={}, progress={}", taskId, progress);
    }

    @Override
    public void done(Long taskId) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.DONE.name())
                        .set(AsyncTaskDO::getProgress, 100)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.info("Async task done: taskId={}", taskId);
    }

    @Override
    public void fail(Long taskId, String errorMsg) {
        String truncatedMsg = errorMsg != null && errorMsg.length() > ERROR_MSG_MAX_LENGTH
                ? errorMsg.substring(0, ERROR_MSG_MAX_LENGTH)
                : errorMsg;
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTaskDO>()
                        .eq(AsyncTaskDO::getId, taskId)
                        .set(AsyncTaskDO::getStatus, TaskStatus.FAILED.name())
                        .set(AsyncTaskDO::getErrorMsg, truncatedMsg)
                        .set(AsyncTaskDO::getUpdatedAt, LocalDateTime.now()));
        log.error("Async task failed: taskId={}, errorMsg={}", taskId, truncatedMsg);
    }

    @Override
    public TaskStatusVO getStatus(Long taskId) {
        AsyncTaskDO task = asyncTaskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.of(7001, "任务不存在");
        }
        return toStatusVO(task);
    }

    @Override
    public TaskStatusVO getStatusIgnoreTenant(Long taskId) {
        AsyncTaskDO task = asyncTaskMapper.selectByIdIgnoreTenant(taskId);
        if (task == null) {
            throw BusinessException.of(7001, "任务不存在");
        }
        return toStatusVO(task);
    }

    @Override
    public TaskStatusVO getByBizId(Long bizId, String taskType) {
        AsyncTaskDO task = asyncTaskMapper.selectByBizId(bizId, taskType);
        return task == null ? null : toStatusVO(task);
    }

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
                log.warn("Stale RUNNING task marked failed: taskId={}, taskType={}, bizId={}, tenantId={}, updatedAt={}",
                        task.getId(), task.getTaskType(), task.getBizId(), task.getTenantId(), updatedAt);
            }
        }

        log.info("Stale RUNNING task scan finished: checked={}, failed={}, timeoutMinutes={}",
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
