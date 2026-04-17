package com.kb.app.util;

import com.kb.app.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 — 负责 token 的签发、解析和校验。
 * <p>
 * <b>两种 Token 的区别和使用场景：</b>
 * <ul>
 *     <li><b>accessToken</b> — 有效期 1 小时，用于所有业务接口的鉴权。
 *         Payload 中包含 userId、tenantId、role 三个自定义字段，
 *         Gateway 解析后注入到请求头（X-User-Id / X-Tenant-Id），业务层直接使用。</li>
 *     <li><b>refreshToken</b> — 有效期 7 天，仅用于 POST /api/auth/refresh 接口。
 *         Payload 中只包含 userId，不含 tenantId 和 role（刷新时从数据库重新查询最新值），
 *         避免角色变更后旧 token 携带过期角色信息。</li>
 * </ul>
 * <p>
 * <b>JWT Payload 字段说明：</b>
 * <ul>
 *     <li><b>sub</b> — 标准字段，存放 userId（字符串形式）</li>
 *     <li><b>tenantId</b> — 自定义字段，租户ID（仅 accessToken）</li>
 *     <li><b>role</b> — 自定义字段，用户角色枚举值（仅 accessToken）：
 *         0=SUPER_ADMIN / 1=TENANT_ADMIN / 2=USER</li>
 *     <li><b>iat</b> — 标准字段，签发时间</li>
 *     <li><b>exp</b> — 标准字段，过期时间</li>
 * </ul>
 *
 * @author kb-system
 */
@Slf4j
@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 使用 HMAC-SHA256 算法，从配置的 secret 字符串生成签名密钥
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 accessToken（业务接口鉴权用）。
     * <p>
     * Payload 包含 userId（sub）、tenantId、role 三个字段，
     * 有效期由 jwt.access-token-expire 配置（默认 1 小时）。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param role     用户角色（0=SUPER_ADMIN / 1=TENANT_ADMIN / 2=USER）
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(Long userId, Long tenantId, Integer role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpire());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 生成 refreshToken（刷新 accessToken 用）。
     * <p>
     * Payload 只包含 userId（sub），不含 tenantId 和 role，
     * 刷新时从数据库重新查询最新角色信息，避免角色变更后 token 携带旧值。
     * 有效期由 jwt.refresh-token-expire 配置（默认 7 天）。
     *
     * @param userId 用户ID
     * @return 签名后的 JWT 字符串
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpire());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析 token，返回 Claims（JWT 载荷）。
     * <p>
     * 如果 token 无效（签名错误、格式错误等）会抛出 JwtException；
     * 如果 token 已过期会抛出 ExpiredJwtException。
     * 调用方应使用 try-catch 处理异常。
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims 对象，包含所有 Payload 字段
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 token 中提取 userId。
     * <p>
     * userId 存储在 JWT 标准字段 sub 中，accessToken 和 refreshToken 都包含此字段。
     *
     * @param token JWT 字符串
     * @return 用户ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * 从 token 中提取 tenantId。
     * <p>
     * tenantId 是自定义 Claim，仅 accessToken 包含此字段。
     * 对 refreshToken 调用此方法会返回 null。
     *
     * @param token JWT 字符串（应为 accessToken）
     * @return 租户ID，refreshToken 返回 null
     */
    public Long getTenantId(String token) {
        Object tenantId = parseToken(token).get("tenantId");
        return tenantId != null ? Long.valueOf(tenantId.toString()) : null;
    }

    /**
     * 从 token 中提取用户角色。
     * <p>
     * role 是自定义 Claim，仅 accessToken 包含此字段。
     * 值为整数：0=SUPER_ADMIN / 1=TENANT_ADMIN / 2=USER。
     *
     * @param token JWT 字符串（应为 accessToken）
     * @return 角色值，refreshToken 返回 null
     */
    public Integer getRole(String token) {
        Object role = parseToken(token).get("role");
        return role != null ? Integer.valueOf(role.toString()) : null;
    }

    /**
     * 判断 token 是否已过期。
     * <p>
     * <b>注意：</b>jjwt 在解析过期 token 时会直接抛出 {@link ExpiredJwtException}，
     * 而不是返回一个标记。因此此方法通过 try-catch 捕获该异常来判断过期状态：
     * <ul>
     *     <li>捕获到 ExpiredJwtException → 已过期，返回 true</li>
     *     <li>解析成功 → 未过期，返回 false</li>
     *     <li>捕获到其他异常（签名错误等）→ 视为无效 token，也返回 true</li>
     * </ul>
     *
     * @param token JWT 字符串
     * @return true=已过期或无效，false=未过期且有效
     */
    public boolean isExpired(String token) {
        try {
            parseToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            // token 已过期，jjwt 解析时直接抛出此异常
            return true;
        } catch (Exception e) {
            // 签名错误、格式错误等其他异常，视为无效 token
            log.warn("JWT 解析失败: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 获取 token 的剩余有效秒数。
     * <p>
     * 用于登出时将 accessToken 加入 Redis 黑名单，黑名单的 TTL 设置为 token 的剩余有效期，
     * 这样 token 自然过期后黑名单记录也会自动清除，避免 Redis 中堆积无用数据。
     * <p>
     * <b>注意：</b>如果 token 已过期，jjwt 会抛出 {@link ExpiredJwtException}，
     * 但该异常对象中仍然包含 Claims 信息，可以从中提取过期时间。
     * 已过期的 token 返回 0。
     *
     * @param token JWT 字符串
     * @return 剩余有效秒数，已过期返回 0
     */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parseToken(token);
            long expMillis = claims.getExpiration().getTime();
            long remaining = (expMillis - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (ExpiredJwtException e) {
            // token 已过期，从异常中提取 Claims 计算（通常返回 0）
            Claims claims = e.getClaims();
            long expMillis = claims.getExpiration().getTime();
            long remaining = (expMillis - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            log.warn("JWT 解析失败，无法获取剩余有效期: {}", e.getMessage());
            return 0;
        }
    }
}
