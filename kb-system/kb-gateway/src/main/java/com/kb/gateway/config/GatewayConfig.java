package com.kb.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 配置类 — 启用配置属性绑定 + 定义路由规则。
 * <p>
 * 路由规则：
 * <ul>
 *     <li>/api/** 全部转发到 kb-app 主业务服务（http://app:8081）</li>
 * </ul>
 * <p>
 * 注意：JwtAuthFilter 实现了 GlobalFilter 并标注 @Component，
 * 由 Spring 容器自动注册到过滤器链中，无需在此手动注册。
 *
 * @author kb-system
 */
@Configuration
@EnableConfigurationProperties(WhiteListProperties.class)
public class GatewayConfig {

    /**
     * 定义 Gateway 路由规则。
     * <p>
     * 所有 /api/** 路径的请求转发到 kb-app 服务。
     * Docker 环境中通过服务名 app 互访（http://app:8081），
     * 本地开发时在 application-dev.yml 中覆盖为 localhost:8082。
     *
     * @param builder 路由构建器
     * @return 路由定位器
     */
    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // /api/** → kb-app 主业务服务
                .route("kb-app-route", r -> r
                        .path("/api/**")
                        .uri("${gateway.app-service-url:http://app:8081}"))
                .build();
    }
}
