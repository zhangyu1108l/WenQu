package com.kb.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * kb-app 主业务模块启动类。
 * <p>
 * 职责：用户认证、文档管理、对话问答、系统管理、评估模块、异步任务、RAG 引擎。
 * 运行端口：8081（Docker 内部），由 Gateway（8080）路由转发。
 *
 * @author kb-system
 */
@SpringBootApplication
public class KbAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbAppApplication.class, args);
    }
}
