package com.kb.app.scheduler;

import com.kb.app.module.task.service.AsyncTaskService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 应用启动自检组件。
 * <p>
 * ApplicationRunner 的执行时机是在 Spring 容器全部初始化完成后，比 @PostConstruct 更晚；
 * 此时所有 Bean 和数据库连接都已就绪，适合执行启动恢复和轻量连通性检查。
 *
 * @author 问渠系统
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppStartupRunner implements ApplicationRunner {

    private static final String HEALTH_CHECK_COLLECTION = "startup_health_check";
    private static final String REDIS_HEALTH_CHECK_KEY = "startup:health-check";

    private final AsyncTaskService asyncTaskService;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final ObjectProvider<MilvusServiceClient> milvusServiceClientProvider;
    private final ObjectProvider<MinioClient> minioClientProvider;

    @Override
    public void run(ApplicationArguments args) {
        // 服务重启、OOM、强制 Kill 等情况下，@Async 异步线程会突然中断，
        // async_task 中遗留的 RUNNING 任务永远不会完成；必须在启动时清理，否则用户看到进度条会永远转圈。
        asyncTaskService.markStaleTasksFailed();

        log.info("服务启动完成，已清理遗留异常任务");

        // 连通性检查失败不应阻止启动：中间件可能短暂不可用，服务需要先启动并等待连接恢复。
        checkMysql();
        checkRedis();
        checkMilvus();
        checkMinio();
    }

    private void checkMysql() {
        runHealthCheck("MySQL", () -> {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                throw new IllegalStateException("未找到 DataSource Bean");
            }
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(2)) {
                    throw new IllegalStateException("数据库连接不可用");
                }
            }
        });
    }

    private void checkRedis() {
        runHealthCheck("Redis", () -> {
            StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
            if (stringRedisTemplate == null) {
                throw new IllegalStateException("未找到 StringRedisTemplate Bean");
            }
            stringRedisTemplate.hasKey(REDIS_HEALTH_CHECK_KEY);
        });
    }

    private void checkMilvus() {
        runHealthCheck("Milvus", () -> {
            MilvusServiceClient milvusServiceClient = milvusServiceClientProvider.getIfAvailable();
            if (milvusServiceClient == null) {
                throw new IllegalStateException("未找到 MilvusServiceClient Bean");
            }

            HasCollectionParam param = HasCollectionParam.newBuilder()
                    .withCollectionName(HEALTH_CHECK_COLLECTION)
                    .build();
            R<Boolean> response = milvusServiceClient.hasCollection(param);
            if (response == null || response.getStatus() != R.Status.Success.getCode()) {
                String message = response == null ? "响应为空" : response.getMessage();
                throw new IllegalStateException(message);
            }
        });
    }

    private void checkMinio() {
        runHealthCheck("MinIO", () -> {
            MinioClient minioClient = minioClientProvider.getIfAvailable();
            if (minioClient == null) {
                throw new IllegalStateException("未找到 MinioClient Bean");
            }
            minioClient.listBuckets();
        });
    }

    private void runHealthCheck(String name, HealthCheck healthCheck) {
        try {
            healthCheck.check();
            log.info("{} 连通性检查通过", name);
        } catch (Exception e) {
            log.warn("{} 连通性检查失败，服务将继续启动并等待连接恢复：{}", name, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface HealthCheck {

        void check() throws Exception;
    }
}
