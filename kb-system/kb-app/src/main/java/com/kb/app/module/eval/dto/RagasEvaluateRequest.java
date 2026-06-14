package com.kb.app.module.eval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Java 调用 Ragas 侧车 POST /evaluate 的请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagasEvaluateRequest {

    @JsonProperty("batch_id")
    private Long batchId;

    @JsonProperty("tenant_id")
    private Long tenantId;

    private List<CaseItem> cases;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseItem {

        @JsonProperty("case_id")
        private Long caseId;

        private String question;

        @JsonProperty("ground_truth")
        private String groundTruth;
    }
}
