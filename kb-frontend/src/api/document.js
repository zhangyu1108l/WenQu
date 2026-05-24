import { del, get, put, upload } from '../utils/request';

export const uploadDoc = (formData) => upload('/docs/upload', formData);

export const getDocList = (params) => get('/docs', params);

export const getDocDetail = (id) => get(`/docs/${id}`);

export const getVersionList = (id) => get(`/docs/${id}/versions`);

export const getDownloadUrl = (id) => get(`/docs/${id}/download`);

export const setExpireAt = (id, data) => put(`/docs/${id}/expire`, data);

export const deleteDoc = (id) => del(`/docs/${id}`);
