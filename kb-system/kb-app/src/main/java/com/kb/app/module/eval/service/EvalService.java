package com.kb.app.module.eval.service;

import com.kb.app.module.eval.dto.EvalBatchDetailVO;
import com.kb.app.module.eval.dto.EvalBatchVO;
import com.kb.app.module.eval.dto.EvalCaseRequest;
import com.kb.app.module.eval.dto.EvalCaseVO;
import com.kb.app.module.eval.dto.EvalRunResponse;

import java.util.List;

public interface EvalService {

    List<EvalCaseVO> listCases(Integer operatorRole);

    EvalCaseVO createCase(EvalCaseRequest request);

    void deleteCase(Long id, Integer operatorRole);

    List<EvalBatchVO> listBatches(Integer operatorRole);

    EvalBatchDetailVO getBatchDetail(Long id, Integer operatorRole);

    EvalRunResponse runEval();
}
