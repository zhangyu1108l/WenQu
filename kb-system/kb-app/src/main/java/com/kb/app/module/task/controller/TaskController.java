package com.kb.app.module.task.controller;

import com.kb.app.module.task.dto.TaskStatusVO;
import com.kb.app.module.task.service.AsyncTaskService;
import com.kb.common.dto.Result;
import com.kb.common.enums.TaskType;
import com.kb.common.enums.UserRole;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AsyncTaskService asyncTaskService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/{taskId}/status")
    public Result<TaskStatusVO> getStatus(
            @PathVariable("taskId") Long taskId,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertTaskBelongsToTenant(taskId, tenantId, userRole);
        TaskStatusVO status = isSuperAdmin(userRole)
                ? asyncTaskService.getStatusIgnoreTenant(taskId)
                : asyncTaskService.getStatus(taskId);
        return Result.ok(status);
    }

    @GetMapping("/doc/{docId}/status")
    public Result<TaskStatusVO> getDocumentStatus(@PathVariable("docId") Long docId) {
        TaskStatusVO status = asyncTaskService.getByBizId(docId, TaskType.DOC_PROCESS.name());
        return Result.ok(status);
    }

    private void assertTaskBelongsToTenant(Long taskId, Long currentTenantId, Integer userRole) {
        if (taskId == null) {
            throw BusinessException.of(400, "taskId 不能为空");
        }
        if (currentTenantId == null) {
            throw BusinessException.of(401, "缺少租户身份");
        }

        Long taskTenantId = queryTaskTenantId(taskId);
        if (taskTenantId == null) {
            throw BusinessException.of(7001, "任务不存在");
        }
        if (!isSuperAdmin(userRole) && !currentTenantId.equals(taskTenantId)) {
            throw BusinessException.of(403, "无权查看该任务状态");
        }
    }

    private Long queryTaskTenantId(Long taskId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM async_task WHERE id = ?",
                    Long.class,
                    taskId);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private boolean isSuperAdmin(Integer role) {
        return Integer.valueOf(UserRole.SUPER_ADMIN.getCode()).equals(role);
    }
}
