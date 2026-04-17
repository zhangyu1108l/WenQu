package com.kb.app.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册响应 DTO — 对应 POST /api/auth/login 和 POST /api/auth/register 的响应体。
 * <p>
 * 包含两个 Token 和用户基本信息，前端用于：
 * <ul>
 *     <li>accessToken — 存储到 localStorage/内存，后续请求带 Authorization: Bearer {accessToken}</li>
 *     <li>refreshToken — 存储到安全位置，accessToken 过期时调 /api/auth/refresh 换新</li>
 *     <li>userId / username / role / tenantId — 前端展示和权限判断</li>
 * </ul>
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 访问令牌，有效期 1 小时，用于所有业务接口鉴权
     */
    private String accessToken;

    /**
     * 刷新令牌，有效期 7 天，仅用于 POST /api/auth/refresh 换取新的 accessToken
     */
    private String refreshToken;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色：0=SUPER_ADMIN / 1=TENANT_ADMIN / 2=USER
     */
    private Integer role;

    /**
     * 租户ID
     */
    private Long tenantId;
}
