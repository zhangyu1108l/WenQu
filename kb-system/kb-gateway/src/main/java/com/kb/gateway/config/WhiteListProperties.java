package com.kb.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 白名单路径配置 — 从 application.yml 读取 gateway.white-list 前缀的配置。
 * <p>
 * 白名单中的路径不需要 JWT 鉴权，JwtAuthFilter 会直接放行。
 * <p>
 * 默认白名单包含注册、登录和刷新三个公开接口：
 * <ul>
 *     <li>/api/auth/register — 用户注册</li>
 *     <li>/api/auth/login — 用户登录</li>
 *     <li>/api/auth/refresh — 刷新 Token（refreshToken 只含 userId，无 tenantId/role，
 *         无法通过 Gateway 的 JWT 完整校验，因此放入白名单，由 kb-app 内部自行校验）</li>
 * </ul>
 * <p>
 * 对应 yml 配置示例：
 * <pre>
 * gateway:
 *   white-list:
 *     paths:
 *       - /api/auth/register
 *       - /api/auth/login
 * </pre>
 *
 * @author kb-system
 */
@Data
@ConfigurationProperties(prefix = "gateway.white-list")
public class WhiteListProperties {

    /**
     * 不需要鉴权的路径列表。
     * <p>
     * 匹配规则：精确匹配请求路径（不支持通配符），
     * JwtAuthFilter 逐一比较，命中则直接放行。
     */
    private List<String> paths = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh"
    );
}
