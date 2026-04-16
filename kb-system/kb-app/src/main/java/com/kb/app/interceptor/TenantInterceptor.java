package com.kb.app.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.kb.app.context.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;

import java.util.Set;

/**
 * MyBatis-Plus 多租户拦截器 — 实现 {@link TenantLineHandler} 接口。
 * <p>
 * 自动为所有包含 tenant_id 字段的 SQL 追加 {@code AND tenant_id = ?} 条件，
 * 确保租户间数据隔离，业务代码无需手动拼接租户条件。
 * <p>
 * <b>需要租户隔离的表（自动追加条件）：</b>
 * <ul>
 *     <li>user          — 用户归属租户</li>
 *     <li>document      — 文档归属租户</li>
 *     <li>doc_chunk     — 文档分块归属租户（冗余字段加速查询）</li>
 *     <li>conversation  — 会话归属租户</li>
 *     <li>async_task    — 异步任务归属租户</li>
 *     <li>eval_case     — 评估用例归属租户</li>
 *     <li>eval_batch    — 评估批次归属租户</li>
 * </ul>
 * <p>
 * <b>不需要租户隔离的表（忽略列表）：</b>
 * <ul>
 *     <li>tenant             — 租户表本身没有 tenant_id 字段</li>
 *     <li>document_version   — 通过 document_id 关联 document 间接隔离</li>
 *     <li>message            — 通过 conversation_id 关联 conversation 间接隔离</li>
 *     <li>eval_result        — 通过 batch_id 关联 eval_batch 间接隔离</li>
 * </ul>
 * <p>
 * 忽略原因：这些表没有 tenant_id 列，如果拦截器对它们追加条件会导致 SQL 报错。
 * 它们的数据安全通过外键关联的父表（已有 tenant_id 隔离）来保证。
 *
 * @author kb-system
 */
public class TenantInterceptor implements TenantLineHandler {

    /**
     * 不需要租户隔离的表名集合。
     * <p>
     * 这些表没有 tenant_id 字段，拦截器必须跳过，否则会生成错误的 SQL：
     * {@code SELECT * FROM tenant WHERE tenant_id = ?}（tenant 表无此列）。
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "tenant",
            "document_version",
            "message",
            "eval_result"
    );

    /**
     * 获取当前租户ID，用于 MyBatis-Plus 自动追加 {@code AND tenant_id = ?} 条件。
     * <p>
     * 从 {@link TenantContext} 的 ThreadLocal 中读取，由 Servlet Filter 在请求入口写入。
     * <p>
     * 返回 {@link NullValue} 的场景：未鉴权的公开接口（如注册/登录），
     * 此时拦截器不会追加租户条件，允许跨租户操作（如注册时查询 tenant 表）。
     *
     * @return 当前租户ID的表达式；如果上下文为空则返回 NullValue
     */
    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    /**
     * 获取 tenant_id 列名。
     * <p>
     * 数据库字段统一使用 snake_case 命名，列名为 {@code tenant_id}。
     *
     * @return 列名字符串 "tenant_id"
     */
    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 判断指定表是否需要忽略租户隔离条件。
     * <p>
     * 返回 true 表示该表不需要追加 {@code AND tenant_id = ?}，
     * 返回 false 表示该表需要自动追加租户隔离条件。
     * <p>
     * 忽略规则：
     * <ul>
     *     <li>tenant — 租户表本身，没有 tenant_id 字段</li>
     *     <li>document_version — 通过 document_id 间接关联租户</li>
     *     <li>message — 通过 conversation_id 间接关联租户</li>
     *     <li>eval_result — 通过 batch_id 间接关联租户</li>
     * </ul>
     *
     * @param tableName 当前 SQL 操作的表名
     * @return true=忽略（不追加租户条件），false=不忽略（追加租户条件）
     */
    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }
}
