package com.kb.app.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.chat.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话表 Mapper — 对应数据库 conversation 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * conversation 表包含 tenant_id 字段，所有查询会被 {@code TenantLineInnerInterceptor}
 * 自动追加 AND tenant_id = ?，确保租户间数据隔离。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>新建对话：insert 一条默认标题"新对话"的记录</li>
 *     <li>对话列表：按 user_id + tenant_id 分页查询</li>
 *     <li>标题更新：首条消息后截取前 20 字更新 title</li>
 *     <li>删除对话：删除会话及关联的 message 记录</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {
}
