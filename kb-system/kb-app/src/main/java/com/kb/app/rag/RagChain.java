package com.kb.app.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.app.module.chat.service.ConversationHistoryService;
import com.kb.app.module.chat.service.MessageService;
import com.kb.app.rag.dto.ChatMessage;
import com.kb.app.rag.dto.ChunkResult;
import com.kb.app.rag.dto.SourceChunkVO;
import com.kb.app.rag.llm.DeepSeekChatClient;
import com.kb.app.rag.retrieval.VectorRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 完整 RAG 问答链路编排器。
 *
 * <pre>
 * 用户问题
 *   ↓ 拉取历史（Redis）
 *   ↓ Query改写（DeepSeek）
 *   ↓ 向量检索（Milvus Top-10）
 *   ↓ 重排序（Top-5）
 *   ↓ 构建Prompt
 *   ↓ 流式生成（DeepSeek）→ SSE逐token推送
 *   ↓ 推送source_chunks（event:done）
 *   ↓ 异步写MySQL + 更新Redis
 * </pre>
 *
 * @author kb-system
 */
@Slf4j
@Component
public class RagChain {

    private static final long SSE_TIMEOUT_MILLIS = 180_000L;
    private static final int HISTORY_MAX_ROUNDS = 5;
    private static final int ROLE_USER = 0;
    private static final int ROLE_ASSISTANT = 1;
    private static final String USER_FACING_ERROR = "服务异常，请重试";

    private final ConversationHistoryService historyService;
    private final QueryRewriter queryRewriter;
    private final VectorRetriever vectorRetriever;
    private final PromptBuilder promptBuilder;
    private final DeepSeekChatClient deepSeekChatClient;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final Executor ragExecutor;

    public RagChain(ConversationHistoryService historyService,
                    QueryRewriter queryRewriter,
                    VectorRetriever vectorRetriever,
                    PromptBuilder promptBuilder,
                    DeepSeekChatClient deepSeekChatClient,
                    MessageService messageService,
                    ObjectMapper objectMapper,
                    @Qualifier("docProcessPool") Executor ragExecutor) {
        this.historyService = historyService;
        this.queryRewriter = queryRewriter;
        this.vectorRetriever = vectorRetriever;
        this.promptBuilder = promptBuilder;
        this.deepSeekChatClient = deepSeekChatClient;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
        this.ragExecutor = ragExecutor;
    }

    /**
     * RAG 问答唯一入口，Controller 可直接返回该 SseEmitter 给前端。
     *
     * @param conversationId 会话ID
     * @param question       用户原始问题
     * @param tenantId       租户ID，用于定位 Milvus 租户 Collection
     * @param userId         用户ID，用于日志追踪
     * @return SSE 长连接发射器
     */
    public SseEmitter ask(Long conversationId, String question, Long tenantId, Long userId) {
        // ① 创建 SseEmitter。大文档检索 + LLM 生成可能较慢，180秒可以给复杂问答留足生成时间。
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        /*
         * ② 提交到 CompletableFuture 异步执行。
         * SSE 是长连接，如果在主线程执行会阻塞 Tomcat 工作线程，影响其他请求处理。
         * 这里不用 @Async：SSE Emitter 需要在同一个请求线程创建并立即返回，@Async 创建的线程
         * 返回的 Emitter 可能已经关闭，导致后续 token 无法稳定写回客户端。
         */
        CompletableFuture.runAsync(() -> {
            try {
                // 步骤1：拉取对话历史，取最近5轮历史用于上下文构建。
                List<ChatMessage> history = historyService.getWindow(conversationId, HISTORY_MAX_ROUNDS);

                // 步骤2：Query 改写，消解指代词；改写后的 query 用于检索，原始 question 用于展示。
                String rewrittenQuery = queryRewriter.rewrite(toRewriterHistory(history), question);

                // 步骤3：向量检索 + 重排序，用改写后的 query 检索，语义更完整，命中精度更高。
                List<ChunkResult> topChunks = vectorRetriever.searchAndRerank(rewrittenQuery, tenantId);

                // 步骤4：构建 RAG Prompt，用原始 question 构建 Prompt，展示给 LLM 的是用户原话。
                String prompt = promptBuilder.buildRagPrompt(history, topChunks, question);

                // 步骤5：DeepSeek 流式生成，SSE 逐 token 推送给前端。
                StringBuilder fullAnswer = new StringBuilder();
                deepSeekChatClient.streamChat(prompt, history, token -> {
                    fullAnswer.append(token);
                    try {
                        emitter.send(
                                SseEmitter.event()
                                        .name("token")
                                        .data(token)
                        );
                    } catch (IOException e) {
                        // 客户端断开连接时不向上抛异常，避免打断主流程；服务端静默结束本次推送尝试。
                        log.warn("SSE客户端已断开，conversationId={}", conversationId);
                    }
                });

                // 步骤6：生成完毕后推送 source_chunks；event:done 是前端渲染来源引用卡片的信号。
                List<SourceChunkVO> sourceVOs = buildSourceVOs(topChunks);
                emitter.send(
                        SseEmitter.event()
                                .name("done")
                                .data(toJson(Collections.singletonMap("source_chunks", sourceVOs)))
                );

                // 步骤7：异步写入 MySQL，生成完成后再写库，不影响流式输出速度。
                messageService.saveAsync(conversationId, question, fullAnswer.toString(), topChunks);

                // 步骤8：更新 Redis 滑动窗口，保证下次改写时能用到最新上下文。
                historyService.push(conversationId, question, fullAnswer.toString());

                // 步骤9：完成 SSE。
                emitter.complete();
            } catch (Exception ex) {
                log.error("RAG问答链路执行失败，conversationId={}, tenantId={}, userId={}",
                        conversationId, tenantId, userId, ex);
                sendErrorAndComplete(emitter);
            }
        }, ragExecutor);

        return emitter;
    }

    /**
     * 将 ChunkResult 转为前端展示 VO，只暴露前端来源卡片需要的字段，不暴露内部 milvus_id 等存储实现细节。
     */
    private List<SourceChunkVO> buildSourceVOs(List<ChunkResult> chunks) {
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

    private List<QueryRewriter.ChatMessage> toRewriterHistory(List<ChatMessage> history) {
        if (CollectionUtils.isEmpty(history)) {
            return Collections.emptyList();
        }

        return history.stream()
                .filter(message -> message != null)
                .map(message -> new QueryRewriter.ChatMessage(
                        message.isAssistant() ? ROLE_ASSISTANT : ROLE_USER,
                        message.getContent()))
                .toList();
    }

    private void sendErrorAndComplete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("error").data(USER_FACING_ERROR));
        } catch (IOException sendEx) {
            log.warn("RAG异常事件推送失败，客户端可能已断开", sendEx);
        } finally {
            emitter.complete();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON序列化失败", ex);
        }
    }
}
