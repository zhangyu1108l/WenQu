package com.kb.app.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 异步任务实体类 — 对应数据库 async_task 表。
 * <p>
 * 统一管理两类慢任务：
 * <ul>
 *     <li>DOC_PROCESS = 文档处理（解析 → Embedding → 入库）</li>
 *     <li>RAGAS_EVAL  = Ragas 质量评估</li>
 * </ul>
 * <p>
 * bizId 关联业务主键：
 * <ul>
 *     <li>task_type=DOC_PROCESS 时，biz_id = document.id</li>
 *     <li>task_type=RAGAS_EVAL  时，biz_id = eval_batch.id</li>
 * </ul>
 * <p>
 * progress 进度说明（文档处理各阶段）：
 * <ul>
 *     <li>存 MinIO = 30</li>
 *     <li>解析 = 60</li>
 *     <li>Embedding = 90</li>
 *     <li>完成 = 100</li>
 * </ul>
 * <p>
 * status 状态机：PENDING → RUNNING → DONE / FAILED
 * <p>
 * async_task 表包含 tenant_id 字段，所有查询会被租户拦截器自动追加租户隔离条件。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("async_task")
public class AsyncTaskDO {

    /**
     * 任务主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务类型：DOC_PROCESS=文档处理  RAGAS_EVAL=评估任务
     */
    private String taskType;

    /**
     * 关联业务ID：文档处理时为 document.id，评估时为 eval_batch.id
     */
    private Long bizId;

    /**
     * 所属租户ID，由租户拦截器自动注入查询条件
     */
    private Long tenantId;

    /**
     * 任务状态：PENDING=待执行  RUNNING=执行中  DONE=成功  FAILED=失败
     */
    private String status;

    /**
     * 执行进度：0~100，前端用于展示进度条
     */
    private Integer progress;

    /**
     * 失败时的错误信息，成功时为 NULL
     */
    private String errorMsg;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间，每次状态变更自动刷新
     */
    private LocalDateTime updatedAt;
}
