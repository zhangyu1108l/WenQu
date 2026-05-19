package com.kb.app.module.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * async_task 表用于统一管理系统内两类耗时较长、需要前端轮询进度的任务：
 * <ul>
 *     <li>DOC_PROCESS = 文档处理任务，覆盖上传后的 MinIO 存储、文档解析、Embedding、Milvus 入库。</li>
 *     <li>RAGAS_EVAL = 评估任务，覆盖管理员触发的 Ragas 批量评估流程。</li>
 * </ul>
 * <p>
 * 设计上将两类慢任务收敛到同一张表，避免为每条业务链路重复建设进度表；
 * 业务方通过 task_type + biz_id 定位任务，前端通过 taskId 轮询 status、progress、errorMsg。
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
     * 任务主键，数据库自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务类型。
     * <p>
     * 取值范围：
     * <ul>
     *     <li>DOC_PROCESS = 文档处理</li>
     *     <li>RAGAS_EVAL = 评估任务</li>
     * </ul>
     */
    private String taskType;

    /**
     * 关联业务ID。
     * <p>
     * taskType=DOC_PROCESS 时为 document.id；
     * taskType=RAGAS_EVAL 时为 eval_batch.id。
     */
    private Long bizId;

    /**
     * 所属租户ID。
     * <p>
     * async_task 表参与 MyBatis-Plus 多租户拦截，查询时会按 tenant_id 隔离。
     */
    private Long tenantId;

    /**
     * 任务状态。
     * <p>
     * 取值范围：
     * <ul>
     *     <li>PENDING = 待执行</li>
     *     <li>RUNNING = 执行中</li>
     *     <li>DONE = 成功</li>
     *     <li>FAILED = 失败</li>
     * </ul>
     */
    private String status;

    /**
     * 执行进度。
     * <p>
     * 取值范围：0~100，前端用于展示进度条。
     */
    private Integer progress;

    /**
     * 失败原因。
     * <p>
     * status=FAILED 时记录错误摘要；任务成功时为 null。
     */
    private String errorMsg;

    /**
     * 创建时间。
     * <p>
     * 新建任务时自动填充，对应 async_task.created_at。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     * <p>
     * 使用 {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 自动填充，
     * 每次任务状态、进度或错误信息变更时刷新；前端可结合该字段判断任务是否长时间无进展。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
