import axios from 'axios';
import { ElMessage } from 'element-plus';
import {
  clearAuthStorage,
  getAccessToken,
  getRefreshToken,
  persistAuth,
  readStoredUserInfo
} from './authStorage';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

const service = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000
});

let refreshPromise = null;
let hasShownLoginExpiredMessage = false;

const isAuthEndpoint = (url = '') =>
  url.includes('/auth/login') ||
  url.includes('/auth/register') ||
  url.includes('/auth/refresh');

const isLoginOrRegisterEndpoint = (url = '') =>
  url.includes('/auth/login') || url.includes('/auth/register');

const normalizeAuthResponse = (body) => {
  if (body && typeof body === 'object' && 'code' in body) {
    if (body.code !== 0) {
      throw new Error(body.msg || '登录状态刷新失败');
    }

    return body.data || {};
  }

  return body || {};
};

const persistRefreshResponse = (data) => {
  const nextAccessToken = data.accessToken || data.token || '';
  const nextRefreshToken = data.refreshToken || getRefreshToken();
  const userInfo = {
    ...readStoredUserInfo(),
    ...(data.userInfo || data.user || data || {})
  };

  if (!nextAccessToken) {
    throw new Error('刷新令牌未返回 accessToken');
  }

  persistAuth(nextAccessToken, nextRefreshToken, userInfo);
  return nextAccessToken;
};

const redirectToLogin = (message = '登录已过期，请重新登录') => {
  clearAuthStorage();

  if (!hasShownLoginExpiredMessage) {
    hasShownLoginExpiredMessage = true;
    ElMessage.error(message);
  }

  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
};

export const refreshAccessToken = async () => {
  const refreshToken = getRefreshToken();

  if (!refreshToken) {
    throw new Error('缺少 refreshToken');
  }

  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${API_BASE_URL}/auth/refresh`, null, {
        headers: {
          Authorization: `Bearer ${refreshToken}`
        }
      })
      .then((response) => persistRefreshResponse(normalizeAuthResponse(response.data)))
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
};

service.interceptors.request.use(
  (config) => {
    config.headers = config.headers || {};

    const token = config.useRefreshToken ? getRefreshToken() : getAccessToken();

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
        hasShownLoginExpiredMessage = false;
        return body.data;
      }

      const message = body.msg || '请求处理失败';
      ElMessage.error(message);
      return Promise.reject(new Error(message));
    }

    hasShownLoginExpiredMessage = false;
    return body;
  },
  (error) => {
    const status = error.response?.status;
    const originalRequest = error.config || {};

    if (status === 401) {
      if (isLoginOrRegisterEndpoint(originalRequest.url)) {
        const message = error.response?.data?.msg || '认证失败';
        ElMessage.error(message);
        return Promise.reject(error);
      }

      if (!originalRequest._retry && !isAuthEndpoint(originalRequest.url) && getRefreshToken()) {
        originalRequest._retry = true;

        return refreshAccessToken()
          .then((nextAccessToken) => {
            originalRequest.headers = originalRequest.headers || {};
            originalRequest.headers.Authorization = `Bearer ${nextAccessToken}`;
            return service(originalRequest);
          })
          .catch((refreshError) => {
            redirectToLogin(refreshError?.message || '登录已过期，请重新登录');
            return Promise.reject(refreshError);
          });
      }

      redirectToLogin(error.response?.data?.msg || '登录已过期，请重新登录');
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

export const post = (url, data, config) => service.post(url, data, config);

export const put = (url, data, config) => service.put(url, data, config);

export const del = (url, config) => service.delete(url, config);

export const upload = (url, formData) =>
  service.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });

export default service;
