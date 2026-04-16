package com.kb.app.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体类 — 对应数据库 conversation 表。
 * <p>
 * 每个会话是独立的对话上下文，会话之间历史不互通。
 * <p>
 * title 默认为"新对话"，首条问题发出后自动截取前 20 字更新。
 * <p>
 * conversation 表包含 tenant_id 字段，所有查询会被租户拦截器自动追加租户隔离条件。
 * <p>
 * Redis 缓存策略：
 * <ul>
 *     <li>Key 格式：conv:{conversationId}:history</li>
 *     <li>数据结构：Redis List（RPUSH 追加，LTRIM 保留最新 10 条）</li>
 *     <li>TTL：24 小时，每次问答 EXPIRE 续期</li>
 *     <li>冷启动：Redis 过期后从 MySQL message 表重建最近 5 轮</li>
 * </ul>
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversation")
public class ConversationDO {

    /**
     * 会话主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建该会话的用户ID，关联 user.id
     */
    private Long userId;

    /**
     * 所属租户ID，由租户拦截器自动注入查询条件
     */
    private Long tenantId;

    /**
     * 会话标题，默认"新对话"，首条问题发送后自动截取前 20 字更新
     */
    private String title;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
