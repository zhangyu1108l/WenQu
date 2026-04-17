package com.kb.app.module.auth.service;

import com.kb.app.module.auth.dto.LoginRequest;
import com.kb.app.module.auth.dto.LoginResponse;
import com.kb.app.module.auth.dto.RegisterRequest;

/**
 * 用户认证 Service 接口 — 定义注册、登录、登出、刷新四个核心操作。
 * <p>
 * 所有认证相关的业务逻辑封装在此接口中，Controller 层只做参数校验和调用转发。
 *
 * @author kb-system
 */
public interface AuthService {

    /**
     * 用户注册。
     * <p>
     * 根据 tenantCode 查找租户 → 检查用户名是否重复 → BCrypt 加密密码 → 插入用户记录
     * → 签发 accessToken 和 refreshToken。
     *
     * @param request 注册请求（tenantCode + username + password）
     * @return 登录响应（包含 Token 和用户信息）
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 用户登录。
     * <p>
     * 根据 tenantCode 查找租户 → 校验用户名和密码 → 签发 accessToken 和 refreshToken。
     *
     * @param request 登录请求（tenantCode + username + password）
     * @return 登录响应（包含 Token 和用户信息）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出。
     * <p>
     * 将 accessToken 加入 Redis 黑名单，使其在剩余有效期内不可用。
     *
     * @param accessToken 当前用户的 accessToken
     */
    void logout(String accessToken);

    /**
     * 刷新 Token。
     * <p>
     * 解析 refreshToken → 重新查询用户最新信息 → 签发新的 accessToken。
     * refreshToken 仅用于换取 accessToken，不能用于业务接口鉴权。
     *
     * @param refreshToken 用户的 refreshToken
     * @return 登录响应（包含新的 accessToken，refreshToken 不变）
     */
    LoginResponse refresh(String refreshToken);
}
