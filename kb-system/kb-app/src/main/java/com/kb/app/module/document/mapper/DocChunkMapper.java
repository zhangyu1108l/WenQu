package com.kb.app.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.document.entity.DocChunkDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档分块表 Mapper — 对应数据库 doc_chunk 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * doc_chunk 表包含 tenant_id 字段，所有查询会被 {@code TenantLineInnerInterceptor}
 * 自动追加 AND tenant_id = ?，确保租户间数据隔离。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>Embedding：文档解析后批量 insert chunk 记录</li>
 *     <li>RAG 检索后：根据 chunk_id 查询原文内容，用于来源引用展示</li>
 *     <li>版本清理：按 version_id 批量删除旧版本的所有 chunk</li>
 *     <li>文档删除：按 document_id 删除所有 chunk，需先删 Milvus 向量</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface DocChunkMapper extends BaseMapper<DocChunkDO> {
}
