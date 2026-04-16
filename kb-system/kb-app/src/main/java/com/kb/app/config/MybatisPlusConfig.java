package com.kb.app.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.kb.app.interceptor.TenantInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类 — 注册插件拦截器。
 * <p>
 * 注册了两个核心拦截器（按注册顺序执行）：
 * <ol>
 *     <li><b>TenantLineInnerInterceptor</b> — 多租户拦截器，自动为 SQL 追加
 *         {@code AND tenant_id = ?} 条件，确保租户间数据隔离。
 *         忽略表列表在 {@link TenantInterceptor} 中定义。</li>
 *     <li><b>PaginationInnerInterceptor</b> — 分页拦截器，支持 MyBatis-Plus
 *         {@code Page} 对象自动拼接 LIMIT 语句，无需手写分页 SQL。</li>
 * </ol>
 * <p>
 * 拦截器执行顺序非常重要：租户拦截器必须在分页拦截器之前，
 * 否则分页 COUNT 查询不会带上租户条件，导致总数不准确。
 * <p>
 * 后续按 Step 顺序还会在此配置类中添加其他拦截器，
 * 如乐观锁拦截器（如需要）。
 *
 * @author kb-system
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器插件 Bean。
     * <p>
     * 将租户拦截器和分页拦截器按顺序注册到插件链中。
     * MyBatis-Plus 拦截器是责任链模式，按注册顺序依次执行。
     *
     * @param tenantInterceptor 租户拦截器，由 Spring 容器注入
     * @return 配置好的 MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantInterceptor tenantInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 租户拦截器：自动追加 AND tenant_id = ?，必须在分页之前
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantInterceptor));

        // 2. 分页拦截器：支持 Page 对象自动分页，数据库类型 MySQL 8.0
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }

    /**
     * 租户拦截器 Bean。
     * <p>
     * 单独注册为 Bean，方便后续扩展（如动态调整忽略表列表），
     * 也使得单元测试可以单独 Mock 此拦截器。
     *
     * @return TenantInterceptor 实例
     */
    @Bean
    public TenantInterceptor tenantInterceptor() {
        return new TenantInterceptor();
    }
}
