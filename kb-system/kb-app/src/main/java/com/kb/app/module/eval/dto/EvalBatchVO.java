package com.kb.app.module.eval.dto;

import com.kb.app.module.eval.entity.EvalBatchDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EvalBatchVO {

    private Long id;

    private Long tenantId;

    private Integer caseCount;

    private String status;

    private Float avgFaithfulness;

    private Float avgAnswerRelevancy;

    private Float avgContextRecall;

    private Float avgContextPrecision;

    private LocalDateTime createdAt;

    public static EvalBatchVO from(EvalBatchDO batch) {
        if (batch == null) {
            return null;
        }
        return EvalBatchVO.builder()
                .id(batch.getId())
                .tenantId(batch.getTenantId())
                .caseCount(batch.getCaseCount())
                .status(batch.getStatus())
                .avgFaithfulness(batch.getAvgFaithfulness())
                .avgAnswerRelevancy(batch.getAvgAnswerRelevancy())
                .avgContextRecall(batch.getAvgContextRecall())
                .avgContextPrecision(batch.getAvgContextPrecision())
                .createdAt(batch.getCreatedAt())
                .build();
    }
}
