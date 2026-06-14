package com.kb.app.module.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/eval/run 响应体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalRunResponse {

    private Long batchId;

    private Long taskId;
}
