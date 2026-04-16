package com.kb.app.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.document.entity.DocumentVersionDO;
import org.apache.ibatis.annotations.Mapper;

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
}
