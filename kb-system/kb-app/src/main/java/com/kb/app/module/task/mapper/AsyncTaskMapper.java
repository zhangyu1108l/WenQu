package com.kb.app.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.task.entity.AsyncTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步任务表 Mapper — 对应数据库 async_task 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得单表 CRUD 能力。
 * <p>
 * async_task 表包含 tenant_id 字段，所有查询会被 {@code TenantLineInnerInterceptor}
 * 自动追加 AND tenant_id = ?，确保租户间数据隔离。
 * <p>
 * 主要使用场景：
 * <ul>
 *     <li>触发任务：insert 一条 PENDING 状态的任务记录，立即返回 taskId</li>
 *     <li>状态轮询：前端每 2 秒 GET /api/tasks/{taskId}/status 查询进度</li>
 *     <li>异步更新：@Async 线程池中逐步更新 progress（30/60/90/100）和 status</li>
 *     <li>失败处理：status=FAILED 时写入 error_msg</li>
 * </ul>
 *
 * @author kb-system
 */
@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTaskDO> {
}
