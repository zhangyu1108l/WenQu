package com.kb.gateway.filter;

import com.kb.gateway.config.WhiteListProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Gateway JWT 鉴权全局过滤器 — 所有请求的统一入口鉴权。
 * <p>
 * <b>为什么在 Gateway 层做鉴权而不在业务层做？</b>
 * <ol>
 *     <li><b>统一拦截</b> — 所有外部请求必经 Gateway，在此统一校验 JWT，
 *         下游业务服务（kb-app）无需重复处理鉴权逻辑，职责清晰。</li>
 *     <li><b>安全边界</b> — Gateway 是系统唯一对外暴露的入口，
 *         非法请求在网关层就被拦截，不会到达业务服务，减少攻击面。</li>
 *     <li><b>信息注入</b> — Gateway 解析 JWT 后将 userId / tenantId / role
 *         写入请求头传递给下游，业务层直接读取，避免每个服务重复解析 JWT。</li>
 * </ol>
 * <p>
 * 过滤器处理流程（7 步）：
 * <pre>
 * ① 白名单放行 → ② 提取 Bearer Token → ③ Redis 黑名单检查
 * → ④ 解析 JWT → ⑤ 提取用户信息 → ⑥ 写入请求头 → ⑦ 放行到下游
 * </pre>
 *
 * @author kb-system
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final WhiteListProperties whiteListProperties;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final SecretKey signingKey;

    /** JWT 黑名单 Redis Key 前缀，与 AuthServiceImpl 中保持一致 */
    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    public JwtAuthFilter(WhiteListProperties whiteListProperties,
                         ReactiveStringRedisTemplate reactiveRedisTemplate,
                         @Value("${jwt.secret}") String jwtSecret) {
        this.whiteListProperties = whiteListProperties;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        // 使用与 kb-app JwtUtil 相同的算法（HMAC-SHA256）和密钥
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 过滤器执行优先级。
     * <p>
     * 返回最小值确保 JWT 鉴权过滤器在所有自定义过滤器中最先执行，
     * 保证未认证的请求不会进入后续过滤器处理链。
     *
     * @return 优先级顺序值，越小越先执行
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 核心过滤逻辑 — 7 步完成 JWT 鉴权和请求头注入。
     *
     * @param exchange 当前请求上下文
     * @param chain    过滤器链
     * @return Mono<Void> 响应式结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ① 判断当前请求路径是否在白名单，在则直接放行（不校验 JWT）
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // ② 从请求头 Authorization 取 Bearer Token，没有则返回 401
        String token = resolveToken(request);
        if (token == null) {
            return unauthorizedResponse(exchange, "缺少有效的 Authorization 头");
        }

        // ③ 检查 token 是否在 Redis 黑名单（用户登出后 token 被加入黑名单）
        // 使用响应式 Redis 查询，避免阻塞 Netty 事件循环线程
        return reactiveRedisTemplate.hasKey(JWT_BLACKLIST_PREFIX + token)
                .flatMap(inBlacklist -> {
                    if (Boolean.TRUE.equals(inBlacklist)) {
                        // token 在黑名单中（用户已登出），返回 401
                        return unauthorizedResponse(exchange, "token 已失效（用户已登出）");
                    }

                    // ④ 解析 token，过期或签名非法则返回 401
                    Claims claims;
                    try {
                        claims = Jwts.parser()
                                .verifyWith(signingKey)
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();
                    } catch (ExpiredJwtException e) {
                        return unauthorizedResponse(exchange, "token 已过期");
                    } catch (Exception e) {
                        log.warn("JWT 解析失败: {}", e.getMessage());
                        return unauthorizedResponse(exchange, "无效的 token");
                    }

                    // ⑤ 从 token 中取出 userId / tenantId / role
                    String userId = claims.getSubject();
                    Object tenantIdObj = claims.get("tenantId");
                    Object roleObj = claims.get("role");

                    if (userId == null || tenantIdObj == null || roleObj == null) {
                        return unauthorizedResponse(exchange, "token 缺少必要字段");
                    }

                    String tenantId = tenantIdObj.toString();
                    String role = roleObj.toString();

                    // ⑥ 将 userId / tenantId / role 写入请求头，传递给下游服务
                    // X-Tenant-Id 的作用：下游 kb-app 的 Servlet Filter 读取此值，
                    // 注入 TenantContext（ThreadLocal），供 MyBatis-Plus 租户拦截器
                    // 自动追加 AND tenant_id = ? 条件，实现数据库层的多租户隔离。
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-User-Id", userId)
                            .header("X-Tenant-Id", tenantId)
                            .header("X-User-Role", role)
                            .build();

                    // ⑦ 放行请求到下游服务（携带注入的请求头）
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    /**
     * 判断请求路径是否在白名单中。
     *
     * @param path 当前请求路径
     * @return true=在白名单中（免鉴权），false=需要鉴权
     */
    private boolean isWhiteListed(String path) {
        return whiteListProperties.getPaths().stream()
                .anyMatch(path::equals);
    }

    /**
     * 从请求头中解析 Bearer Token。
     * <p>
     * Authorization 头格式：Bearer eyJhbGciOiJIUzI1NiJ9...
     * 截取 "Bearer " 之后的部分（第 7 个字符起）即为纯 Token 字符串。
     *
     * @param request HTTP 请求
     * @return 纯 Token 字符串，Header 不存在或格式不正确时返回 null
     */
    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * 构建 401 未认证响应。
     * <p>
     * <b>为什么返回 401 而不是 403？</b>
     * <ul>
     *     <li>HTTP 401 Unauthorized = 未认证：客户端未提供有效凭证（token 缺失/过期/无效），
     *         需要重新登录获取合法 token。</li>
     *     <li>HTTP 403 Forbidden = 无权限：客户端已认证（token 有效），
     *         但当前角色没有访问该资源的权限。</li>
     *     <li>此过滤器处理的场景都是 token 无效的情况，属于"未认证"，因此使用 401。</li>
     * </ul>
     * <p>
     * 响应体使用统一格式 {"code":401,"msg":"...","data":null}，
     * 与 GlobalExceptionHandler 保持一致。
     *
     * @param exchange 请求上下文
     * @param message  错误描述
     * @return Mono<Void> 响应式结果
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":401,\"msg\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
