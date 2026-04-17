package com.kb.app.module.auth.controller;

import com.kb.app.module.auth.dto.LoginRequest;
import com.kb.app.module.auth.dto.LoginResponse;
import com.kb.app.module.auth.dto.RegisterRequest;
import com.kb.app.module.auth.service.AuthService;
import com.kb.common.dto.Result;
import com.kb.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证 Controller — 处理注册、登录、登出、刷新四个认证接口。
 * <p>
 * 接口路径统一前缀 /api/auth，严格按照架构文档定义，不可新增或修改路径。
 * <p>
 * 包含的接口：
 * <ul>
 *     <li>POST /api/auth/register — 用户注册（公开，无需鉴权）</li>
 *     <li>POST /api/auth/login    — 用户登录（公开，无需鉴权）</li>
 *     <li>POST /api/auth/logout   — 用户登出（需登录，accessToken 加入 Redis 黑名单）</li>
 *     <li>POST /api/auth/refresh  — 刷新 Token（需登录，用 refreshToken 换新 accessToken）</li>
 * </ul>
 * <p>
 * Controller 层只做参数校验和调用转发，不包含任何业务逻辑。
 *
 * @author kb-system
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册。
     * <p>
     * 接口：POST /api/auth/register
     * 权限：公开接口，无需鉴权
     * 请求体：{tenantCode, username, password}
     *
     * @param request 注册请求（经 @Valid 校验 tenantCode/username/password 非空及长度）
     * @return 包含 accessToken、refreshToken 和用户信息的统一响应
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return Result.ok(response);
    }

    /**
     * 用户登录。
     * <p>
     * 接口：POST /api/auth/login
     * 权限：公开接口，无需鉴权
     * 请求体：{tenantCode, username, password}
     *
     * @param request 登录请求（经 @Valid 校验 tenantCode/username/password 非空）
     * @return 包含 accessToken、refreshToken 和用户信息的统一响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.ok(response);
    }

    /**
     * 用户登出 — 将 accessToken 加入 Redis 黑名单。
     * <p>
     * 接口：POST /api/auth/logout
     * 权限：登录用户
     * <p>
     * <b>Bearer Token 解析方式：</b>
     * 从 HTTP 请求头 Authorization 中提取 Token，格式为 "Bearer {token}"。
     * 先检查 Header 是否存在且以 "Bearer " 开头（注意空格），
     * 然后截取第 7 个字符之后的部分即为纯 Token 字符串。
     * 如果 Header 不存在或格式不正确，抛出业务异常。
     *
     * @param request HTTP 请求，用于读取 Authorization 头
     * @return 统一响应，data 为 null
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        authService.logout(token);
        return Result.ok();
    }

    /**
     * 刷新 Token — 用 refreshToken 换取新的 accessToken。
     * <p>
     * 接口：POST /api/auth/refresh
     * 权限：登录用户
     * <p>
     * 前端将 refreshToken 放入 Authorization: Bearer {refreshToken} 头中发送。
     * 此处的 Token 是 refreshToken（不是 accessToken），
     * 服务端解析后签发新的 accessToken。
     *
     * @param request HTTP 请求，用于读取 Authorization 头中的 refreshToken
     * @return 包含新 accessToken 的统一响应（refreshToken 保持不变）
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(HttpServletRequest request) {
        String refreshToken = resolveToken(request);
        LoginResponse response = authService.refresh(refreshToken);
        return Result.ok(response);
    }

    /**
     * 从 HTTP 请求头中解析 Bearer Token。
     * <p>
     * Authorization 头格式：Bearer eyJhbGciOiJIUzI1NiJ9...
     * 截取 "Bearer " 之后的部分（第 7 个字符起）即为纯 Token 字符串。
     *
     * @param request HTTP 请求
     * @return 纯 Token 字符串（不含 "Bearer " 前缀）
     * @throws BusinessException 如果 Authorization 头不存在或格式不正确
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BusinessException.of(1008, "缺少有效的 Authorization 头");
        }
        return header.substring(7);
    }
}
