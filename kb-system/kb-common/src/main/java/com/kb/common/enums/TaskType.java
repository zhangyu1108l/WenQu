package com.kb.common.enums;

/**
 * 异步任务类型枚举。
 * <p>
 * 对应 async_task 表的 task_type 字段（VARCHAR 存储枚举名称字符串）。
 * <ul>
 *   <li>{@link #DOC_PROCESS} — 文档处理任务：包括文件解析、文本切块、向量化嵌入全流程</li>
 *   <li>{@link #RAGAS_EVAL} — Ragas 评估任务：批量运行评估用例，计算各维度评分</li>
 * </ul>
 */
public enum TaskType {

    /** 文档处理任务：上传文档后触发，依次执行解析→切块→Embedding→入库 */
    DOC_PROCESS,

    /** Ragas 评估任务：由管理员手动触发，调用 Python 侧车执行评估 */
    RAGAS_EVAL
}
