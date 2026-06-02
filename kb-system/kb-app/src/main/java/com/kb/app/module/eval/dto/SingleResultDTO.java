package com.kb.app.module.eval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 单个用例评估结果 DTO。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SingleResultDTO {

    @JsonProperty("case_id")
    private Long caseId;

    @JsonProperty("model_answer")
    private String modelAnswer;

    private Double faithfulness;

    @JsonProperty("answer_relevancy")
    private Double answerRelevancy;

    @JsonProperty("context_recall")
    private Double contextRecall;

    @JsonProperty("context_precision")
    private Double contextPrecision;

    private String error;
}
