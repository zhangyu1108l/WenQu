package com.kb.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 鉴权配置属性类 — 从 application.yml 中读取 jwt.* 前缀的配置。
 * <p>
 * 配置项说明：
 * <ul>
 *     <li><b>secret</b> — JWT 签名密钥（至少 256 位），必须从环境变量 JWT_SECRET 注入，禁止硬编码</li>
 *     <li><b>accessTokenExpire</b> — accessToken 有效期（毫秒），默认 3600000（1 小时）</li>
 *     <li><b>refreshTokenExpire</b> — refreshToken 有效期（毫秒），默认 604800000（7 天）</li>
 * </ul>
 * <p>
 * 对应 yml 配置：
 * <pre>
 * jwt:
 *   secret: ${JWT_SECRET:}
 *   access-token-expire: 3600000
 *   refresh-token-expire: 604800000
 * </pre>
 *
 * @author kb-system
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥（HMAC-SHA256），必须从环境变量 JWT_SECRET 读取，不允许硬编码。
     * 长度至少 32 字符（256 位），否则 jjwt 会拒绝签名。
     */
    private String secret;

    /**
     * accessToken 有效期（毫秒）。
     * 默认 3600000（1 小时），用于普通接口鉴权。
     */
    private long accessTokenExpire;

    /**
     * refreshToken 有效期（毫秒）。
     * 默认 604800000（7 天），用于在 accessToken 过期后无感刷新。
     */
    private long refreshTokenExpire;
}
