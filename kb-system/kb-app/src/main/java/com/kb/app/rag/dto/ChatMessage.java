package com.kb.app.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 对话消息轻量对象，用于 Redis 缓存和 LLM 多轮对话调用。
 * <p>
 * 该类不同于 MySQL 的 message 表：这里只保留 role、content、createdAt 三个轻量字段，
 * 不包含 source_chunks 等用于来源引用展示的重字段，避免 Redis 滑动窗口过大。
 *
 * @author kb-system
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * 消息角色，仅支持 user 或 assistant。
     */
    private String role;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 创建时间戳，单位毫秒。
     */
    private Long createdAt;

    public static ChatMessage user(String content) {
        return ChatMessage.builder()
                .role(ROLE_USER)
                .content(content)
                .createdAt(System.currentTimeMillis())
                .build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role(ROLE_ASSISTANT)
                .content(content)
                .createdAt(System.currentTimeMillis())
                .build();
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equals(role);
    }
}
