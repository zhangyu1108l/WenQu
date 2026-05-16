package com.kb.app.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class SpringAiConfig {

    private static final int ZHIPU_EMBEDDING_DIMENSION = 2048;

    @Bean
    @Primary
    public EmbeddingModel zhipuEmbeddingModel(EmbeddingProperties properties) {
        if (properties.getDimensions() == null
                || properties.getDimensions() != ZHIPU_EMBEDDING_DIMENSION) {
            throw new IllegalStateException("Zhipu embedding-3 dimension must be 2048");
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .embeddingsPath(properties.getEmbeddingsPath())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getModel())
                .dimensions(properties.getDimensions())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
