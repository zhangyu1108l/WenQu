package com.kb.app.module.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.app.module.chat.dto.ConversationVO;
import com.kb.app.module.chat.dto.MessageVO;
import com.kb.app.module.chat.service.ConversationService;
import com.kb.app.module.chat.service.MessageService;
import com.kb.app.rag.RagChain;
import com.kb.common.dto.Result;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话管理 Controller，接口路径严格按架构文档定义。
 *
 * <ul>
 *     <li>POST   /api/chat/conversations               - 登录用户 - 创建新会话</li>
 *     <li>GET    /api/chat/conversations               - 登录用户 - 获取当前用户会话列表</li>
 *     <li>DELETE /api/chat/conversations/{id}          - 登录用户 - 删除会话</li>
 *     <li>GET    /api/chat/conversations/{id}/messages - 登录用户 - 获取完整消息历史</li>
 *     <li>POST   /api/chat/conversations/{id}/ask      - 登录用户 - SSE 流式问答</li>
 * </ul>
 *
 * Gateway 完成 JWT 校验后会注入 X-User-Id / X-Tenant-Id，请求进入 kb-app 后直接从请求头读取，
 * 不在 Controller 中重复解析 JWT。
 *
 * @author kb-system
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int QUESTION_MAX_LENGTH = 500;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final RagChain ragChain;
    private final ObjectMapper objectMapper;

    /**
     * 创建新会话。
     *
     * @param userId   当前用户ID，由 Gateway 注入 X-User-Id
     * @param tenantId 当前租户ID，由 Gateway 注入 X-Tenant-Id
     * @return 新创建的会话
     */
    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        ConversationVO conversation = conversationService.createConversation(userId, tenantId);
        return Result.ok(conversation);
    }

    /**
     * 获取当前用户的会话列表。
     *
     * @param userId   当前用户ID，由 Gateway 注入 X-User-Id
     * @param tenantId 当前租户ID，由 Gateway 注入 X-Tenant-Id
     * @return 当前用户在当前租户下的会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> getConversationList(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        List<ConversationVO> conversations = conversationService.getConversationList(userId, tenantId);
        return Result.ok(conversations);
    }

    /**
     * 删除会话。
     *
     * 删除前先校验会话属于当前 userId + tenantId，防止同租户用户之间或跨租户误删会话。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID，由 Gateway 注入 X-User-Id
     * @param tenantId       当前租户ID，由 Gateway 注入 X-Tenant-Id
     * @return 统一响应，data 为 null
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(
            @PathVariable("id") Long conversationId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        assertConversationOwnership(conversationId, userId, tenantId);
        conversationService.deleteConversation(conversationId, userId, tenantId);
        return Result.ok();
    }

    /**
     * 获取完整消息历史。
     *
     * MySQL message 表保存完整历史，Redis 只保留最近 5 轮作为 RAG 上下文滑动窗口。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID，由 Gateway 注入 X-User-Id
     * @param tenantId       当前租户ID，由 Gateway 注入 X-Tenant-Id
     * @return 消息历史列表
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> getMessageList(
            @PathVariable("id") Long conversationId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        List<MessageVO> messages = messageService.getMessageList(conversationId, userId, tenantId);
        return Result.ok(messages);
    }

    /**
     * SSE 流式问答接口。
     *
     * SSE(Server-Sent Events) 是基于 HTTP 的长连接：前端发起一次请求后保持连接不断开，
     * 服务端可以在 RAG 检索和 DeepSeek 生成过程中主动向浏览器连续推送事件。
     * 前端使用 EventSource 监听三类事件：
     * <ul>
     *     <li>event:token - 每收到一个 token，就追加到当前助手气泡文字中。</li>
     *     <li>event:done  - 模型输出结束，携带 source_chunks JSON，前端据此渲染来源引用卡片。</li>
     *     <li>event:error - RAG 链路异常时展示错误提示。</li>
     * </ul>
     *
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE 的作用是把响应 Content-Type 标记为 text/event-stream，
     * 告诉浏览器按 SSE 协议解析返回内容，而不是按普通 JSON 或纯文本处理。
     *
     * 此接口不使用统一 Result 包装格式，因为 SSE 协议要求响应体保持纯 text/event-stream 事件流；
     * 如果外层包一层 {"code":0,"msg":"ok","data":...}，会破坏 EventSource 对 event/data 帧的解析。
     *
     * SseEmitter 的超时时间在 RagChain.ask 内集中设置。复杂问题可能包含历史读取、问题改写、
     * Milvus Top-10 检索、Top-5 重排和 LLM 流式生成，超时时间需要覆盖完整生成周期。
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID，由 Gateway 注入 X-User-Id
     * @param tenantId       当前租户ID，由 Gateway 注入 X-Tenant-Id
     * @param questionParam  URL 或 form 参数中的 question，可选
     * @param questionBody   请求体中的 question，可选，支持纯字符串或 {"question":"..."} JSON
     * @return SSE 长连接发射器，不包装 Result
     */
    @PostMapping(value = "/conversations/{id}/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(
            @PathVariable("id") Long conversationId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(value = "question", required = false) String questionParam,
            @RequestBody(required = false) String questionBody) {
        String question = resolveQuestion(questionParam, questionBody);
        validateQuestion(question);
        assertConversationOwnership(conversationId, userId, tenantId);
        return ragChain.ask(conversationId, question, tenantId, userId);
    }

    private String resolveQuestion(String questionParam, String questionBody) {
        if (StringUtils.hasText(questionParam)) {
            return questionParam.trim();
        }
        if (!StringUtils.hasText(questionBody)) {
            return null;
        }

        String trimmedBody = questionBody.trim();
        if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) {
            String jsonQuestion = readQuestionFromJsonBody(trimmedBody);
            if (StringUtils.hasText(jsonQuestion)) {
                return jsonQuestion.trim();
            }
        }
        return trimmedBody;
    }

    private String readQuestionFromJsonBody(String body) {
        try {
            JsonNode questionNode = objectMapper.readTree(body).get("question");
            if (questionNode == null || questionNode.isNull()) {
                return null;
            }
            return questionNode.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private void validateQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw BusinessException.of(400, "question 不能为空");
        }

        /*
         * 限制 question 最大 500 字，避免超长输入叠加历史、检索段落和系统提示后，
         * 导致 Prompt 溢出 LLM 上下文窗口，影响生成稳定性和响应延迟。
         */
        if (question.length() > QUESTION_MAX_LENGTH) {
            throw BusinessException.of(400, "question 长度不能超过 500 字");
        }
    }

    private void assertConversationOwnership(Long conversationId, Long userId, Long tenantId) {
        if (conversationId == null) {
            throw BusinessException.of(400, "conversationId 不能为空");
        }

        boolean owned = conversationService.getConversationList(userId, tenantId).stream()
                .anyMatch(conversation -> conversationId.equals(conversation.getId()));
        if (!owned) {
            throw BusinessException.of(6002, "无权操作此会话");
        }
    }
}
