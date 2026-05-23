/**
 * 文档接口：管理上传、列表、详情、版本、下载、过期时间和删除。
 */
import { del, get, put, upload } from '../utils/request';

// POST /api/docs/upload：上传文档，返回 {docId, taskId}，前端需要根据 taskId 轮询任务状态。
export const uploadDoc = (formData) => upload('/docs/upload', formData);

// GET /api/docs：分页获取文档列表。
export const getDocList = (params) => get('/docs', params);

// GET /api/docs/{id}：获取文档详情和当前激活版本。
export const getDocDetail = (id) => get(`/docs/${id}`);

// GET /api/docs/{id}/versions：获取文档历史版本列表。
export const getVersionList = (id) => get(`/docs/${id}/versions`);

// GET /api/docs/{id}/download：获取 MinIO 预签名下载 URL。
export const getDownloadUrl = (id) => get(`/docs/${id}/download`);

// PUT /api/docs/{id}/expire：设置文档过期时间。
export const setExpireAt = (id, data) => put(`/docs/${id}/expire`, data);

// DELETE /api/docs/{id}：删除文档并同步清理向量数据。
export const deleteDoc = (id) => del(`/docs/${id}`);
