package com.kb.app.module.chat.service;

import com.kb.app.rag.dto.ChatMessage;

import java.util.List;

/**
 * 对话历史 Service 接口。
 * <p>
 * 负责维护 Redis 中的会话滑动窗口，并在 Redis 缓存失效时从 MySQL message 表重建最近历史。
 *
 * @author kb-system
 */
public interface ConversationHistoryService {

    /**
     * 获取最近 N 轮对话历史。
     *
     * @param conversationId 会话ID
     * @param maxRounds      最大轮数，一轮包含 user + assistant 两条消息
     * @return 按时间升序排列的对话历史
     */
    List<ChatMessage> getWindow(Long conversationId, int maxRounds);

    /**
     * 向 Redis 追加一轮新的问答。
     *
     * @param conversationId 会话ID
     * @param question       用户问题
     * @param answer         助手回答
     */
    void push(Long conversationId, String question, String answer);

    /**
     * Redis 冷启动时，从 MySQL 重建最近 N 轮对话历史缓存。
     *
     * @param conversationId 会话ID
     * @param maxRounds      最大轮数，一轮包含 user + assistant 两条消息
     * @return 按时间升序排列的对话历史
     */
    List<ChatMessage> rebuildFromMySQL(Long conversationId, int maxRounds);

    /**
     * 清除某个会话的 Redis 历史缓存。
     *
     * @param conversationId 会话ID
     */
    void clearHistory(Long conversationId);
}
