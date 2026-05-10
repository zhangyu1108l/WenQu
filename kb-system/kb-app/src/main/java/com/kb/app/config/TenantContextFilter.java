package com.kb.app.config;

import com.kb.app.context.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 租户上下文 Servlet Filter — 从请求头读取 X-Tenant-Id / X-User-Id 并写入 ThreadLocal。
 * <p>
 * Gateway 的 JwtAuthFilter 解析 JWT 后，将 tenantId 和 userId 写入请求头：
 * <ul>
 *     <li>X-Tenant-Id — 当前用户所属租户ID</li>
 *     <li>X-User-Id — 当前用户ID</li>
 * </ul>
 * <p>
 * 本 Filter 负责在请求入口将这两个值存入 {@link TenantContext}（ThreadLocal），
 * 供 MyBatis-Plus 租户拦截器和业务代码使用。
 * <p>
 * <b>为什么需要这个 Filter：</b>
 * <ul>
 *     <li>MyBatis-Plus 的 TenantLineInnerInterceptor 通过 TenantContext.getTenantId()
 *         获取当前租户ID，自动追加 AND tenant_id = ? 条件</li>
 *     <li>如果 TenantContext 为空，拦截器会追加 AND tenant_id = NULL，
 *         导致所有查询结果为空（如文档同名判断失效，每次上传都创建新文档而非新版本）</li>
 * </ul>
 * <p>
 * <b>必须在 finally 块中清理 ThreadLocal</b>，防止线程池复用导致跨租户数据泄露。
 *
 * @author kb-system
 */
@Slf4j
@Component
@Order(1)
public class TenantContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        try {
            // ① 从请求头读取 Gateway 注入的 X-Tenant-Id
            String tenantIdHeader = httpRequest.getHeader("X-Tenant-Id");
            if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
                TenantContext.setTenantId(Long.valueOf(tenantIdHeader));
            }

            // ② 从请求头读取 Gateway 注入的 X-User-Id
            String userIdHeader = httpRequest.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                TenantContext.setUserId(Long.valueOf(userIdHeader));
            }

            // ③ 继续执行后续 Filter 和 Controller
            chain.doFilter(request, response);
        } finally {
            // ④ 必须清理 ThreadLocal，防止线程复用导致跨租户数据泄露
            TenantContext.clear();
        }
    }
}
