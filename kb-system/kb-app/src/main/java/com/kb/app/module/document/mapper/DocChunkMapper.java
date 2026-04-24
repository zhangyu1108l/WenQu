package com.kb.app.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.document.entity.DocChunkDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 查询某版本下的所有 chunk。
     * <p>
     * 调用时机：删除文档版本时，需要先获取该版本下所有 chunk 的 milvus_id，
     * 然后通过 milvus_id 批量删除 Milvus 中的向量，最后再删除 doc_chunk 记录。
     * <p>
     * 此方法确保在执行删除前能先收集所有需要清理的向量 ID。
     *
     * @param versionId 版本ID，关联 document_version.id，不允许为 null
     * @return 该版本下的所有 chunk 列表，按 chunk_index 顺序
     */
    @Select("SELECT * FROM doc_chunk WHERE version_id = #{versionId} ORDER BY chunk_index")
    List<DocChunkDO> selectByVersionId(@Param("versionId") Long versionId);

    /**
     * 删除某版本下的所有 chunk 记录。
     * <p>
     * 调用时机：删除文档版本时，在 Milvus 向量已清理完成后调用。
     * 删除顺序必须为：① MinIO 文件 → ② Milvus 向量 → ③ doc_chunk 记录 → ④ document_version 记录。
     * 此方法对应第 ③ 步。
     *
     * @param versionId 版本ID，关联 document_version.id，不允许为 null
     * @return 删除的记录数
     */
    @Delete("DELETE FROM doc_chunk WHERE version_id = #{versionId}")
    int deleteByVersionId(@Param("versionId") Long versionId);
}
