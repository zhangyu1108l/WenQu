package com.kb.app.module.eval.dto;

import lombok.Data;

/**
 * 内部 RAG 问答接口请求体。
 * <p>
 * 该接口仅供 Ragas 侧车在内网 Docker 网络中调用，不经过 Gateway。
 */
@Data
public class InternalRagRequest {

    private String question;

    private Long tenantId;
}
