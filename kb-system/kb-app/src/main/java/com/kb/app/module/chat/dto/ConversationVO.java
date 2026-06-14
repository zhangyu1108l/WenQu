package com.kb.app.module.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话列表响应 VO。
 * <p>
 * 仅返回前端会话列表需要展示和排序的字段，隐藏 user_id、tenant_id 等内部字段。
 *
 * @author 问渠系统
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {

    /** 会话主键 */
    private Long id;

    /** 会话标题，默认“新对话”，首条消息发送后自动更新 */
    private String title;

    /** 会话创建时间 */
    private LocalDateTime createdAt;

    /**
     * 最近一条消息时间。
     * <p>
     * 用于会话列表排序：最近有消息的会话排在前面，用户继续对话时体验更好。
     */
    private LocalDateTime lastMessageAt;
}
