package com.kb.app.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.document.entity.DocumentVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档版本表 Mapper — 对应数据库 document_version 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * 注意：document_version 表不包含 tenant_id 字段，
 * 必须在 {@code TenantLineInnerInterceptor} 中将此表加入忽略列表，
 * 否则拦截器会错误地追加 AND tenant_id = ? 导致查询失败。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>上传：insert 新版本记录，设置 is_active = 1</li>
 *     <li>版本查询：按 document_id 查询历史版本列表（最多 5 个）</li>
 *     <li>版本清理：版本数 > 5 时，删除 version_no 最小的版本</li>
 *     <li>下载：查询激活版本的 minio_bucket + minio_key 生成预签名 URL</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersionDO> {

    /**
     * 查询某文档的当前激活版本（is_active=1）。
     * <p>
     * 调用时机：
     * <ul>
     *     <li>文档下载时，获取激活版本的 minio_bucket + minio_key 生成预签名 URL</li>
     *     <li>文档详情接口中，返回当前激活版本信息</li>
     * </ul>
     * <p>
     * 每个文档同时只有一个激活版本，因此结果最多一条。
     *
     * @param documentId 文档ID，不允许为 null
     * @return 激活版本实体，不存在时返回 null
     */
    @Select("SELECT * FROM document_version WHERE document_id = #{documentId} AND is_active = 1 LIMIT 1")
    DocumentVersionDO selectActiveVersion(@Param("documentId") Long documentId);

    /**
     * 查询某文档的所有版本，按 version_no 降序排列（最新版本在前）。
     * <p>
     * 调用时机：文档版本历史接口 GET /api/docs/{id}/versions，
     * 返回该文档所有版本（最多 5 个）。
     *
     * @param documentId 文档ID，不允许为 null
     * @return 版本列表，按 version_no 降序排列
     */
    @Select("SELECT * FROM document_version WHERE document_id = #{documentId} ORDER BY version_no DESC")
    List<DocumentVersionDO> selectVersionList(@Param("documentId") Long documentId);

    /**
     * 统计某文档的版本数量。
     * <p>
     * 调用时机：新版本上传后，判断版本数是否超过 5 个。
     * 若超过，则删除 version_no 最小的旧版本（顺序：MinIO → Milvus → doc_chunk → document_version）。
     *
     * @param documentId 文档ID，不允许为 null
     * @return 版本数量
     */
    @Select("SELECT COUNT(*) FROM document_version WHERE document_id = #{documentId}")
    int countByDocumentId(@Param("documentId") Long documentId);
}
