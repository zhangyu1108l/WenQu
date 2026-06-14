package com.kb.app.module.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.app.context.TenantContext;
import com.kb.app.module.eval.dto.EvalBatchDetailVO;
import com.kb.app.module.eval.dto.EvalBatchVO;
import com.kb.app.module.eval.dto.EvalCaseRequest;
import com.kb.app.module.eval.dto.EvalCaseVO;
import com.kb.app.module.eval.dto.EvalResultVO;
import com.kb.app.module.eval.dto.EvalRunResponse;
import com.kb.app.module.eval.dto.RagasEvaluateRequest;
import com.kb.app.module.eval.dto.RagasEvaluateResponse;
import com.kb.app.module.eval.entity.EvalBatchDO;
import com.kb.app.module.eval.entity.EvalCaseDO;
import com.kb.app.module.eval.entity.EvalResultDO;
import com.kb.app.module.eval.mapper.EvalBatchMapper;
import com.kb.app.module.eval.mapper.EvalCaseMapper;
import com.kb.app.module.eval.mapper.EvalResultMapper;
import com.kb.app.module.eval.service.EvalService;
import com.kb.app.module.task.entity.AsyncTaskDO;
import com.kb.app.module.task.service.AsyncTaskService;
import com.kb.common.enums.TaskStatus;
import com.kb.common.enums.TaskType;
import com.kb.common.enums.UserRole;
import com.kb.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
public class EvalServiceImpl implements EvalService {

    private final EvalCaseMapper evalCaseMapper;
    private final EvalBatchMapper evalBatchMapper;
    private final EvalResultMapper evalResultMapper;
    private final AsyncTaskService asyncTaskService;
    private final RestClient ragasRestClient;
    private final String evalCallbackUrl;

    public EvalServiceImpl(EvalCaseMapper evalCaseMapper,
                           EvalBatchMapper evalBatchMapper,
                           EvalResultMapper evalResultMapper,
                           AsyncTaskService asyncTaskService,
                           @Qualifier("ragasRestClient") RestClient ragasRestClient,
                           @Value("${sidecar.eval-callback-url}")
                           String evalCallbackUrl) {
        this.evalCaseMapper = evalCaseMapper;
        this.evalBatchMapper = evalBatchMapper;
        this.evalResultMapper = evalResultMapper;
        this.asyncTaskService = asyncTaskService;
        this.ragasRestClient = ragasRestClient;
        this.evalCallbackUrl = evalCallbackUrl;
    }

    @Override
    public List<EvalCaseVO> listCases(Integer operatorRole) {
        List<EvalCaseDO> cases = isSuperAdmin(operatorRole)
                ? evalCaseMapper.selectAllIgnoreTenant()
                : evalCaseMapper.selectList(new LambdaQueryWrapper<EvalCaseDO>()
                .orderByDesc(EvalCaseDO::getCreatedAt)
                .orderByDesc(EvalCaseDO::getId));
        return cases.stream()
                .map(EvalCaseVO::from)
                .toList();
    }

    @Override
    public EvalCaseVO createCase(EvalCaseRequest request) {
        validateCaseRequest(request);
        Long tenantId = requireTenantId();

        EvalCaseDO evalCase = EvalCaseDO.builder()
                .tenantId(tenantId)
                .question(request.getQuestion().trim())
                .groundTruth(request.getGroundTruth().trim())
                .build();
        evalCaseMapper.insert(evalCase);
        return EvalCaseVO.from(evalCase);
    }

    @Override
    public void deleteCase(Long id, Integer operatorRole) {
        if (id == null) {
            throw BusinessException.of(400, "caseId 不能为空");
        }
        int deleted = isSuperAdmin(operatorRole)
                ? evalCaseMapper.deleteByIdIgnoreTenant(id)
                : evalCaseMapper.deleteById(id);
        if (deleted == 0) {
            throw BusinessException.of(8101, "评估用例不存在");
        }
    }

    @Override
    public List<EvalBatchVO> listBatches(Integer operatorRole) {
        List<EvalBatchDO> batches = isSuperAdmin(operatorRole)
                ? evalBatchMapper.selectAllIgnoreTenant()
                : evalBatchMapper.selectList(new LambdaQueryWrapper<EvalBatchDO>()
                .orderByDesc(EvalBatchDO::getCreatedAt)
                .orderByDesc(EvalBatchDO::getId));
        return batches.stream()
                .map(EvalBatchVO::from)
                .toList();
    }

    @Override
    public EvalBatchDetailVO getBatchDetail(Long id, Integer operatorRole) {
        if (id == null) {
            throw BusinessException.of(400, "batchId 不能为空");
        }
        boolean superAdmin = isSuperAdmin(operatorRole);
        EvalBatchDO batch = superAdmin
                ? evalBatchMapper.selectByIdIgnoreTenant(id)
                : evalBatchMapper.selectById(id);
        if (batch == null) {
            throw BusinessException.of(8102, "评估批次不存在");
        }

        List<EvalResultVO> results = evalResultMapper.selectList(
                        new LambdaQueryWrapper<EvalResultDO>()
                                .eq(EvalResultDO::getBatchId, id)
                                .orderByAsc(EvalResultDO::getId))
                .stream()
                .map(result -> toResultVO(result, superAdmin))
                .toList();
        return EvalBatchDetailVO.builder()
                .batch(EvalBatchVO.from(batch))
                .results(results)
                .build();
    }

    @Override
    public EvalRunResponse runEval() {
        Long tenantId = requireTenantId();
        String callbackUrl = requireCallbackUrl();
        List<EvalCaseDO> cases = evalCaseMapper.selectList(new LambdaQueryWrapper<EvalCaseDO>()
                .orderByAsc(EvalCaseDO::getId));
        if (cases.isEmpty()) {
            throw BusinessException.of(8103, "暂无评估用例，请先新增用例");
        }

        EvalBatchDO batch = EvalBatchDO.builder()
                .tenantId(tenantId)
                .caseCount(cases.size())
                .status(TaskStatus.PENDING.name())
                .build();
        evalBatchMapper.insert(batch);

        AsyncTaskDO task = asyncTaskService.create(
                TaskType.RAGAS_EVAL.name(),
                batch.getId(),
                tenantId);

        markEvalRunning(batch.getId(), task.getId());

        RagasEvaluateRequest request = RagasEvaluateRequest.builder()
                .batchId(batch.getId())
                .tenantId(tenantId)
                .cases(toRagasCases(cases))
                .callbackUrl(callbackUrl)
                .build();

        try {
            RagasEvaluateResponse response = ragasRestClient.post()
                    .uri("/evaluate")
                    .body(request)
                    .retrieve()
                    .body(RagasEvaluateResponse.class);
            if (response == null || !Boolean.TRUE.equals(response.getAccepted())) {
                throw new IllegalStateException("Ragas sidecar did not accept the evaluation task");
            }
            log.info("Ragas evaluation task accepted: batchId={}, taskId={}, caseCount={}",
                    batch.getId(), task.getId(), cases.size());
            return EvalRunResponse.builder()
                    .batchId(batch.getId())
                    .taskId(task.getId())
                    .build();
        } catch (Exception ex) {
            markEvalFailed(batch.getId(), task.getId(), ex.getMessage());
            log.error("Failed to call Ragas sidecar: batchId={}, taskId={}", batch.getId(), task.getId(), ex);
            throw BusinessException.of(8104, "Ragas 评估侧车调用失败：" + ex.getMessage());
        }
    }

    private List<RagasEvaluateRequest.CaseItem> toRagasCases(List<EvalCaseDO> cases) {
        return cases.stream()
                .map(evalCase -> RagasEvaluateRequest.CaseItem.builder()
                        .caseId(evalCase.getId())
                        .question(evalCase.getQuestion())
                        .groundTruth(evalCase.getGroundTruth())
                        .build())
                .toList();
    }

    private void markEvalRunning(Long batchId, Long taskId) {
        EvalBatchDO update = new EvalBatchDO();
        update.setId(batchId);
        update.setStatus(TaskStatus.RUNNING.name());
        evalBatchMapper.updateById(update);
        asyncTaskService.running(taskId, 10);
    }

    private void markEvalFailed(Long batchId, Long taskId, String errorMsg) {
        EvalBatchDO update = new EvalBatchDO();
        update.setId(batchId);
        update.setStatus(TaskStatus.FAILED.name());
        evalBatchMapper.updateById(update);
        asyncTaskService.fail(taskId, errorMsg);
    }

    private String requireCallbackUrl() {
        if (!StringUtils.hasText(evalCallbackUrl)) {
            throw BusinessException.of(8105, "Ragas 回调地址未配置");
        }
        return evalCallbackUrl.trim();
    }

    private EvalResultVO toResultVO(EvalResultDO result, boolean superAdmin) {
        EvalCaseDO evalCase = result.getEvalCaseId() == null
                ? null
                : superAdmin
                ? evalCaseMapper.selectByIdIgnoreTenant(result.getEvalCaseId())
                : evalCaseMapper.selectById(result.getEvalCaseId());
        return EvalResultVO.from(result, evalCase);
    }

    private void validateCaseRequest(EvalCaseRequest request) {
        if (request == null) {
            throw BusinessException.of(400, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getQuestion())) {
            throw BusinessException.of(400, "评估问题不能为空");
        }
        if (!StringUtils.hasText(request.getGroundTruth())) {
            throw BusinessException.of(400, "标准答案不能为空");
        }
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(401, "缺少租户身份");
        }
        return tenantId;
    }

    private boolean isSuperAdmin(Integer role) {
        return Integer.valueOf(UserRole.SUPER_ADMIN.getCode()).equals(role);
    }
}
