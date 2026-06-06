package com.kb.app.module.task.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kb.app.module.task.entity.AsyncTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务表 Mapper — 对应数据库 async_task 表。
 * <p>
 * 继承 MyBatis-Plus {@link BaseMapper}，自动获得 async_task 单表 CRUD 能力；
 * 额外查询方法用于业务任务定位、服务重启恢复检查和管理员任务历史分页。
 *
 * @author kb-system
 */
@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTaskDO> {

    /**
     * 根据业务ID和任务类型查询最新一条任务。
     * <p>
     * 必须同时加 taskType 条件，因为同一个 bizId 可能对应不同类型的任务：
     * 例如 bizId=1 既可能是 document.id 对应的 DOC_PROCESS，也可能是 eval_batch.id 对应的 RAGAS_EVAL。
     * 按创建时间和主键倒序取第一条，用于获取该业务对象最近一次触发的任务状态。
     *
     * @param bizId    业务ID，文档处理时为 document.id，评估时为 eval_batch.id
     * @param taskType 任务类型：DOC_PROCESS / RAGAS_EVAL
     * @return 最新任务记录，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM async_task
            WHERE biz_id = #{bizId}
              AND task_type = #{taskType}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    AsyncTaskDO selectByBizId(@Param("bizId") Long bizId,
                              @Param("taskType") String taskType);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM async_task WHERE id = #{id} LIMIT 1")
    AsyncTaskDO selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 查询指定类型下所有 RUNNING 状态的任务。
     * <p>
     * 使用场景：服务重启时需要调用该方法，找出重启前遗留的 RUNNING 任务，
     * 并统一标记为 FAILED，避免任务因为进程中断而永远卡在 RUNNING 状态。
     * <p>
     * 重启扫描通常不在请求线程内执行，没有 TenantContext，因此这里显式忽略租户拦截器，
     * 扫描全量租户的遗留任务。
     *
     * @param taskType 任务类型：DOC_PROCESS / RAGAS_EVAL
     * @return RUNNING 状态的任务列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM async_task
            WHERE task_type = #{taskType}
              AND status = 'RUNNING'
            ORDER BY updated_at ASC, id ASC
            """)
    List<AsyncTaskDO> selectRunningTasks(@Param("taskType") String taskType);

    /**
     * Query stale RUNNING tasks that have not been updated for longer than the cutoff.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM async_task
            WHERE task_type = #{taskType}
              AND status = 'RUNNING'
              AND updated_at < #{cutoff}
            ORDER BY updated_at ASC, id ASC
            """)
    List<AsyncTaskDO> selectStaleRunningTasks(@Param("taskType") String taskType,
                                             @Param("cutoff") LocalDateTime cutoff);

    /**
     * 按租户和任务类型分页查询任务列表。
     * <p>
     * 供管理员查看任务历史使用，按最近更新时间倒序展示，便于优先看到最新进度或失败原因。
     * page 从 1 开始计数；当 page 小于等于 1 时按第一页处理。
     *
     * @param tenantId 租户ID
     * @param taskType 任务类型：DOC_PROCESS / RAGAS_EVAL
     * @param page     页码，从 1 开始
     * @param size     每页条数
     * @return 当前页任务列表
     */
    @Select("""
            <script>
            <bind name="offset" value="page &lt;= 1 ? 0 : (page - 1) * size" />
            SELECT *
            FROM async_task
            WHERE tenant_id = #{tenantId}
              AND task_type = #{taskType}
            ORDER BY updated_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<AsyncTaskDO> selectByTenantId(@Param("tenantId") Long tenantId,
                                       @Param("taskType") String taskType,
                                       @Param("page") int page,
                                       @Param("size") int size);
}
