package com.kb.app.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类 — 对 {@link StringRedisTemplate} 的轻量封装。
 * <p>
 * <b>为什么封装 RedisTemplate 而不直接使用：</b>
 * <ol>
 *     <li><b>统一 API 风格</b> — RedisTemplate 原生方法需要 opsForValue()、opsForList() 等中间调用，
 *         链式写法冗长且不直观。封装后提供简洁的 set/get/delete 方法，降低业务层使用复杂度。</li>
 *     <li><b>统一异常处理</b> — Redis 连接超时、序列化失败等异常由工具类统一记录日志，
 *         避免每个调用点都写 try-catch，业务代码更干净。</li>
 *     <li><b>屏蔽实现细节</b> — 业务层不感知底层使用的是 Lettuce 还是 Jedis 客户端，
 *         后续切换客户端或迁移到 Redis Cluster 时只需修改工具类，不影响业务代码。</li>
 *     <li><b>便于单元测试</b> — Mock 一个简单的 RedisUtil 比 Mock RedisTemplate 的多层嵌套调用更容易。</li>
 * </ol>
 * <p>
 * <b>当前使用场景：</b>
 * <ul>
 *     <li>JWT 登出黑名单 — Key: jwt:blacklist:{token}，TTL=token 剩余有效期</li>
 *     <li>对话历史滑动窗口 — Key: conv:{conversationId}:history，TTL 24 小时</li>
 * </ul>
 * <p>
 * 使用 {@link StringRedisTemplate}（Key 和 Value 都是 String 序列化），
 * 适合存储 JWT token、JSON 字符串等文本数据。
 *
 * @author kb-system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 设置带过期时间的键值对。
     * <p>
     * 典型用法：JWT 登出时将 token 加入黑名单
     * {@code redisUtil.set("jwt:blacklist:" + token, "1", remainingSeconds)}
     *
     * @param key        Redis Key
     * @param value      Redis Value（字符串）
     * @param ttlSeconds 过期时间（秒），到期后 Redis 自动删除
     */
    public void set(String key, String value, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取指定 Key 的值。
     *
     * @param key Redis Key
     * @return 对应的值，Key 不存在时返回 null
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 删除指定 Key。
     * <p>
     * 典型用法：删除对话历史缓存、手动清理黑名单等。
     *
     * @param key Redis Key
     * @return true=删除成功，false=Key 不存在
     */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 判断指定 Key 是否存在。
     * <p>
     * 典型用法：检查 JWT 是否在黑名单中
     * {@code redisUtil.hasKey("jwt:blacklist:" + token)}
     *
     * @param key Redis Key
     * @return true=存在，false=不存在
     */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }
}
