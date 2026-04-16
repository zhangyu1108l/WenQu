package com.kb.app.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 租户实体类 — 对应数据库 tenant 表。
 * <p>
 * 每个租户代表一个独立的公司或组织，租户之间数据完全隔离：
 * <ul>
 *     <li>数据库层：MyBatis-Plus TenantLineInnerInterceptor 自动追加 AND tenant_id = ?</li>
 *     <li>向量库层：每租户独立 Collection（tenant_{tenantId}_docs）</li>
 *     <li>文件存储层：每租户独立 Bucket（tenant-{tenantId}）</li>
 * </ul>
 * <p>
 * 注意：tenant 表本身不受租户拦截器影响（它是租户的元数据表），
 * 需要在拦截器配置中将此表排除。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tenant")
public class TenantDO {

    /**
     * 租户主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户名称，如：阿里巴巴
     */
    private String name;

    /**
     * 租户唯一标识（英文），如：alibaba
     * 用于用户注册/登录时指定所属租户
     */
    private String code;

    /**
     * 状态：1=启用  0=禁用
     * 禁用后该租户下所有用户无法登录
     */
    private Integer status;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
