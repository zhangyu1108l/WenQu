package com.kb.app.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.chat.entity.MessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息表 Mapper — 对应数据库 message 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * 注意：message 表不包含 tenant_id 字段，
 * 必须在 {@code TenantLineInnerInterceptor} 中将此表加入忽略列表，
 * 否则拦截器会错误地追加 AND tenant_id = ? 导致查询失败。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>RAG 问答完成后：异步写入用户消息和 AI 回答（含 source_chunks JSON）</li>
 *     <li>冷启动：Redis 对话缓存过期后，从此表查最近 5 轮重建</li>
 *     <li>历史记录：按 conversation_id 分页查询完整对话历史</li>
 *     <li>删除会话：级联删除该会话下的所有消息</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageDO> {
}
