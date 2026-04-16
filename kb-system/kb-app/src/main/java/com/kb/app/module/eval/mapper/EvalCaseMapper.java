package com.kb.app.module.eval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.eval.entity.EvalCaseDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评估用例表 Mapper — 对应数据库 eval_case 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * eval_case 表包含 tenant_id 字段，所有查询会被 {@code TenantLineInnerInterceptor}
 * 自动追加 AND tenant_id = ?，确保租户间数据隔离。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>管理员录入：insert 新的评估用例（问题 + 标准答案）</li>
 *     <li>用例列表：按租户分页查询所有用例</li>
 *     <li>运行评估：查询所有用例作为 Ragas 评估输入</li>
 *     <li>删除用例：按 id 删除单条用例</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface EvalCaseMapper extends BaseMapper<EvalCaseDO> {
}
