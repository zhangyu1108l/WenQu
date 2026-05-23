/**
 * 任务接口：管理异步任务状态查询，供前端轮询文档处理和评估进度。
 */
import { get } from '../utils/request';

// GET /api/tasks/{taskId}/status：根据 taskId 查询任务状态，前端可定时轮询直到 DONE 或 FAILED。
export const getTaskStatus = (taskId) => get(`/tasks/${taskId}/status`);

// GET /api/tasks/doc/{docId}/status：根据 docId 查询文档处理任务状态，前端可用于文档列表中的轮询刷新。
export const getTaskStatusByDocId = (docId) => get(`/tasks/doc/${docId}/status`);
