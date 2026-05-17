package com.kb.app.rag.llm;

import com.kb.app.config.DeepSeekProperties;
import com.kb.app.rag.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * DeepSeek Chat 客户端封装。
 * <p>
 * 业务代码不直接依赖 Spring AI 底层调用细节，是为了把模型供应商、框架 API 和 RAG 业务逻辑隔离开；
 * 后续如果切换其他 LLM，只需要调整这一层封装，RAG 链路和上层接口无需感知底层差异。
 *
 * @author kb-system
 */
@Slf4j
@Component
public class DeepSeekChatClient {

    private static final String STREAM_ERROR_PREFIX = "[LLM_STREAM_ERROR] ";

    private final ChatClient chatClient;
    private final DeepSeekProperties properties;

    public DeepSeekChatClient(ChatModel chatModel, DeepSeekProperties properties) {
        this.chatClient = ChatClient.create(chatModel);
        this.properties = properties;
    }

    /**
     * 流式对话主方法。
     * <p>
     * Consumer<String> 回调用于解耦生成逻辑和推送逻辑：本类只负责从 DeepSeek 生成 token，
     * SSE 推送由调用方处理，符合单一职责原则。
     * <p>
     * history 使用原生消息列表传入，而不是提前拼接进 prompt，是因为 Spring AI 的 ChatClient
     * 支持标准多轮对话消息格式，比手工拼接字符串更规范，也更利于后续替换模型或接入工具调用。
     * <p>
     * Spring AI 的 stream().content() 返回 Flux<String> 响应流；blockLast() 会触发订阅，
     * 每收到一个 chunk 就通过 doOnNext 回调给调用方，流结束时自然完成，不需要显式关闭。
     * <p>
     * 异常处理策略：流式过程中如果出现异常，会先通过 tokenConsumer 通知调用方，再继续抛出异常，
     * 交由上层 SSE 或 RAG 编排逻辑统一收尾，避免静默失败。
     *
     * @param prompt        当前 Prompt
     * @param history       最近对话历史，按时间正序传入
     * @param tokenConsumer token 回调
     */
    public void streamChat(String prompt, List<ChatMessage> history, Consumer<String> tokenConsumer) {
        if (tokenConsumer == null) {
            throw new IllegalArgumentException("tokenConsumer 不能为空");
        }

        AtomicBoolean errorNotified = new AtomicBoolean(false);
        try {
            List<Message> messages = buildMessages(prompt, history);
            chatClient.prompt()
                    .messages(messages)
                    .options(buildOptions())
                    .stream()
                    .content()
                    .doOnNext(tokenConsumer)
                    .doOnError(ex -> notifyStreamError(tokenConsumer, ex, errorNotified))
                    .blockLast();
        } catch (Exception ex) {
            notifyStreamError(tokenConsumer, ex, errorNotified);
            throw new IllegalStateException("DeepSeek 流式对话失败", ex);
        }
    }

    /**
     * 非流式对话方法。
     * <p>
     * 该方法用于不需要流式输出的场景，例如 Query 改写、Ragas 评估中需要等待完整回答后才能继续处理的判断逻辑。
     *
     * @param prompt 当前 Prompt
     * @return 完整回答文本
     */
    public String chat(String prompt) {
        String content = chatClient.prompt()
                .messages(buildMessages(prompt, List.of()))
                .options(buildOptions())
                .call()
                .content();
        return content == null ? "" : content;
    }

    private List<Message> buildMessages(String prompt, List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(safeText(properties.getSystemPrompt())));

        if (!CollectionUtils.isEmpty(history)) {
            for (ChatMessage message : history) {
                if (message == null) {
                    continue;
                }
                messages.add(toSpringAiMessage(message));
            }
        }

        messages.add(new UserMessage(safeText(prompt)));
        return messages;
    }

    private Message toSpringAiMessage(ChatMessage message) {
        String content = safeText(message.getContent());
        if (message.isUser()) {
            return new UserMessage(content);
        }
        if (message.isAssistant()) {
            return new AssistantMessage(content);
        }
        throw new IllegalArgumentException("不支持的对话角色：" + message.getRole());
    }

    private OpenAiChatOptions buildOptions() {
        return OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private void notifyStreamError(Consumer<String> tokenConsumer, Throwable ex, AtomicBoolean errorNotified) {
        if (!errorNotified.compareAndSet(false, true)) {
            return;
        }

        String message = ex == null || ex.getMessage() == null ? "unknown error" : ex.getMessage();
        try {
            tokenConsumer.accept(STREAM_ERROR_PREFIX + message);
        } catch (RuntimeException callbackEx) {
            log.warn("DeepSeek 流式异常通知回调失败", callbackEx);
        }
    }
}
