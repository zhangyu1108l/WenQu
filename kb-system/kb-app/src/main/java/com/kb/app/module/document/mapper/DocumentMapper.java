package com.kb.app.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.document.entity.DocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 逻辑文档表 Mapper — 对应数据库 document 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * document 表包含 tenant_id 字段，所有查询会被 {@code TenantLineInnerInterceptor}
 * 自动追加 AND tenant_id = ?，确保租户间数据隔离。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>上传：insert 新文档记录，或查询同名文档判断是否为新版本</li>
 *     <li>列表：按租户分页查询文档（支持关键词搜索）</li>
 *     <li>状态更新：异步任务中更新 status 字段（PENDING → PARSING → EMBEDDING → READY）</li>
 *     <li>删除：删除文档时需同步清理 MinIO + Milvus + doc_chunk + document_version</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentDO> {

    /**
     * 根据租户ID和文档标题查询文档。
     * <p>
     * 调用时机：文档上传时，判断同名文档是否已存在。
     * 若存在，则视为同一文档的新版本（version_no 递增）；若不存在，则创建新文档。
     * <p>
     * 虽然租户拦截器会自动追加 tenant_id 条件，
     * 但此处显式写入 SQL 使同名判断意图更清晰。
     *
     * @param tenantId 租户ID，不允许为 null
     * @param title    文档标题（文件名去掉扩展名），不允许为 null
     * @return 匹配的文档实体，不存在时返回 null
     */
    @Select("SELECT * FROM document WHERE tenant_id = #{tenantId} AND title = #{title} LIMIT 1")
    DocumentDO selectByTenantIdAndTitle(@Param("tenantId") Long tenantId,
                                       @Param("title") String title);

    /**
     * 更新文档处理状态。
     * <p>
     * 调用时机：异步文档处理流程中频繁调用，驱动状态机流转：
     * PENDING → PARSING → EMBEDDING → READY（或 FAILED）。
     * <p>
     * 使用独立 UPDATE 方法而非 MyBatis-Plus 的 updateById，
     * 避免每次只更新 status 却需要构建完整实体对象。
     *
     * @param id     文档ID，不允许为 null
     * @param status 目标状态值（PENDING/PARSING/EMBEDDING/READY/FAILED）
     */
    @Update("UPDATE document SET status = #{status} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
