package com.kb.app.module.eval.controller;

import com.kb.app.context.TenantContext;
import com.kb.app.module.eval.dto.EvalCallbackRequest;
import com.kb.app.module.eval.dto.InternalRagRequest;
import com.kb.app.module.eval.dto.InternalRagResponse;
import com.kb.app.module.eval.dto.SingleResultDTO;
import com.kb.app.rag.RagChain;
import com.kb.common.dto.Result;
import com.kb.common.enums.TaskStatus;
import com.kb.common.enums.TaskType;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 内部接口控制器。
 * <p>
 * 安全策略：这些接口依赖 Docker 内网隔离，不加业务鉴权，也不经过 Gateway；
 * 生产环境可在此基础上增加 IP 白名单校验。
 */
@RestController
@RequiredArgsConstructor
public class InternalController {

    private static final int ERROR_MSG_MAX_LENGTH = 500;

    private final RagChain ragChain;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 供 Ragas 侧车调用，执行一次完整 RAG 问答。
     * <p>
     * 这里使用非 SSE 版本的 RAG 方法，因为 Ragas 评估需要完整字符串，不需要流式 token 输出。
     * 内部接口不加权限校验，只在内网 Docker 网络中可访问，不对外暴露。
     */
    @PostMapping("/internal/rag/answer")
    public Result<InternalRagResponse> answer(@RequestBody InternalRagRequest request) {
        validateInternalRagRequest(request);

        Map<String, Object> ragResult = withTenantContext(request.getTenantId(),
                () -> ragChain.askSync(request.getQuestion(), request.getTenantId()));

        return Result.ok(InternalRagResponse.builder()
                .modelAnswer((String) ragResult.get("modelAnswer"))
                .contexts(castContexts(ragResult.get("contexts")))
                .build());
    }

    /**
     * 供 Ragas 侧车回调，将评估结果写入 MySQL。
     * <p>
     * 此接口是评估结果落库的唯一入口；事务边界覆盖 eval_batch 更新、eval_result 批量插入、
     * async_task 状态更新，确保批量插入 eval_result 时和批次状态保持原子性。
     */
    @PostMapping("/internal/eval/callback")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> callback(@RequestBody EvalCallbackRequest request) {
        validateCallbackRequest(request);

        String status = normalizeStatus(request.getStatus());
        updateEvalBatch(request, status);
        batchInsertEvalResults(request.getBatchId(), request.getResults());
        updateAsyncTask(request.getBatchId(), status, request.getError());

        return Result.ok(null);
    }

    private void updateEvalBatch(EvalCallbackRequest request, String status) {
        int updated = jdbcTemplate.update("""
                        UPDATE eval_batch
                        SET status = ?,
                            avg_faithfulness = ?,
                            avg_answer_relevancy = ?,
                            avg_context_recall = ?,
                            avg_context_precision = ?
                        WHERE id = ?
                        """,
                status,
                toFloat(request.getAvgFaithfulness()),
                toFloat(request.getAvgAnswerRelevancy()),
                toFloat(request.getAvgContextRecall()),
                toFloat(request.getAvgContextPrecision()),
                request.getBatchId());
        if (updated == 0) {
            throw BusinessException.of(8201, "评估批次不存在");
        }
    }

    private void batchInsertEvalResults(Long batchId, List<SingleResultDTO> results) {
        if (CollectionUtils.isEmpty(results)) {
            return;
        }

        List<Object[]> rows = results.stream()
                .map(result -> new Object[]{
                        batchId,
                        result.getCaseId(),
                        safeText(result.getModelAnswer()),
                        toFloat(result.getFaithfulness()),
                        toFloat(result.getAnswerRelevancy()),
                        toFloat(result.getContextRecall()),
                        toFloat(result.getContextPrecision())
                })
                .toList();

        jdbcTemplate.batchUpdate("""
                        INSERT INTO eval_result (
                            batch_id,
                            eval_case_id,
                            model_answer,
                            faithfulness,
                            answer_relevancy,
                            context_recall,
                            context_precision
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                rows);
    }

    private void updateAsyncTask(Long batchId, String status, String error) {
        int updated;
        if (TaskStatus.DONE.name().equals(status)) {
            updated = jdbcTemplate.update("""
                            UPDATE async_task
                            SET status = ?,
                                progress = 100,
                                error_msg = NULL,
                                updated_at = NOW()
                            WHERE biz_id = ?
                              AND task_type = ?
                            """,
                    status,
                    batchId,
                    TaskType.RAGAS_EVAL.name());
        } else {
            updated = jdbcTemplate.update("""
                            UPDATE async_task
                            SET status = ?,
                                error_msg = ?,
                                updated_at = NOW()
                            WHERE biz_id = ?
                              AND task_type = ?
                            """,
                    status,
                    truncateError(error),
                    batchId,
                    TaskType.RAGAS_EVAL.name());
        }

        if (updated == 0) {
            throw BusinessException.of(8202, "评估异步任务不存在");
        }
    }

    private void validateInternalRagRequest(InternalRagRequest request) {
        if (request == null) {
            throw BusinessException.of(400, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getQuestion())) {
            throw BusinessException.of(400, "问题不能为空");
        }
        if (request.getTenantId() == null) {
            throw BusinessException.of(400, "tenantId 不能为空");
        }
    }

    private void validateCallbackRequest(EvalCallbackRequest request) {
        if (request == null) {
            throw BusinessException.of(400, "请求体不能为空");
        }
        if (request.getBatchId() == null) {
            throw BusinessException.of(400, "batchId 不能为空");
        }
        normalizeStatus(request.getStatus());
    }

    private String normalizeStatus(String status) {
        if (TaskStatus.DONE.name().equals(status)) {
            return TaskStatus.DONE.name();
        }
        if (TaskStatus.FAILED.name().equals(status)) {
            return TaskStatus.FAILED.name();
        }
        throw BusinessException.of(400, "评估状态必须是 DONE 或 FAILED");
    }

    private Float toFloat(Double value) {
        return value == null ? null : value.floatValue();
    }

    private String truncateError(String error) {
        if (!StringUtils.hasText(error)) {
            return null;
        }
        return error.length() > ERROR_MSG_MAX_LENGTH
                ? error.substring(0, ERROR_MSG_MAX_LENGTH)
                : error;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    @SuppressWarnings("unchecked")
    private List<String> castContexts(Object contexts) {
        if (contexts instanceof List<?>) {
            return (List<String>) contexts;
        }
        return Collections.emptyList();
    }

    private <T> T withTenantContext(Long tenantId, TenantCallback<T> callback) {
        Long previousTenantId = TenantContext.getTenantId();
        Long previousUserId = TenantContext.getUserId();
        try {
            TenantContext.clear();
            TenantContext.setTenantId(tenantId);
            return callback.execute();
        } finally {
            TenantContext.clear();
            if (previousTenantId != null) {
                TenantContext.setTenantId(previousTenantId);
            }
            if (previousUserId != null) {
                TenantContext.setUserId(previousUserId);
            }
        }
    }

    @FunctionalInterface
    private interface TenantCallback<T> {
        T execute();
    }
}
