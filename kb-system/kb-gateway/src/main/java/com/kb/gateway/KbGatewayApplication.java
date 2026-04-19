package com.kb.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * kb-gateway 网关模块启动类。
 * <p>
 * 基于 Spring Cloud Gateway（WebFlux + Netty），不是传统的 Servlet 容器。
 * 职责：JWT 校验、Redis 黑名单检查、请求头注入（X-User-Id / X-Tenant-Id）、路由转发。
 * 运行端口：8080（Docker 内部），由 Nginx 反向代理对外暴露。
 *
 * @author kb-system
 */
@SpringBootApplication
public class KbGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbGatewayApplication.class, args);
    }
}
