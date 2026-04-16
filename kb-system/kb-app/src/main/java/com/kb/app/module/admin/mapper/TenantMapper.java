package com.kb.app.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.admin.entity.TenantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户表 Mapper — 对应数据库 tenant 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力，
 * 无需手写 XML 映射文件。
 * <p>
 * 注意：tenant 表是租户元数据表，不包含 tenant_id 字段，
 * 必须在 {@code TenantLineInnerInterceptor} 中将此表加入忽略列表，
 * 否则拦截器会错误地追加 AND tenant_id = ? 导致查询失败。
 *
 * @author kb-system
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantDO> {
    // MyBatis-Plus BaseMapper 已提供：
    //   insert / deleteById / updateById / selectById
    //   selectList / selectPage / selectCount 等常用方法
    // 如需自定义 SQL，在此接口中追加方法并配合 @Select 或 XML
}
