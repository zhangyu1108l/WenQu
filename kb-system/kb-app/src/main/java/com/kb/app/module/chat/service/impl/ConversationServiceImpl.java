package com.kb.app.module.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kb.app.module.chat.dto.ConversationVO;
import com.kb.app.module.chat.entity.ConversationDO;
import com.kb.app.module.chat.entity.MessageDO;
import com.kb.app.module.chat.mapper.ConversationMapper;
import com.kb.app.module.chat.mapper.MessageMapper;
import com.kb.app.module.chat.service.ConversationHistoryService;
import com.kb.app.module.chat.service.ConversationService;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话管理 Service 实现类。
 * <p>
 * conversation 表包含 tenant_id，查询会由 MyBatis-Plus 租户拦截器自动追加租户条件；
 * message 表不包含 tenant_id，因此删除消息前必须先校验 conversation 的 user_id 归属。
 *
 * @author 问渠系统
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final int TITLE_MAX_LENGTH = 20;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ConversationHistoryService conversationHistoryService;

    /**
     * 创建新会话。
     * <p>
     * 会话 ID 由 MySQL 主键自增生成，MyBatis-Plus 执行 insert 后会回填到实体的 id 字段。
     * title 先写入默认值“新对话”，首条消息发送后会自动更新为首条问题的前 20 个字符。
     *
     * @param userId   当前用户ID
     * @param tenantId 当前租户ID
     * @return 新创建的会话 VO（含 id）
     */
    @Override
    public ConversationVO createConversation(Long userId, Long tenantId) {
        ConversationDO conversation = ConversationDO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .title(DEFAULT_TITLE)
                .build();
        conversationMapper.insert(conversation);
        log.info("会话已创建：conversationId={}，userId={}，tenantId={}",
                conversation.getId(), userId, tenantId);
        return toVO(conversation);
    }

    /**
     * 获取当前用户的会话列表。
     * <p>
     * 只返回当前用户自己的会话：使用 user_id 过滤，避免同租户内不同用户互相看到会话。
     * 当前 Step 按 created_at 降序排列，新创建的会话优先展示。
     *
     * @param userId   当前用户ID
     * @param tenantId 当前租户ID
     * @return 当前用户的会话列表
     */
    @Override
    public List<ConversationVO> getConversationList(Long userId, Long tenantId) {
        List<ConversationDO> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationDO>()
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getTenantId, tenantId)
                        .orderByDesc(ConversationDO::getCreatedAt));

        return conversations.stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 删除会话。
     * <p>
     * 删除顺序非常重要：必须先删除 message 表中的子记录，再删除 conversation 记录，
     * 这样可以保持数据完整性，避免先删父记录后留下无法关联的消息数据。
     * <p>
     * 最后必须清除 Redis 中的对话历史缓存；如果不清除，未来同 ID 会话被读取时可能命中旧缓存，
     * 导致读到已经删除的脏历史。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param tenantId       当前租户ID
     */
    @Override
    @Transactional
    public void deleteConversation(Long conversationId, Long userId, Long tenantId) {
        ConversationDO conversation = getAndCheckOwnership(conversationId, userId, tenantId);

        messageMapper.delete(new LambdaQueryWrapper<MessageDO>()
                .eq(MessageDO::getConversationId, conversationId));
        conversationMapper.deleteById(conversation.getId());
        conversationHistoryService.clearHistory(conversationId);

        log.info("会话已删除：conversationId={}，userId={}，tenantId={}",
                conversationId, userId, tenantId);
    }

    /**
     * 更新会话标题。
     * <p>
     * 首条消息发送后调用，title 取首条问题的前 20 个字符，便于用户在会话列表中识别上下文。
     *
     * @param conversationId 会话ID
     * @param title          首条问题文本
     */
    @Override
    public void updateTitle(Long conversationId, String title) {
        if (conversationId == null || title == null || title.isBlank()) {
            return;
        }

        conversationMapper.update(null,
                new LambdaUpdateWrapper<ConversationDO>()
                        .eq(ConversationDO::getId, conversationId)
                        .set(ConversationDO::getTitle, normalizeTitle(title)));
    }

    private ConversationDO getAndCheckOwnership(Long conversationId, Long userId, Long tenantId) {
        ConversationDO conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw BusinessException.of(6001, "会话不存在");
        }
        if (!conversation.getUserId().equals(userId) || !conversation.getTenantId().equals(tenantId)) {
            throw BusinessException.of(6002, "无权操作此会话");
        }
        return conversation;
    }

    private String normalizeTitle(String title) {
        String trimmedTitle = title.trim();
        if (trimmedTitle.length() <= TITLE_MAX_LENGTH) {
            return trimmedTitle;
        }
        return trimmedTitle.substring(0, TITLE_MAX_LENGTH);
    }

    private ConversationVO toVO(ConversationDO conversation) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .lastMessageAt(conversation.getCreatedAt())
                .build();
    }
}
