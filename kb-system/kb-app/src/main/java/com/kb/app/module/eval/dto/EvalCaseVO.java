package com.kb.app.module.eval.dto;

import com.kb.app.module.eval.entity.EvalCaseDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EvalCaseVO {

    private Long id;

    private Long tenantId;

    private String question;

    private String groundTruth;

    private LocalDateTime createdAt;

    public static EvalCaseVO from(EvalCaseDO evalCase) {
        if (evalCase == null) {
            return null;
        }
        return EvalCaseVO.builder()
                .id(evalCase.getId())
                .tenantId(evalCase.getTenantId())
                .question(evalCase.getQuestion())
                .groundTruth(evalCase.getGroundTruth())
                .createdAt(evalCase.getCreatedAt())
                .build();
    }
}
