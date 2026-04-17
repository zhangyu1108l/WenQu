package com.kb.app.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO — 对应 POST /api/auth/register 的请求体。
 * <p>
 * 注册时用户需提供租户标识（tenantCode）、用户名和密码。
 * 系统根据 tenantCode 查找对应租户，然后在该租户下创建用户。
 * <p>
 * 新注册用户的默认角色为 2（USER，普通用户），
 * 租户管理员可以后续通过 PUT /api/admin/users/{id}/role 修改角色。
 *
 * @author kb-system
 */
@Data
public class RegisterRequest {

    /**
     * 租户唯一标识（英文），如：alibaba。
     * 用户注册时必须指定所属租户，系统根据此字段查找 tenant 表。
     */
    @NotBlank(message = "租户标识不能为空")
    private String tenantCode;

    /**
     * 用户名，租户内唯一，长度 2~20 个字符。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在2~20个字符之间")
    private String username;

    /**
     * 密码（明文），长度 6~20 个字符。
     * 后端接收后使用 BCrypt 加密存储，禁止明文入库。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6~20个字符之间")
    private String password;
}
