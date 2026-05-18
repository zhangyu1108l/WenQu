package com.kb.app.module.chat.dto;

import com.kb.app.rag.dto.SourceChunkVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息响应 VO。
 * <p>
 * role 返回字符串 "user" / "assistant"，而不是数据库中的数字 0 / 1。
 * 这样前端渲染时语义更清晰，也避免把数据库枚举值直接暴露给页面逻辑。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    /** 消息主键 */
    private Long id;

    /** 所属会话ID */
    private Long conversationId;

    /** 消息角色，固定返回 "user" 或 "assistant" */
    private String role;

    /** 消息正文内容 */
    private String content;

    /** AI回答引用的来源 chunk；用户消息为空列表 */
    private List<SourceChunkVO> sourceChunks;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
