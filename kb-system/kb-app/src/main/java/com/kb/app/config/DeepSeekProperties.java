package com.kb.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek Chat 配置属性。
 * <p>
 * 该类只读取业务侧对话参数，底层 HTTP 接入仍复用 Spring AI 的 OpenAI 兼容配置。
 *
 * @author kb-system
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.ai.deepseek.chat")
public class DeepSeekProperties {

    private static final String FIXED_MODEL = "deepseek-chat";

    /**
     * DeepSeek 对话模型名，固定使用 deepseek-chat。
     */
    private String model = FIXED_MODEL;

    /**
     * 温度参数，控制模型输出的随机性。
     * <p>
     * 0 表示更确定性的输出，1 表示更有创造性的输出。
     * 知识库问答场景优先保证事实准确性，因此默认使用低温度。
     */
    private double temperature = 0.3D;

    /**
     * 最大输出 token 数。
     */
    private int maxTokens = 2048;

    /**
     * 全局系统提示词，从配置文件读取。
     */
    private String systemPrompt;

    /**
     * 模型名由架构约束固定，外部配置传入其他值时不覆盖。
     *
     * @param model 外部配置中的模型名
     */
    public void setModel(String model) {
        this.model = FIXED_MODEL;
    }
}
