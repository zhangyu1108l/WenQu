package com.kb.app.module.chat.service;

import com.kb.app.module.chat.dto.MessageVO;
import com.kb.app.rag.dto.ChunkResult;

import java.util.List;

/**
 * 消息存储 Service 接口。
 * <p>
 * 负责从 MySQL 读取完整消息历史，以及在 RAG 生成完成后异步落库用户问题与 AI 回答。
 *
 * @author 问渠系统
 */
public interface MessageService {

    /**
     * 获取会话完整消息历史。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param tenantId       当前租户ID
     * @return 会话完整消息列表，按创建时间升序排列
     */
    List<MessageVO> getMessageList(Long conversationId, Long userId, Long tenantId);

    /**
     * 异步保存一轮问答消息。
     *
     * @param conversationId 会话ID
     * @param question       用户问题
     * @param answer         AI回答
     * @param chunks         本轮回答引用的检索 chunk
     */
    void saveAsync(Long conversationId, String question, String answer, List<ChunkResult> chunks);
}
