package com.kb.app.module.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.app.module.chat.entity.MessageDO;
import com.kb.app.module.chat.mapper.MessageMapper;
import com.kb.app.module.chat.service.ConversationHistoryService;
import com.kb.app.rag.dto.ChatMessage;
import com.kb.app.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对话历史 Service 实现类。
 * <p>
 * 滑动窗口机制示意：
 * <pre>
 * Redis List: [Q1, A1, Q2, A2, Q3, A3, Q4, A4, Q5, A5]
 *                                                  ↑新消息 RPUSH 追加
 * LTRIM 保留最新20条 → 窗口自动滑动
 * </pre>
 * <p>
 * Redis 仅保存轻量级 {@link ChatMessage} JSON 字符串，完整消息与 source_chunks 仍以 MySQL message 表为准。
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationHistoryServiceImpl implements ConversationHistoryService {

    private static final String HISTORY_KEY_TEMPLATE = "conv:%d:history";
    private static final int MESSAGE_COUNT_PER_ROUND = 2;
    private static final int CACHE_MESSAGE_LIMIT = 20;
    private static final long HISTORY_TTL_SECONDS = 86400L;

    private final MessageMapper messageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    /**
     * 获取最近 N 轮对话历史。
     * <p>
     * 一轮对话包含两条消息：user 提问 + assistant 回答，因此需要读取 maxRounds * 2 条。
     * 例如 5 轮对话 = 10 条消息，user 和 assistant 各 5 条。
     * <p>
     * 冷启动场景：Redis 过期或服务重启后，缓存中没有会话历史，此时从 MySQL message 表重建缓存。
     * <p>
     * 返回顺序必须按时间升序排列，LLM 会按顺序理解对话历史；顺序错乱会影响指代消解和上下文理解。
     *
     * @param conversationId 会话ID
     * @param maxRounds      最大轮数
     * @return 按时间升序排列的对话历史
     */
    @Override
    public List<ChatMessage> getWindow(Long conversationId, int maxRounds) {
        if (conversationId == null || maxRounds <= 0) {
            return Collections.emptyList();
        }

        String key = historyKey(conversationId);
        List<String> values = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(values)) {
            return rebuildFromMySQL(conversationId, maxRounds);
        }

        List<ChatMessage> messages = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                messages.add(objectMapper.readValue(value, ChatMessage.class));
            } catch (Exception ex) {
                log.warn("Redis 对话历史反序列化失败，已跳过单条坏数据：conversationId={}，value={}",
                        conversationId, value, ex);
            }
        }

        int limit = maxRounds * MESSAGE_COUNT_PER_ROUND;
        int fromIndex = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(fromIndex, messages.size()));
    }

    /**
     * 向 Redis 追加一轮新的问答。
     * <p>
     * 序列化方式：将 {@link ChatMessage} 对象序列化为 JSON 字符串后存入 Redis List。
     * <p>
     * RPUSH + LTRIM 是 Redis 实现滑动窗口的标准做法：先从尾部追加新消息，再裁剪掉过旧消息。
     * <p>
     * EXPIRE 续期很重要：活跃会话不应该过期，每次问答都将 TTL 重置为 24 小时。
     * <p>
     * Redis 保留 20 条而不是 10 条，是为了多留一些容错空间；getWindow 会再按调用方需要的轮数截取。
     *
     * @param conversationId 会话ID
     * @param question       用户问题
     * @param answer         助手回答
     */
    @Override
    public void push(Long conversationId, String question, String answer) {
        if (conversationId == null) {
            return;
        }

        String key = historyKey(conversationId);
        try {
            String userMessage = objectMapper.writeValueAsString(ChatMessage.user(question));
            String assistantMessage = objectMapper.writeValueAsString(ChatMessage.assistant(answer));

            stringRedisTemplate.opsForList().rightPush(key, userMessage);
            stringRedisTemplate.opsForList().rightPush(key, assistantMessage);
            stringRedisTemplate.opsForList().trim(key, -CACHE_MESSAGE_LIMIT, -1);
            stringRedisTemplate.expire(key, HISTORY_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            log.error("对话历史序列化失败：conversationId={}", conversationId, ex);
            throw new IllegalStateException("对话历史序列化失败", ex);
        }
    }

    /**
     * 从 MySQL 重建 Redis 缓存。
     * <p>
     * 先按 created_at 降序查询，是为了配合 LIMIT 直接取最新 maxRounds * 2 条消息；
     * 查询完成后再反转为升序，是为了保证返回给 LLM 的时间顺序正确。
     *
     * @param conversationId 会话ID
     * @param maxRounds      最大轮数
     * @return 按时间升序排列的对话历史
     */
    @Override
    public List<ChatMessage> rebuildFromMySQL(Long conversationId, int maxRounds) {
        if (conversationId == null || maxRounds <= 0) {
            return Collections.emptyList();
        }

        int limit = maxRounds * MESSAGE_COUNT_PER_ROUND;
        List<MessageDO> records = messageMapper.selectList(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conversationId)
                        .orderByDesc(MessageDO::getCreatedAt)
                        .orderByDesc(MessageDO::getId)
                        .last("LIMIT " + limit));

        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }

        Collections.reverse(records);
        List<ChatMessage> messages = records.stream()
                .map(this::toChatMessage)
                .toList();

        writeHistoryCache(conversationId, messages);
        return messages;
    }

    /**
     * 清除某会话的 Redis 缓存。
     * <p>
     * 使用场景：用户删除会话时调用，避免已删除会话的短期历史继续留在 Redis。
     *
     * @param conversationId 会话ID
     */
    @Override
    public void clearHistory(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        redisUtil.delete(historyKey(conversationId));
    }

    private void writeHistoryCache(Long conversationId, List<ChatMessage> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }

        String key = historyKey(conversationId);
        List<String> values = messages.stream()
                .map(this::serialize)
                .toList();
        redisUtil.delete(key);
        stringRedisTemplate.opsForList().rightPushAll(key, values);
        stringRedisTemplate.opsForList().trim(key, -CACHE_MESSAGE_LIMIT, -1);
        stringRedisTemplate.expire(key, HISTORY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private String serialize(ChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            log.error("对话历史序列化失败：messageRole={}", message != null ? message.getRole() : null, ex);
            throw new IllegalStateException("对话历史序列化失败", ex);
        }
    }

    private ChatMessage toChatMessage(MessageDO message) {
        String role = Integer.valueOf(0).equals(message.getRole())
                ? ChatMessage.ROLE_USER
                : ChatMessage.ROLE_ASSISTANT;
        return ChatMessage.builder()
                .role(role)
                .content(message.getContent())
                .createdAt(toEpochMilli(message))
                .build();
    }

    private Long toEpochMilli(MessageDO message) {
        if (message.getCreatedAt() == null) {
            return null;
        }
        return message.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private String historyKey(Long conversationId) {
        return String.format(HISTORY_KEY_TEMPLATE, conversationId);
    }
}
