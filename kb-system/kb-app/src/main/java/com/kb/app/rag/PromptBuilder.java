package com.kb.app.rag;

import com.kb.app.rag.dto.ChatMessage;
import com.kb.app.rag.dto.ChunkResult;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 构建器。
 * <p>
 * Prompt 工程的核心原则：
 * 1. 角色定义清晰，让 LLM 明确自己是企业内部知识库助手。
 * 2. 约束 LLM 只根据文档内容回答，防止模型使用自身知识补全答案而产生幻觉。
 * 3. 段落来源清晰，让回答具备可追溯性，便于用户核对原始文档依据。
 *
 * @author 问渠系统
 */
@Component
public class PromptBuilder {

    private static final int RECENT_ROUND_MESSAGE_COUNT = 6;
    private static final int MAX_PROMPT_TOKEN_COUNT = 6000;
    private static final double CHINESE_CHAR_PER_TOKEN = 1.5D;

    private static final String RAG_SYSTEM_DEFINITION = """
            ===== 系统角色定义 =====
            你是一个企业内部知识库助手。
            请只根据文档内容回答问题，不要使用文档外知识补充答案。
            如果文档内容中没有相关信息，请明确告知用户"根据现有文档，
            无法找到相关信息"，不要编造内容。
            回答时请引用具体的文档段落来支撑你的回答。
            """;

    private static final String REWRITE_SYSTEM_PROMPT = """
            你是一个问题改写助手。
            根据对话历史，将用户的最新问题改写为一个独立完整的问题。
            要求：
            1. 消解所有指代词（如"它"、"那个"、"刚才说的"）
            2. 补全省略的主语和宾语
            3. 只输出改写后的问题，不要解释，不要加引号
            如果最新问题已经完整清晰，原样返回即可。
            """;

    /**
     * 构建 RAG 问答 Prompt。
     * <p>
     * Prompt 顺序固定为：系统角色定义 -> 参考文档段落 -> 对话历史 -> 当前问题。
     * 参考文档放在历史之前，是为了让 LLM 优先关注检索命中的企业文档内容，
     * 再结合最近上下文理解当前问题，减少被历史话题带偏的概率。
     *
     * @param history  对话历史，按时间正序传入
     * @param chunks   检索命中的文档分块
     * @param question 当前问题
     * @return 完整 RAG Prompt
     */
    public String buildRagPrompt(List<ChatMessage> history, List<ChunkResult> chunks, String question) {
        List<ChatMessage> recentHistory = latestValidMessages(history);
        String prompt = buildRagPromptWithHistory(recentHistory, chunks, question);

        while (estimateTokenCount(prompt) > MAX_PROMPT_TOKEN_COUNT && !recentHistory.isEmpty()) {
            recentHistory.remove(0);
            prompt = buildRagPromptWithHistory(recentHistory, chunks, question);
        }

        return prompt;
    }

    /**
     * 构建 Query 改写 Prompt，供 QueryRewriter 调用。
     *
     * @param history         对话历史，按时间正序传入
     * @param currentQuestion 用户当前问题
     * @return Query 改写 Prompt
     */
    public String buildRewritePrompt(List<ChatMessage> history, String currentQuestion) {
        List<ChatMessage> recentHistory = latestValidMessages(history);
        String prompt = buildRewritePromptWithHistory(recentHistory, currentQuestion);

        while (estimateTokenCount(prompt) > MAX_PROMPT_TOKEN_COUNT && !recentHistory.isEmpty()) {
            recentHistory.remove(0);
            prompt = buildRewritePromptWithHistory(recentHistory, currentQuestion);
        }

        return prompt;
    }

    /**
     * 粗略估算文本 token 数。
     * <p>
     * DeepSeek 有上下文窗口限制，超长 Prompt 可能被模型截断或直接报错，
     * 因此这里用中文场景下的粗略估算值（字符数 / 1.5）提前控制长度。
     * 如果 Prompt 超过 6000 token，会自动截断最旧的历史消息；文档段落和当前问题保留，
     * 避免丢失 RAG 回答最关键的证据和用户意图。
     *
     * @param text 待估算文本
     * @return 估算 token 数
     */
    public int estimateTokenCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHINESE_CHAR_PER_TOKEN);
    }

    private String buildRagPromptWithHistory(List<ChatMessage> history, List<ChunkResult> chunks, String question) {
        StringBuilder builder = new StringBuilder();
        builder.append(RAG_SYSTEM_DEFINITION)
                .append('\n')
                .append("===== 参考文档段落 =====")
                .append('\n');

        if (!appendChunks(builder, chunks)) {
            builder.append('\n');
        }

        builder.append("===== 对话历史 =====")
                .append('\n');
        appendHistory(builder, history);

        builder.append('\n')
                .append("===== 当前问题 =====")
                .append('\n')
                .append(safeTrim(question));

        return builder.toString();
    }

    private String buildRewritePromptWithHistory(List<ChatMessage> history, String currentQuestion) {
        StringBuilder builder = new StringBuilder();
        builder.append("系统提示：")
                .append('\n')
                .append(REWRITE_SYSTEM_PROMPT)
                .append('\n')
                .append("对话历史：最近3轮")
                .append('\n');
        appendHistory(builder, history);

        builder.append('\n')
                .append("改写要求：")
                .append('\n')
                .append(safeTrim(currentQuestion));

        return builder.toString();
    }

    private boolean appendChunks(StringBuilder builder, List<ChunkResult> chunks) {
        if (CollectionUtils.isEmpty(chunks)) {
            return false;
        }

        int paragraphIndex = 1;
        for (ChunkResult chunk : chunks) {
            if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
                continue;
            }

            builder.append("【段落")
                    .append(paragraphIndex++)
                    .append("】")
                    .append('\n')
                    /*
                     * 在 Prompt 中显示来源信息，是为了引导 LLM 回答时引用具体段落，
                     * 让答案更容易回溯到原文依据，同时提升 Ragas 的 Faithfulness 指标。
                     */
                    .append("来源：")
                    .append(formatSource(chunk))
                    .append('\n')
                    .append("内容：")
                    .append(chunk.getContent().trim())
                    .append('\n')
                    .append('\n');
        }
        return paragraphIndex > 1;
    }

    private void appendHistory(StringBuilder builder, List<ChatMessage> history) {
        if (CollectionUtils.isEmpty(history)) {
            return;
        }

        for (ChatMessage message : history) {
            builder.append(message.isAssistant() ? "助手：" : "用户：")
                    .append(message.getContent().trim())
                    .append('\n');
        }
    }

    private String formatSource(ChunkResult chunk) {
        StringBuilder source = new StringBuilder();
        if (StringUtils.hasText(chunk.getHeadingPath())) {
            source.append(chunk.getHeadingPath().trim());
        } else {
            source.append("文档片段");
        }

        if (chunk.getPageNo() != null) {
            source.append("（第")
                    .append(chunk.getPageNo())
                    .append("页）");
        }

        return source.toString();
    }

    private List<ChatMessage> latestValidMessages(List<ChatMessage> history) {
        if (CollectionUtils.isEmpty(history)) {
            return new ArrayList<>();
        }

        List<ChatMessage> validMessages = history.stream()
                .filter(message -> message != null && StringUtils.hasText(message.getContent()))
                .toList();
        if (validMessages.isEmpty()) {
            return new ArrayList<>();
        }

        int fromIndex = Math.max(0, validMessages.size() - RECENT_ROUND_MESSAGE_COUNT);
        return new ArrayList<>(validMessages.subList(fromIndex, validMessages.size()));
    }

    private String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }
}
