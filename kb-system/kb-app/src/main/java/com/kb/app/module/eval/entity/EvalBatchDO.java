package com.kb.app.module.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评估批次实体类 — 对应数据库 eval_batch 表。
 * <p>
 * 每次运行评估创建一条批次记录。
 * 四个 avg_* 字段存储本次批次所有用例的指标均值，用于管理员面板展示趋势。
 * <p>
 * 指标说明：
 * <ul>
 *     <li>faithfulness      = 忠实度：AI 回答是否忠实于检索内容（0~1）</li>
 *     <li>answer_relevancy  = 答案相关性：AI 回答是否切题（0~1）</li>
 *     <li>context_recall    = 上下文召回率：ground truth 是否被检索覆盖（0~1）</li>
 *     <li>context_precision = 上下文精准度：检索内容是否都有用（0~1）</li>
 * </ul>
 * <p>
 * eval_batch 表包含 tenant_id 字段，所有查询会被租户拦截器自动追加租户隔离条件。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("eval_batch")
public class EvalBatchDO {

    /**
     * 批次主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户ID，由租户拦截器自动注入查询条件
     */
    private Long tenantId;

    /**
     * 本次参与评估的用例数量
     */
    private Integer caseCount;

    /**
     * 批次状态：PENDING / RUNNING / DONE / FAILED
     */
    private String status;

    /**
     * 本批次忠实度均值，评估完成后写入
     */
    private Float avgFaithfulness;

    /**
     * 本批次答案相关性均值
     */
    private Float avgAnswerRelevancy;

    /**
     * 本批次上下文召回率均值
     */
    private Float avgContextRecall;

    /**
     * 本批次上下文精准度均值
     */
    private Float avgContextPrecision;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
