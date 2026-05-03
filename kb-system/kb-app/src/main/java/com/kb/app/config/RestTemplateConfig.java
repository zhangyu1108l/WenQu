package com.kb.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类。
 * <p>
 * 提供全局 RestTemplate Bean，用于调用 Python 侧车等内部 HTTP 服务。
 * <p>
 * 使用场景：
 * <ul>
 *     <li>ParserClient — 调用文档解析侧车 POST /parse</li>
 *     <li>Ragas 评估回调等后续扩展</li>
 * </ul>
 *
 * @author kb-system
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
