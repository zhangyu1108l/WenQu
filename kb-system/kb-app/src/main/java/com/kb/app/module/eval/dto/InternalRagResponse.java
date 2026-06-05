package com.kb.app.module.eval.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 内部 RAG 问答接口响应体。
 * <p>
 * contexts 是检索到的 chunk 原文列表，是 Ragas 计算指标的核心输入。
 */
@Data
@Builder
public class InternalRagResponse {

    /**
     * LLM 生成的完整回答。
     */
    private String modelAnswer;

    /**
     * 检索到的 chunk 原文列表。
     */
    private List<String> contexts;
}
