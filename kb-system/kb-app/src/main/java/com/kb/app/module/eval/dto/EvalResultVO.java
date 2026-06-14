package com.kb.app.module.eval.dto;

import com.kb.app.module.eval.entity.EvalCaseDO;
import com.kb.app.module.eval.entity.EvalResultDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EvalResultVO {

    private Long id;

    private Long batchId;

    private Long evalCaseId;

    private String question;

    private String groundTruth;

    private String modelAnswer;

    private Float faithfulness;

    private Float answerRelevancy;

    private Float contextRecall;

    private Float contextPrecision;

    private LocalDateTime createdAt;

    public static EvalResultVO from(EvalResultDO result, EvalCaseDO evalCase) {
        if (result == null) {
            return null;
        }
        return EvalResultVO.builder()
                .id(result.getId())
                .batchId(result.getBatchId())
                .evalCaseId(result.getEvalCaseId())
                .question(evalCase == null ? null : evalCase.getQuestion())
                .groundTruth(evalCase == null ? null : evalCase.getGroundTruth())
                .modelAnswer(result.getModelAnswer())
                .faithfulness(result.getFaithfulness())
                .answerRelevancy(result.getAnswerRelevancy())
                .contextRecall(result.getContextRecall())
                .contextPrecision(result.getContextPrecision())
                .createdAt(result.getCreatedAt())
                .build();
    }
}
