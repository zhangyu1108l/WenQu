/**
 * 评估接口：管理评估用例、触发评估、批次历史和批次详情。
 */
import { del, get, post } from '../utils/request';

// GET /api/eval/cases：获取评估用例列表。
export const getCaseList = () => get('/eval/cases');

// POST /api/eval/cases：新增评估用例。
export const createCase = (data) => post('/eval/cases', data);

// DELETE /api/eval/cases/{id}：删除评估用例。
export const deleteCase = (id) => del(`/eval/cases/${id}`);

// POST /api/eval/run：触发评估任务，返回 {batchId, taskId}。
export const runEval = () => post('/eval/run');

// GET /api/eval/batches：获取评估批次历史列表。
export const getBatchList = () => get('/eval/batches');

// GET /api/eval/batches/{id}：获取批次详情和各用例指标。
export const getBatchDetail = (id) => get(`/eval/batches/${id}`);
