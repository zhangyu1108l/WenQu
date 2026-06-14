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
 * 消息实体类 — 对应数据库 message 表。
 * <p>
 * 存储会话内的完整对话历史（永久保存到 MySQL）。
 * Redis 只缓存最近 5 轮（10 条）用于 RAG 上下文构建，过期后从此表重建。
 * <p>
 * role 字段区分消息角色：
 * <ul>
 *     <li>0 = 用户提问</li>
 *     <li>1 = AI 回答</li>
 * </ul>
 * <p>
 * sourceChunks：AI 回答时引用的原文 chunk 列表，JSON 字符串格式：
 * [{"chunkId":1,"content":"...","headingPath":"第5章>5.3节","score":0.92}]
 * 前端根据此字段渲染来源引用卡片和段落高亮。用户消息此字段为 NULL。
 * <p>
 * 注意：message 表不包含 tenant_id 字段，
 * 需要在租户拦截器中将此表加入忽略列表。
 *
 * @author 问渠系统
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("message")
public class MessageDO {

    /**
     * 消息主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话ID，关联 conversation.id
     */
    private Long conversationId;

    /**
     * 消息角色：0=用户提问  1=AI回答
     */
    private Integer role;

    /**
     * 消息正文内容
     */
    private String content;

    /**
     * AI回答时引用的原文chunk列表，JSON字符串格式：
     * [{"chunkId":1,"content":"...","headingPath":"第5章>5.3节","score":0.92}]
     * <p>
     * 这里使用 String 而不是 Object：MyBatis-Plus 对 JSON 字段的处理需要额外 TypeHandler 配置，
     * 直接存 JSON 字符串最简单可靠，也便于 SSE event:done 与历史消息响应复用同一份来源数据。
     * 用户消息此字段为 NULL。
     */
    private String sourceChunks;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
