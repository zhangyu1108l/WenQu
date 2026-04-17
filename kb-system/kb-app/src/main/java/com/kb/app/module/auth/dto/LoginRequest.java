package com.kb.app.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO — 对应 POST /api/auth/login 的请求体。
 * <p>
 * 登录时用户需提供租户标识、用户名和密码。
 * 系统先根据 tenantCode 查找租户，再在该租户内校验用户名和密码。
 *
 * @author kb-system
 */
@Data
public class LoginRequest {

    /**
     * 租户唯一标识（英文），如：alibaba。
     * 用于定位用户所属租户，不同租户下可以有同名用户。
     */
    @NotBlank(message = "租户标识不能为空")
    private String tenantCode;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（明文），后端使用 BCrypt 校验。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
