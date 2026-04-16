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
 * 评估结果明细实体类 — 对应数据库 eval_result 表。
 * <p>
 * 每个用例在每次批次中对应一条明细记录。
 * modelAnswer 是 RAG 系统对该用例问题给出的实际回答。
 * <p>
 * 四个指标字段由 Ragas 对该用例计算得出：
 * <ul>
 *     <li>faithfulness      = 忠实度（0~1）</li>
 *     <li>answerRelevancy   = 答案相关性（0~1）</li>
 *     <li>contextRecall     = 上下文召回率（0~1）</li>
 *     <li>contextPrecision  = 上下文精准度（0~1）</li>
 * </ul>
 * <p>
 * 注意：eval_result 表不包含 tenant_id 字段，
 * 需要在租户拦截器中将此表加入忽略列表。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("eval_result")
public class EvalResultDO {

    /**
     * 结果主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属批次ID，关联 eval_batch.id
     */
    private Long batchId;

    /**
     * 对应用例ID，关联 eval_case.id
     */
    private Long evalCaseId;

    /**
     * RAG 系统对该问题的实际回答
     */
    private String modelAnswer;

    /**
     * 忠实度得分：0~1，衡量回答是否忠实于检索内容
     */
    private Float faithfulness;

    /**
     * 答案相关性得分：0~1，衡量回答是否切题
     */
    private Float answerRelevancy;

    /**
     * 上下文召回率得分：0~1，衡量标准答案是否被检索覆盖
     */
    private Float contextRecall;

    /**
     * 上下文精准度得分：0~1，衡量检索内容是否都有用
     */
    private Float contextPrecision;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
