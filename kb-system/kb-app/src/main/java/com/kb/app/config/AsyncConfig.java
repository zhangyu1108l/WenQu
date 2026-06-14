package com.kb.app.config;

import com.kb.app.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置类 — 为 @Async 注解提供自定义线程池。
 * <p>
 * <b>用途：</b>
 * <ul>
 *     <li>docProcessPool — 文档处理管道（解析 → Embedding → Milvus 写入）</li>
 *     <li>后续可扩展更多线程池（如 Ragas 评估专用）</li>
 * </ul>
 * <p>
 * <b>线程池参数来源：</b>application-dev.yml 的 async.thread-pool 配置节。
 * <p>
 * <b>拒绝策略：</b>CallerRunsPolicy — 当线程池和队列都满时，
 * 由调用方线程直接执行任务，避免任务被丢弃。
 *
 * @author 问渠系统
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.thread-pool.core-size:4}")
    private int coreSize;

    @Value("${async.thread-pool.max-size:16}")
    private int maxSize;

    @Value("${async.thread-pool.queue-capacity:100}")
    private int queueCapacity;

    @Value("${async.thread-pool.thread-name-prefix:kb-async-}")
    private String threadNamePrefix;

    /**
     * 文档处理线程池。
     * <p>
     * 被 {@code @Async("docProcessPool")} 引用，
     * 执行文档上传后的异步处理管道（MinIO 存储 → Python 解析 → Embedding → Milvus）。
     *
     * @return 配置好的线程池执行器
     */
    @Bean("docProcessPool")
    public Executor docProcessPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setTaskDecorator(tenantContextTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("文档处理线程池已初始化：核心线程数={}，最大线程数={}，队列容量={}",
                coreSize, maxSize, queueCapacity);
        return executor;
    }

    private TaskDecorator tenantContextTaskDecorator() {
        return runnable -> {
            Long capturedTenantId = TenantContext.getTenantId();
            Long capturedUserId = TenantContext.getUserId();
            return () -> {
                Long previousTenantId = TenantContext.getTenantId();
                Long previousUserId = TenantContext.getUserId();
                try {
                    TenantContext.clear();
                    if (capturedTenantId != null) {
                        TenantContext.setTenantId(capturedTenantId);
                    }
                    if (capturedUserId != null) {
                        TenantContext.setUserId(capturedUserId);
                    }
                    runnable.run();
                } finally {
                    TenantContext.clear();
                    if (previousTenantId != null) {
                        TenantContext.setTenantId(previousTenantId);
                    }
                    if (previousUserId != null) {
                        TenantContext.setUserId(previousUserId);
                    }
                }
            };
        };
    }
}
