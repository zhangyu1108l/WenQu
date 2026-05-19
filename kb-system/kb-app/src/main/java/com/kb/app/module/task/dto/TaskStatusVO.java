package com.kb.app.module.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务状态查询响应 VO。
 * <p>
 * 前端轮询 GET /api/tasks/{taskId}/status 时使用这些字段展示任务类型、当前状态、
 * 进度条和失败原因；updatedAt 可用于判断任务是否卡住：
 * 如果 status=RUNNING 且超过 30 分钟未更新，可认为任务异常，需要提示管理员处理。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusVO {

    /**
     * 任务ID，对应 async_task.id。
     */
    private Long taskId;

    /**
     * 任务类型：DOC_PROCESS=文档处理，RAGAS_EVAL=评估任务。
     */
    private String taskType;

    /**
     * 当前状态：PENDING=待执行，RUNNING=执行中，DONE=成功，FAILED=失败。
     */
    private String status;

    /**
     * 任务进度，取值范围 0~100，前端用于展示进度条。
     */
    private int progress;

    /**
     * 失败原因。
     * <p>
     * status=FAILED 时返回错误摘要；任务成功或仍在执行时通常为 null。
     */
    private String errorMsg;

    /**
     * 创建时间，用于展示任务触发时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     * <p>
     * 前端轮询时可用该字段判断任务是否卡住：
     * RUNNING 状态超过 30 分钟未更新，可认为异常。
     */
    private LocalDateTime updatedAt;
}
