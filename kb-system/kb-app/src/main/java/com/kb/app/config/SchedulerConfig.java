package com.kb.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置。
 * <p>
 * @EnableScheduling 必须在 @Configuration 配置类上声明，否则 @Scheduled 注解不生效。
 *
 * @author kb-system
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
