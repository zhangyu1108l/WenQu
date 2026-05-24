import axios from 'axios';
import { ElMessage } from 'element-plus';

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
});

service.interceptors.request.use(
  (config) => {
    config.headers = config.headers || {};

    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    if (!config.headers['Content-Type']) {
      config.headers['Content-Type'] = 'application/json';
    }

    return config;
  },
  (error) => Promise.reject(error)
);

service.interceptors.response.use(
  (response) => {
    const body = response.data;

    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data;
      }

      const message = body.msg || '请求处理失败';
      ElMessage.error(message);
      return Promise.reject(new Error(message));
    }

    return body;
  },
  (error) => {
    const status = error.response?.status;

    if (status === 401) {
      localStorage.clear();
      ElMessage.error('登录已过期，请重新登录');

      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }

      return Promise.reject(error);
    }

    const message =
      error.response?.data?.msg ||
      error.response?.data?.message ||
      (error.response ? '请求失败' : '网络连接失败');

    ElMessage.error(message);
    return Promise.reject(error);
  }
);

export const get = (url, params) => service.get(url, { params });

export const post = (url, data) => service.post(url, data);

export const put = (url, data) => service.put(url, data);

export const del = (url) => service.delete(url);

export const upload = (url, formData) =>
  service.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });

export default service;
