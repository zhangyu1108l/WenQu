package com.kb.app.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.app.context.TenantContext;
import com.kb.app.module.admin.entity.TenantDO;
import com.kb.app.module.admin.mapper.TenantMapper;
import com.kb.app.module.auth.dto.LoginRequest;
import com.kb.app.module.auth.dto.LoginResponse;
import com.kb.app.module.auth.dto.RegisterRequest;
import com.kb.app.module.auth.entity.UserDO;
import com.kb.app.module.auth.mapper.UserMapper;
import com.kb.app.module.auth.service.AuthService;
import com.kb.app.util.JwtUtil;
import com.kb.app.util.RedisUtil;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户认证 Service 实现类 — 注册、登录、登出、刷新的核心业务逻辑。
 * <p>
 * 注意事项：
 * <ul>
 *     <li>注册和登录是公开接口，此时 TenantContext 中没有 tenantId，
 *         需要手动设置 TenantContext 后再操作 user 表（因为 user 表受租户拦截器管控）</li>
 *     <li>密码必须使用 BCrypt 加密存储，禁止明文</li>
 *     <li>登出使用 Redis 黑名单机制，因为 JWT 是无状态的，服务端无法主动使其失效</li>
 * </ul>
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    /**
     * BCrypt 密码编码器，用于加密和校验密码。
     * <p>
     * 使用默认强度（10轮），每次加密生成不同的盐值，
     * 即使两个用户使用相同密码，存储的哈希值也不同。
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** JWT 黑名单 Redis Key 前缀 */
    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    /**
     * 用户注册。
     * <p>
     * 注册是公开接口，TenantContext 中没有 tenantId，
     * 需要根据 tenantCode 查出 tenantId 后手动设置 TenantContext，
     * 否则 MyBatis-Plus 租户拦截器会为 user 表 INSERT 追加 tenant_id = NULL。
     *
     * @param request 注册请求
     * @return 包含 Token 和用户信息的响应
     */
    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // ① 根据 tenantCode 查租户是否存在
        TenantDO tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<TenantDO>()
                        .eq(TenantDO::getCode, request.getTenantCode()));
        if (tenant == null) {
            throw BusinessException.of(1001, "租户不存在");
        }
        if (tenant.getStatus() == 0) {
            throw BusinessException.of(1002, "租户已被禁用");
        }

        // 设置 TenantContext，使后续 user 表操作带上正确的 tenant_id
        TenantContext.setTenantId(tenant.getId());
        try {
            // ② 检查该租户下 username 是否已存在（防止重复注册）
            UserDO existUser = userMapper.selectByTenantIdAndUsername(
                    tenant.getId(), request.getUsername());
            if (existUser != null) {
                throw BusinessException.of(1003, "用户名已存在");
            }

            // ③ BCrypt 加密密码（禁止明文存储）
            String passwordHash = passwordEncoder.encode(request.getPassword());

            // ④ 插入 user 记录，role 默认为 2（普通用户）
            UserDO user = UserDO.builder()
                    .tenantId(tenant.getId())
                    .username(request.getUsername())
                    .passwordHash(passwordHash)
                    .role(2)
                    .status(1)
                    .build();
            userMapper.insert(user);

            // ⑤ 生成 accessToken 和 refreshToken 并返回
            String accessToken = jwtUtil.generateAccessToken(
                    user.getId(), tenant.getId(), user.getRole());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId());

            log.info("用户注册成功: tenantId={}, username={}, userId={}",
                    tenant.getId(), user.getUsername(), user.getId());

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .tenantId(tenant.getId())
                    .build();
        } finally {
            // 清理 TenantContext，防止线程池复用导致数据泄露
            TenantContext.clear();
        }
    }

    /**
     * 用户登录。
     * <p>
     * <b>安全设计：为什么"用户名或密码错误"不分开提示？</b>
     * 如果分别提示"用户名不存在"和"密码错误"，攻击者可以通过不同的错误提示
     * 枚举出系统中存在的有效用户名（用户名枚举攻击），再对有效用户名进行暴力破解。
     * 统一返回"用户名或密码错误"可以有效防止此类攻击。
     *
     * @param request 登录请求
     * @return 包含 Token 和用户信息的响应
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // ① 根据 tenantCode 查租户，不存在或已禁用抛异常
        TenantDO tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<TenantDO>()
                        .eq(TenantDO::getCode, request.getTenantCode()));
        if (tenant == null) {
            throw BusinessException.of(1001, "租户不存在");
        }
        if (tenant.getStatus() == 0) {
            throw BusinessException.of(1002, "租户已被禁用");
        }

        // 设置 TenantContext，使后续 user 表查询带上正确的 tenant_id
        TenantContext.setTenantId(tenant.getId());
        try {
            // ② 根据 tenantId + username 查用户，不存在抛"用户名或密码错误"
            UserDO user = userMapper.selectByTenantIdAndUsername(
                    tenant.getId(), request.getUsername());
            if (user == null) {
                // 不提示"用户名不存在"，防止用户名枚举攻击
                throw BusinessException.of(1004, "用户名或密码错误");
            }

            // ③ BCrypt 校验密码，不匹配抛"用户名或密码错误"
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                // 不提示"密码错误"，防止用户名枚举攻击
                throw BusinessException.of(1004, "用户名或密码错误");
            }

            // ④ 检查用户 status，禁用状态抛异常
            if (user.getStatus() == 0) {
                throw BusinessException.of(1005, "账号已被禁用，请联系管理员");
            }

            // ⑤ 生成 accessToken 和 refreshToken 并返回
            String accessToken = jwtUtil.generateAccessToken(
                    user.getId(), tenant.getId(), user.getRole());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId());

            log.info("用户登录成功: tenantId={}, username={}, userId={}",
                    tenant.getId(), user.getUsername(), user.getId());

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .tenantId(tenant.getId())
                    .build();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 用户登出 — 将 accessToken 加入 Redis 黑名单。
     * <p>
     * <b>为什么要用 Redis 黑名单而不是"删除 token"？</b>
     * JWT 是无状态令牌，签发后存储在客户端，服务端不保存任何会话信息。
     * 因此服务端无法像传统 Session 那样直接"删除"令牌使其失效。
     * Redis 黑名单是 JWT 登出的常见解决方案：
     * <ol>
     *     <li>将 token 写入 Redis，Key 为 jwt:blacklist:{token}，Value 为 "1"</li>
     *     <li>TTL 设置为 token 的剩余有效秒数（token 自然过期后黑名单自动清除，避免 Redis 堆积）</li>
     *     <li>Gateway 校验 JWT 时先检查黑名单，在黑名单中的 token 直接拒绝</li>
     * </ol>
     *
     * @param accessToken 当前用户的 accessToken
     */
    @Override
    public void logout(String accessToken) {
        // ① 解析 token 获取剩余有效秒数
        long remainingSeconds = jwtUtil.getRemainingSeconds(accessToken);

        if (remainingSeconds > 0) {
            // ② 将 token 存入 Redis 黑名单，TTL = 剩余有效秒数
            // token 自然过期后，黑名单记录也会被 Redis 自动删除
            redisUtil.set(JWT_BLACKLIST_PREFIX + accessToken, "1", remainingSeconds);
            log.info("用户登出，token 已加入黑名单，剩余有效期: {}秒", remainingSeconds);
        }
        // 如果 token 已过期（remainingSeconds <= 0），无需加入黑名单，本身就无效了
    }

    /**
     * 刷新 Token — 用 refreshToken 换取新的 accessToken。
     * <p>
     * <b>refreshToken 只用于换 accessToken，不能用于业务接口鉴权。</b>
     * refreshToken 的 Payload 中只有 userId，没有 tenantId 和 role，
     * 无法通过 Gateway 的 JWT 校验过滤器（过滤器需要从 token 中提取 tenantId 和 role）。
     * <p>
     * 刷新时从数据库重新查询用户最新信息（角色、状态等），
     * 确保角色变更后新签发的 accessToken 携带最新角色值。
     *
     * @param refreshToken 用户的 refreshToken
     * @return 包含新 accessToken 的响应（refreshToken 保持不变）
     */
    @Override
    public LoginResponse refresh(String refreshToken) {
        // ① 解析 refreshToken，验证未过期
        if (jwtUtil.isExpired(refreshToken)) {
            throw BusinessException.of(1006, "refreshToken 已过期，请重新登录");
        }

        // ② 从 refreshToken 中提取 userId
        Long userId = jwtUtil.getUserId(refreshToken);

        // ③ 根据 userId 查用户信息（获取最新的 tenantId 和 role）
        // 使用 selectByIdIgnoreTenant：refresh 是公开接口，TenantContext 无 tenantId，
        // 需跳过租户拦截器，避免生成 tenant_id = NULL 导致查询为空
        UserDO user = userMapper.selectByIdIgnoreTenant(userId);
        if (user == null) {
            throw BusinessException.of(1007, "用户不存在");
        }
        if (user.getStatus() == 0) {
            throw BusinessException.of(1005, "账号已被禁用，请联系管理员");
        }

        // ④ 重新生成新的 accessToken（携带最新的 tenantId 和 role）
        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getTenantId(), user.getRole());

        log.info("Token 刷新成功: userId={}, tenantId={}", userId, user.getTenantId());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .build();
    }
}
