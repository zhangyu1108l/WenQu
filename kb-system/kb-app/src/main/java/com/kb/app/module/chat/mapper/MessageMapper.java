package com.kb.app.module.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.chat.entity.MessageDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 查询某个会话的完整消息历史，按创建时间升序返回。
     * <p>
     * 用于前端打开会话详情时展示完整上下文；完整历史必须从 MySQL 读取，
     * 不能依赖 Redis 最近 5 轮滑动窗口。
     *
     * @param conversationId 会话ID
     * @return 该会话下所有消息，按时间升序排列
     */
    @Select("""
            SELECT *
            FROM message
            WHERE conversation_id = #{conversationId}
            ORDER BY created_at ASC, id ASC
            """)
    List<MessageDO> selectByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 查询某个会话最近 N 条消息，按创建时间降序返回。
     * <p>
     * 用于 Redis 冷启动重建最近 5 轮历史：先按降序 LIMIT 取最新消息，
     * 调用方再反转为升序后写入 Redis。
     *
     * @param conversationId 会话ID
     * @param limit          最近消息数量
     * @return 最近 N 条消息，按时间降序排列
     */
    @Select("""
            SELECT *
            FROM message
            WHERE conversation_id = #{conversationId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<MessageDO> selectRecentByConversationId(@Param("conversationId") Long conversationId,
                                                 @Param("limit") int limit);

    /**
     * 删除某会话下的所有消息。
     * <p>
     * 删除会话时由会话 Service 级联调用，必须先删 message 子记录，再删 conversation 父记录。
     *
     * @param conversationId 会话ID
     * @return 删除的消息数量
     */
    @Delete("DELETE FROM message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") Long conversationId);
}
