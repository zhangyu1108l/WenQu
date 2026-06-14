package com.kb.app.module.eval.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EvalBatchDetailVO {

    private EvalBatchVO batch;

    private List<EvalResultVO> results;
}
