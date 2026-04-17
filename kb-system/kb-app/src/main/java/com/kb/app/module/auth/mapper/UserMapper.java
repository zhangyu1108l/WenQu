package com.kb.app.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.auth.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 根据租户ID和用户名查询用户。
     * <p>
     * <b>为什么必须同时传 tenantId：</b>
     * 不同租户下可能存在同名用户（如租户A和租户B都有 username=admin），
     * 如果只按 username 查询，会跨租户查到其他租户的用户，违反租户隔离原则。
     * 因此必须同时带 tenant_id + username 两个条件，确保查询结果严格限制在当前租户内。
     * <p>
     * 虽然 MyBatis-Plus 租户拦截器会自动追加 tenant_id 条件，
     * 但此处显式写入 SQL 使意图更清晰，也便于代码审查时确认租户隔离逻辑。
     * <p>
     * 主要使用场景：
     * <ul>
     *     <li>登录：根据 tenantCode 解析出 tenantId，再查用户</li>
     *     <li>注册：检查同租户下用户名是否已存在</li>
     * </ul>
     *
     * @param tenantId 租户ID，不允许为 null
     * @param username 用户名，不允许为 null
     * @return 匹配的用户实体，不存在时返回 null
     */
    @Select("SELECT * FROM user WHERE tenant_id = #{tenantId} AND username = #{username} LIMIT 1")
    UserDO selectByTenantIdAndUsername(@Param("tenantId") Long tenantId,
                                      @Param("username") String username);
}
