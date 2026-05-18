package com.kb.app.module.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.app.module.chat.dto.MessageVO;
import com.kb.app.module.chat.entity.ConversationDO;
import com.kb.app.module.chat.entity.MessageDO;
import com.kb.app.module.chat.mapper.ConversationMapper;
import com.kb.app.module.chat.mapper.MessageMapper;
import com.kb.app.module.chat.service.MessageService;
import com.kb.app.rag.dto.ChunkResult;
import com.kb.app.rag.dto.SourceChunkVO;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 消息存储 Service 实现类。
 * <p>
 * message 表保存完整对话历史；Redis 只保留最近 5 轮用于 RAG 上下文窗口。
 *
 * @author kb-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final int ROLE_USER = 0;
    private static final int ROLE_ASSISTANT = 1;
    private static final int TITLE_MAX_LENGTH = 20;
    private static final TypeReference<List<SourceChunkVO>> SOURCE_CHUNK_LIST_TYPE = new TypeReference<>() {
    };

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ObjectMapper objectMapper;

    /**
     * 获取会话完整消息历史。
     * <p>
     * 为什么从 MySQL 查而不从 Redis 查：Redis 只保留最近 5 轮消息作为 RAG 滑动窗口，
     * MySQL message 表才保存完整历史，前端打开会话详情必须读取 MySQL。
     * <p>
     * source_chunks 在数据库中是 JSON 字符串；这里统一反序列化为 List&lt;SourceChunkVO&gt;，
     * 让前端拿到结构化来源引用，而不需要自己解析字符串。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @param tenantId       当前租户ID
     * @return 会话完整消息列表，按创建时间升序排列
     */
    @Override
    public List<MessageVO> getMessageList(Long conversationId, Long userId, Long tenantId) {
        checkConversationOwnership(conversationId, userId, tenantId);

        List<MessageDO> messages = messageMapper.selectByConversationId(conversationId);
        return messages.stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 异步保存一轮问答消息到 MySQL。
     * <p>
     * 使用 @Async 的原因：DeepSeek 生成完成后异步写库，不阻塞 SSE 推流收尾，
     * 用户可以更快收到 event:done。
     * <p>
     * 两条消息必须都插入成功：用户问题和 AI 回答天然是一对，如果只保存一条，
     * 后续历史展示、Redis 冷启动重建和上下文理解都会出现不完整数据。
     * <p>
     * 步骤④更新标题的触发条件：插入前先查询该会话的消息总数，如果为 0，
     * 说明这是该会话第一轮问答，需要用 question 前 20 个字符更新 conversation.title。
     *
     * @param conversationId 会话ID
     * @param question       用户问题
     * @param answer         AI回答
     * @param chunks         本轮回答引用的检索 chunk
     */
    @Async("docProcessPool")
    @Override
    @Transactional
    public void saveAsync(Long conversationId, String question, String answer, List<ChunkResult> chunks) {
        long existingMessageCount = messageMapper.selectCount(
                new LambdaQueryWrapper<MessageDO>()
                        .eq(MessageDO::getConversationId, conversationId));

        messageMapper.insert(MessageDO.builder()
                .conversationId(conversationId)
                .role(ROLE_USER)
                .content(question)
                .build());

        String sourceChunksJson = serializeSourceChunks(chunks);
        messageMapper.insert(MessageDO.builder()
                .conversationId(conversationId)
                .role(ROLE_ASSISTANT)
                .content(answer)
                .sourceChunks(sourceChunksJson)
                .build());

        if (existingMessageCount == 0) {
            updateConversationTitle(conversationId, question);
        }

        log.info("问答消息已异步保存：conversationId={}，sourceChunkCount={}",
                conversationId, chunks == null ? 0 : chunks.size());
    }

    private void checkConversationOwnership(Long conversationId, Long userId, Long tenantId) {
        ConversationDO conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw BusinessException.of(6001, "会话不存在");
        }
        if (!Objects.equals(conversation.getUserId(), userId)
                || !Objects.equals(conversation.getTenantId(), tenantId)) {
            throw BusinessException.of(6002, "无权查看此会话");
        }
    }

    private MessageVO toVO(MessageDO message) {
        return MessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .role(toRoleName(message.getRole()))
                .content(message.getContent())
                .sourceChunks(deserializeSourceChunks(message.getSourceChunks()))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String toRoleName(Integer role) {
        if (Integer.valueOf(ROLE_USER).equals(role)) {
            return "user";
        }
        if (Integer.valueOf(ROLE_ASSISTANT).equals(role)) {
            return "assistant";
        }
        throw new IllegalStateException("未知消息角色：" + role);
    }

    private List<SourceChunkVO> deserializeSourceChunks(String sourceChunks) {
        if (sourceChunks == null || sourceChunks.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(sourceChunks, SOURCE_CHUNK_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("source_chunks 反序列化失败，已返回空来源列表：sourceChunks={}", sourceChunks, ex);
            return Collections.emptyList();
        }
    }

    private String serializeSourceChunks(List<ChunkResult> chunks) {
        List<SourceChunkVO> sourceChunks = toSourceChunkVOs(chunks);
        try {
            return objectMapper.writeValueAsString(sourceChunks);
        } catch (JsonProcessingException ex) {
            log.error("source_chunks 序列化失败：chunkCount={}", sourceChunks.size(), ex);
            throw new IllegalStateException("source_chunks 序列化失败", ex);
        }
    }

    private List<SourceChunkVO> toSourceChunkVOs(List<ChunkResult> chunks) {
        if (CollectionUtils.isEmpty(chunks)) {
            return Collections.emptyList();
        }
        return chunks.stream()
                .filter(chunk -> chunk != null)
                .map(chunk -> SourceChunkVO.builder()
                        .chunkId(chunk.getChunkId())
                        .documentId(chunk.getDocumentId())
                        .content(chunk.getContent())
                        .headingPath(chunk.getHeadingPath())
                        .pageNo(chunk.getPageNo())
                        .score(chunk.getScore())
                        .build())
                .toList();
    }

    private void updateConversationTitle(Long conversationId, String question) {
        if (question == null || question.isBlank()) {
            return;
        }
        conversationMapper.update(null,
                new LambdaUpdateWrapper<ConversationDO>()
                        .eq(ConversationDO::getId, conversationId)
                        .set(ConversationDO::getTitle, normalizeTitle(question)));
    }

    private String normalizeTitle(String question) {
        String trimmedQuestion = question.trim();
        if (trimmedQuestion.length() <= TITLE_MAX_LENGTH) {
            return trimmedQuestion;
        }
        return trimmedQuestion.substring(0, TITLE_MAX_LENGTH);
    }
}
