package com.kb.common.enums;

/**
 * 异步任务状态枚举。
 * <p>
 * 对应 async_task 表的 status 字段（VARCHAR 存储枚举名称字符串）。
 * 状态流转：PENDING → RUNNING → DONE / FAILED
 * <ul>
 *   <li>{@link #PENDING} — 等待执行：任务刚创建，尚未被线程池拾取</li>
 *   <li>{@link #RUNNING} — 执行中：任务正在异步线程中运行，progress 持续更新</li>
 *   <li>{@link #DONE} — 执行成功：任务完成，progress=100</li>
 *   <li>{@link #FAILED} — 执行失败：任务异常终止，error_msg 记录失败原因</li>
 * </ul>
 */
public enum TaskStatus {

    /** 等待执行：任务已入库，等待 @Async 线程池拾取 */
    PENDING,

    /** 执行中：任务正在处理，前端可通过轮询查看 progress */
    RUNNING,

    /** 执行成功：任务全部完成 */
    DONE,

    /** 执行失败：任务异常终止，需查看 error_msg 排查原因 */
    FAILED
}
