package com.kb.app.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类 — 对应数据库 user 表。
 * <p>
 * 用户必须归属于某个租户，不同租户下可以有同名用户（通过 uk_tenant_username 唯一约束保证）。
 * <p>
 * 角色说明（枚举值不可扩展）：
 * <ul>
 *     <li>0 = SUPER_ADMIN — 超级管理员，平台级，管理所有租户</li>
 *     <li>1 = TENANT_ADMIN — 租户管理员，管理本租户的用户和文档</li>
 *     <li>2 = USER — 普通用户，只能在本租户内进行对话问答</li>
 * </ul>
 * <p>
 * 密码存储规则：必须使用 BCrypt 加密，禁止明文存储。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class UserDO {

    /**
     * 用户主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户ID，关联 tenant.id
     * 由 MyBatis-Plus 租户拦截器自动注入查询条件
     */
    private Long tenantId;

    /**
     * 用户名（租户内唯一）
     */
    private String username;

    /**
     * 密码哈希值，使用 BCrypt 加密，禁止存明文
     */
    private String passwordHash;

    /**
     * 角色：0=超级管理员  1=租户管理员  2=普通用户
     * 对应枚举 UserRole，数据库存 TINYINT
     */
    private Integer role;

    /**
     * 状态：1=启用  0=禁用
     * 禁用后该用户无法登录
     */
    private Integer status;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
