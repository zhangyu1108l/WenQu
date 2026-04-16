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
 * 评估用例实体类 — 对应数据库 eval_case 表。
 * <p>
 * 管理员手动录入，每条用例包含一个问题和对应的标准答案。
 * 标准答案（groundTruth）是 Ragas 计算 context_recall 等指标的依据。
 * <p>
 * eval_case 表包含 tenant_id 字段，所有查询会被租户拦截器自动追加租户隔离条件。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("eval_case")
public class EvalCaseDO {

    /**
     * 用例主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户ID，由租户拦截器自动注入查询条件
     */
    private Long tenantId;

    /**
     * 评估问题，如：员工年假天数如何计算？
     */
    private String question;

    /**
     * 标准答案，由管理员手动编写，作为评估基准
     */
    private String groundTruth;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
