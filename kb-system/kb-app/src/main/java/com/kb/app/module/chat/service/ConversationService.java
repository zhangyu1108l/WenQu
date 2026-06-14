package com.kb.app.module.chat.service;

import com.kb.app.module.chat.dto.ConversationVO;

import java.util.List;

/**
 * 会话管理 Service 接口。
 * <p>
 * 负责会话的新建、列表查询、删除和标题更新；消息写入与 SSE 问答链路由后续 Service 负责。
 *
 * @author 问渠系统
 */
public interface ConversationService {

    /**
     * 创建新会话。
     *
     * @param userId   当前用户ID
     * @param tenantId 当前租户ID
     * @return 新创建的会话 VO，包含 MySQL 自增生成的会话ID
     */
    ConversationVO createConversation(Long userId, Long tenantId);

    /**
     * 获取当前用户的会话列表。
     *
     * @param userId   当前用户ID
     * @param tenantId 当前租户ID
     * @return 当前用户的会话列表
     */
    List<ConversationVO> getConversationList(Long userId, Long tenantId);

    /**
     * 删除会话。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param tenantId       当前租户ID
     */
    void deleteConversation(Long conversationId, Long userId, Long tenantId);

    /**
     * 更新会话标题。
     *
     * @param conversationId 会话ID
     * @param title          首条问题文本，最终标题取前 20 个字符
     */
    void updateTitle(Long conversationId, String title);
}
