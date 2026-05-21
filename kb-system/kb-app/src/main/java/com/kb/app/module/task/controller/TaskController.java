package com.kb.app.module.task.controller;

import com.kb.app.module.task.dto.TaskStatusVO;
import com.kb.app.module.task.service.AsyncTaskService;
import com.kb.common.dto.Result;
import com.kb.common.enums.TaskType;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务状态查询 Controller。
 * <p>
 * 任务轮询完整交互流程：
 * 前端上传文档或触发评估 -> 拿到 taskId -> 每 2 秒请求
 * GET /api/tasks/{taskId}/status -> DONE/FAILED -> 停止轮询。
 * <p>
 * 前端拿到状态后的处理策略：
 * <ul>
 *     <li>PENDING -> 继续轮询，任务还在队列中。</li>
 *     <li>RUNNING -> 继续轮询，并根据 progress 更新进度条。</li>
 *     <li>DONE -> 停止轮询，刷新业务数据（文档列表/评估报告）。</li>
 *     <li>FAILED -> 停止轮询，展示 errorMsg 给用户。</li>
 * </ul>
 *
 * @author kb-system
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AsyncTaskService asyncTaskService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询指定任务状态。
     * <p>
     * 接口：GET /api/tasks/{taskId}/status
     * <p>
     * 这是前端轮询任务状态的核心接口。前端上传文档或触发评估后拿到 taskId，
     * 每 2 秒调用本接口刷新 status / progress / errorMsg。
     *
     * @param taskId   任务ID，对应 async_task.id
     * @param tenantId 当前租户ID，由 Gateway 注入 X-Tenant-Id
     * @return 任务状态 VO
     */
    @GetMapping("/{taskId}/status")
    public Result<TaskStatusVO> getStatus(
            @PathVariable("taskId") Long taskId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        assertTaskBelongsToTenant(taskId, tenantId);
        TaskStatusVO status = asyncTaskService.getStatus(taskId);
        return Result.ok(status);
    }

    /**
     * 根据文档ID查询最新文档处理任务状态。
     * <p>
     * 接口：GET /api/tasks/doc/{docId}/status
     * <p>
     * 页面刷新场景：用户上传文档后刷新页面，前端无法继续持有上传接口返回的 taskId，
     * 此时可根据 docId 查询最近一次 DOC_PROCESS 任务，用返回的 status / progress
     * 恢复进度条展示。
     *
     * @param docId 文档ID，对应 document.id
     * @return 最新任务状态；文档没有关联任务时 data 为 null
     */
    @GetMapping("/doc/{docId}/status")
    public Result<TaskStatusVO> getDocumentStatus(@PathVariable("docId") Long docId) {
        TaskStatusVO status = asyncTaskService.getByBizId(docId, TaskType.DOC_PROCESS.name());
        return Result.ok(status);
    }

    private void assertTaskBelongsToTenant(Long taskId, Long currentTenantId) {
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

        /*
         * 必须显式校验 tenantId：taskId 是数据库自增数字，用户可能猜测他人的 taskId。
         * 这里只读取 async_task.tenant_id 做鉴权比较，不向前端返回跨租户任务内容。
         */
        if (!currentTenantId.equals(taskTenantId)) {
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
}
