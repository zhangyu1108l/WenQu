package com.kb.app.context;

/**
 * 租户上下文 — 基于 ThreadLocal 存储当前请求的 tenantId 和 userId。
 * <p>
 * 为什么用 ThreadLocal：
 * <ul>
 *     <li>每个请求在独立 Servlet 线程中处理，ThreadLocal 天然实现请求级隔离</li>
 *     <li>业务代码通过静态方法 {@code TenantContext.getTenantId()} 获取租户ID，
 *         不需要在方法参数中逐层传递，避免污染 Service/DAO 接口签名</li>
 *     <li>架构文档明确规定："TenantContext.getTenantId() 从 ThreadLocal 获取，
 *         不允许从参数传入"</li>
 * </ul>
 * <p>
 * 为什么必须手动清理：
 * <ul>
 *     <li>Tomcat / Undertow 使用线程池，线程会被复用处理下一个请求</li>
 *     <li>如果不调用 {@link #clear()}，下一个请求会读到上一个请求的租户ID，
 *         导致跨租户数据泄露，这是极其严重的安全事故</li>
 *     <li>@Async 异步线程同样复用线程池，必须在异步方法执行前后也做清理</li>
 * </ul>
 * <p>
 * 数据写入时机：
 * <ul>
 *     <li>Gateway 过滤器解析 JWT 后，通过 HTTP Header 传入 X-Tenant-Id / X-User-Id</li>
 *     <li>主服务的 Servlet Filter 读取 Header 调用 {@link #setTenantId(Long)} 写入</li>
 *     <li>请求结束后在 Filter 的 finally 块中调用 {@link #clear()} 清理</li>
 * </ul>
 *
 * @author kb-system
 */
public class TenantContext {

    /**
     * 私有构造方法，防止实例化。
     * TenantContext 是纯静态工具类，所有方法均为 static，不应被 new 出来。
     */
    private TenantContext() {
    }

    /**
     * 当前请求的租户ID
     */
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    /**
     * 当前请求的用户ID
     */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 设置当前租户ID。
     * <p>
     * 由 Servlet Filter 在请求入口处调用，从 HTTP Header X-Tenant-Id 读取后写入。
     *
     * @param tenantId 租户ID，不允许为 null
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前租户ID。
     * <p>
     * 业务代码和 MyBatis-Plus 租户拦截器均通过此方法获取租户ID。
     * <p>
     * 注意：如果返回 null，说明上下文未初始化（通常是因为接口不需要鉴权，
     * 如 /api/auth/register 和 /api/auth/login），此时租户拦截器不会追加条件。
     *
     * @return 当前租户ID，可能为 null
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 设置当前用户ID。
     * <p>
     * 由 Servlet Filter 在请求入口处调用，从 HTTP Header X-User-Id 读取后写入。
     *
     * @param userId 用户ID，不允许为 null
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前用户ID。
     * <p>
     * 用于业务代码中获取当前操作人（如文档上传记录 uploader_id）。
     *
     * @return 当前用户ID，可能为 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 清理当前线程的上下文。
     * <p>
     * <b>必须在每个请求结束后调用（在 finally 块中）</b>，
     * 否则线程池复用会导致下一个请求读到脏数据，引发跨租户数据泄露。
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
