package com.kb.app.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.document.entity.DocumentDO;
import org.apache.ibatis.annotations.Mapper;

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
}
