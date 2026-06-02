package com.kb.app.module.eval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Ragas 服务回调 Java 端的请求体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalCallbackRequest {

    @JsonProperty("batch_id")
    private Long batchId;

    /**
     * DONE 或 FAILED。
     */
    private String status;

    private List<SingleResultDTO> results;

    @JsonProperty("avg_faithfulness")
    private Double avgFaithfulness;

    @JsonProperty("avg_answer_relevancy")
    private Double avgAnswerRelevancy;

    @JsonProperty("avg_context_recall")
    private Double avgContextRecall;

    @JsonProperty("avg_context_precision")
    private Double avgContextPrecision;

    private String error;
}
