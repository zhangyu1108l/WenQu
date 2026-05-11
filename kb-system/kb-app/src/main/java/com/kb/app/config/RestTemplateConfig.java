package com.kb.app.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置类。
 * <p>
 * 提供全局 RestTemplate Bean，用于调用 Python 侧车等内部 HTTP 服务。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        /*
         * 连接超时和读取超时需要分开设置：
         * 连接超时用于快速发现服务未启动、端口不通等网络连接问题；
         * 读取超时用于等待服务处理结果。PDF 大文件或扫描件 OCR 可能超过 30 秒，
         * 因此读取超时设置为 120 秒，避免长耗时解析被过早中断。
         */
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }
}
