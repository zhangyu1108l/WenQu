package com.kb.app.module.eval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.eval.entity.EvalBatchDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评估批次表 Mapper — 对应数据库 eval_batch 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * eval_batch 表包含 tenant_id 字段，所有查询会被 {@code TenantLineInnerInterceptor}
 * 自动追加 AND tenant_id = ?，确保租户间数据隔离。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>运行评估：insert 一条 PENDING 状态的批次记录</li>
 *     <li>批次列表：按租户查询历史批次（展示趋势）</li>
 *     <li>评估完成：更新 status=DONE + 四个 avg_* 均值字段</li>
 *     <li>批次详情：按 id 查询单条批次（关联 eval_result 明细）</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface EvalBatchMapper extends BaseMapper<EvalBatchDO> {
}
