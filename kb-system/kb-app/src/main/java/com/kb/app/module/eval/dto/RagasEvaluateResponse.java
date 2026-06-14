package com.kb.app.module.eval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Ragas 侧车 /evaluate 的立即响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagasEvaluateResponse {

    private Boolean accepted;

    @JsonProperty("batch_id")
    private Long batchId;

    private String message;
}
