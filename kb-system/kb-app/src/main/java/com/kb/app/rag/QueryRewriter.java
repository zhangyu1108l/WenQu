package com.kb.app.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Query 改写器，用于在 RAG 检索前将用户追问改写成独立完整的问题。
 * <p>
 * 示例：
 * 历史："年假天数怎么算？" -> AI："按工龄计算..."
 * 当前问题："那病假呢？"
 * 改写后："病假天数是如何规定的？"
 *
 * @author kb-system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriter {

    private static final int RECENT_ROUND_MESSAGE_COUNT = 6;

    /*
     * Prompt 设计原因：
     * 1. 消解指代词：向量检索依赖明确语义，"它"、"那个" 等词会让 embedding 缺少实体信息。
     * 2. 补全主语和宾语：用户追问常省略上下文，补全后才能检索到更准确的制度段落。
     * 3. 只输出改写后的问题：下游只需要纯问题文本做 embedding，解释和引号会污染检索语义。
     * 4. 已经清晰则原样返回：避免过度改写改变用户原意。
     */
    private static final String REWRITE_SYSTEM_PROMPT = """
            你是一个问题改写助手。
            根据对话历史，将用户的最新问题改写为一个独立完整的问题。
            要求：
            1. 消解所有指代词（如"它"、"那个"、"刚才说的"）
            2. 补全省略的主语和宾语
            3. 只输出改写后的问题，不要解释，不要加引号
            如果最新问题已经完整清晰，原样返回即可。
            """;

    private final ChatModel chatModel;

    /**
     * 问题改写主方法。
     *
     * @param history         对话历史，按时间正序传入
     * @param currentQuestion 用户最新问题
     * @return 改写后的独立完整问题；失败时返回原始问题
     */
    public String rewrite(List<ChatMessage> history, String currentQuestion) {
        // 无历史时用户问题本身就是独立输入，直接返回可避免一次不必要的 DeepSeek API 调用。
        if (CollectionUtils.isEmpty(history)) {
            return currentQuestion;
        }

        if (!StringUtils.hasText(currentQuestion)) {
            return currentQuestion;
        }

        /*
         * 只取最近 3 轮历史，而不是把全部历史塞进 Prompt：
         * 历史太长会引入早期话题噪声，导致改写偏离当前追问；最近 3 轮通常已经足够消解指代。
         */
        List<ChatMessage> recentHistory = latestThreeRounds(history);
        String userPrompt = formatHistory(recentHistory)
                + "最新问题：" + currentQuestion;

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(REWRITE_SYSTEM_PROMPT),
                    new UserMessage(userPrompt)
            ));
            ChatResponse response = chatModel.call(prompt);
            String rewrittenQuestion = extractText(response);
            return StringUtils.hasText(rewrittenQuestion)
                    ? rewrittenQuestion.trim()
                    : currentQuestion;
        } catch (Exception ex) {
            /*
             * 改写是提升检索效果的优化手段，不是问答主流程的硬依赖。
             * DeepSeek 临时失败或返回异常时降级使用原始问题，避免中断正常 RAG 问答。
             */
            log.warn("问题改写失败，降级使用原始问题：当前问题={}", currentQuestion, ex);
            return currentQuestion;
        }
    }

    /**
     * 将对话历史格式化为 LLM 易读的文本，让模型能理解用户追问所依赖的上下文。
     *
     * @param history 对话历史
     * @return 格式化后的历史文本
     */
    public String formatHistory(List<ChatMessage> history) {
        if (CollectionUtils.isEmpty(history)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : history) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append(message.isAssistant() ? "助手：" : "用户：")
                    .append(message.getContent().trim())
                    .append('\n');
        }
        return builder.toString();
    }

    private List<ChatMessage> latestThreeRounds(List<ChatMessage> history) {
        int fromIndex = Math.max(0, history.size() - RECENT_ROUND_MESSAGE_COUNT);
        return new ArrayList<>(history.subList(fromIndex, history.size()));
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 对话消息轻量模型，role 沿用 message.role 枚举：0=user，1=assistant。
     */
    public static class ChatMessage {

        public static final int ROLE_USER = 0;
        public static final int ROLE_ASSISTANT = 1;

        private Integer role;
        private String content;

        public ChatMessage() {
        }

        public ChatMessage(Integer role, String content) {
            this.role = role;
            this.content = content;
        }

        public static ChatMessage user(String content) {
            return new ChatMessage(ROLE_USER, content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage(ROLE_ASSISTANT, content);
        }

        public Integer getRole() {
            return role;
        }

        public void setRole(Integer role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        private boolean isAssistant() {
            return Integer.valueOf(ROLE_ASSISTANT).equals(role);
        }
    }
}
