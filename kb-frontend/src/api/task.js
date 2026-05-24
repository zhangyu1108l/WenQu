import { get } from '../utils/request';

export const getTaskStatus = (taskId) => get(`/tasks/${taskId}/status`);
