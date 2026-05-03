package com.kb.app.module.document.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设置过期时间请求 DTO — 对应 PUT /api/docs/{id}/expire 的请求体。
 * <p>
 * expireAt 允许为 null：
 * <ul>
 *     <li>传入具体时间 → 设置过期时间，到期后文档标记为过期</li>
 *     <li>传入 null → 清除过期时间，文档永不过期</li>
 * </ul>
 *
 * @author kb-system
 */
@Data
public class ExpireRequest {

    /**
     * 过期时间。
     * <p>
     * 传 null 表示永不过期（清除之前设置的过期时间）。
     * 传具体 LocalDateTime 值表示到达该时间后文档过期。
     */
    private LocalDateTime expireAt;
}
