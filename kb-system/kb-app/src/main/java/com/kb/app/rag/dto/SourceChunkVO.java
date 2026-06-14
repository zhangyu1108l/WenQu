package com.kb.app.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 来源引用展示 VO，作为 SSE event:done 的 data 推送给前端。
 *
 * @author 问渠系统
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceChunkVO {

    /**
     * chunk ID，前端用于定位引用的原始片段。
     */
    private Long chunkId;

    /**
     * 文档ID，前端点击来源卡片时可跳转到文档详情页。
     */
    private Long documentId;

    /**
     * 原文段落内容，前端用于展示引用正文并做关键词高亮。
     */
    private String content;

    /**
     * 标题路径，前端用于展示章节位置。
     */
    private String headingPath;

    /**
     * 页码，PDF 文档用于跳转页；Word 文档可为 null。
     */
    private Integer pageNo;

    /**
     * 相关度得分，前端用于展示或排序来源可信度。
     */
    private float score;
}
