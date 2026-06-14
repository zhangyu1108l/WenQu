package com.kb.app.module.eval.controller;

import com.kb.app.module.eval.dto.EvalBatchDetailVO;
import com.kb.app.module.eval.dto.EvalBatchVO;
import com.kb.app.module.eval.dto.EvalCaseRequest;
import com.kb.app.module.eval.dto.EvalCaseVO;
import com.kb.app.module.eval.dto.EvalRunResponse;
import com.kb.app.module.eval.service.EvalService;
import com.kb.common.dto.Result;
import com.kb.common.enums.UserRole;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    @GetMapping("/cases")
    public Result<List<EvalCaseVO>> listCases(
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertAdmin(userRole);
        return Result.ok(evalService.listCases(userRole));
    }

    @PostMapping("/cases")
    public Result<EvalCaseVO> createCase(
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole,
            @RequestBody EvalCaseRequest request) {
        assertAdmin(userRole);
        return Result.ok(evalService.createCase(request));
    }

    @DeleteMapping("/cases/{id}")
    public Result<Void> deleteCase(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertAdmin(userRole);
        evalService.deleteCase(id, userRole);
        return Result.ok();
    }

    @PostMapping("/run")
    public Result<EvalRunResponse> runEval(
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertAdmin(userRole);
        return Result.ok(evalService.runEval());
    }

    @GetMapping("/batches")
    public Result<List<EvalBatchVO>> listBatches(
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertAdmin(userRole);
        return Result.ok(evalService.listBatches(userRole));
    }

    @GetMapping("/batches/{id}")
    public Result<EvalBatchDetailVO> getBatchDetail(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertAdmin(userRole);
        return Result.ok(evalService.getBatchDetail(id, userRole));
    }

    private void assertAdmin(Integer role) {
        if (!Integer.valueOf(UserRole.SUPER_ADMIN.getCode()).equals(role)
                && !Integer.valueOf(UserRole.TENANT_ADMIN.getCode()).equals(role)) {
            throw BusinessException.of(403, "无权限，需管理员角色");
        }
    }
}
