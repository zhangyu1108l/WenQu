package com.kb.app.module.eval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.eval.entity.EvalResultDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评估结果明细表 Mapper — 对应数据库 eval_result 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * 注意：eval_result 表不包含 tenant_id 字段，
 * 必须在 {@code TenantLineInnerInterceptor} 中将此表加入忽略列表，
 * 否则拦截器会错误地追加 AND tenant_id = ? 导致查询失败。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>评估执行：Ragas 返回结果后批量 insert 每条用例的明细</li>
 *     <li>批次详情：按 batch_id 查询所有明细，供管理员逐条查看</li>
 *     <li>指标聚合：计算四个指标的均值，回写到 eval_batch 表</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface EvalResultMapper extends BaseMapper<EvalResultDO> {
}
