package com.kb.app.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.auth.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper — 对应数据库 user 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * user 表包含 tenant_id 字段，所有查询会被 {@code TenantLineInnerInterceptor}
 * 自动追加 AND tenant_id = ?，确保租户间数据隔离，业务层无需手动传入 tenantId。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>注册：insert 新用户</li>
 *     <li>登录：根据 username 查询用户（拦截器自动加 tenant_id 条件）</li>
 *     <li>管理员：查询/修改本租户用户列表和角色</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    // MyBatis-Plus BaseMapper 已提供：
    //   insert / deleteById / updateById / selectById
    //   selectList / selectPage / selectCount 等常用方法
    // 如需自定义 SQL，在此接口中追加方法并配合 @Select 或 XML
}
