import axios from 'axios';
import { ElMessage } from 'element-plus';

const service = axios.create({
  // baseURL 从环境变量读取：开发环境走 Vite proxy，生产环境走 nginx 反向代理。
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
});

service.interceptors.request.use(
  (config) => {
    // 每次请求自动从 localStorage 注入 token，页面层不需要重复处理鉴权请求头。
    config.headers = config.headers || {};
    const token = localStorage.getItem('accessToken');

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    config.headers['Content-Type'] = config.headers['Content-Type'] || 'application/json';
    return config;
  },
  (error) => Promise.reject(error)
);

service.interceptors.response.use(
  (response) => {
    const body = response.data || {};
    const { code, msg, data } = body;

    if (code === 0) {
      // code 为 0 表示业务成功，只返回 data，页面层直接拿到业务数据。
      return data;
    }

    // code 非 0 表示后端业务校验失败或业务异常，提示 msg 并抛出错误给调用方处理。
    const errorMessage = msg || '请求处理失败';
    ElMessage.error(errorMessage);
    return Promise.reject(new Error(errorMessage));
  },
  (error) => {
    const status = error.response?.status;

    if (status === 401) {
      // 401 表示 token 过期或无效，强制清理本地登录态并重新登录。
      localStorage.clear();
      ElMessage.error('登录已过期，请重新登录');
      window.location.href = '/login';
      return Promise.reject(error);
    }

    if (status === 500) {
      // 500 表示服务端内部错误，统一提示用户稍后重试。
      ElMessage.error('服务器错误，请稍后重试');
      return Promise.reject(error);
    }

    if (!error.response) {
      // 没有 HTTP 响应通常是网络中断、超时或网关不可达。
      ElMessage.error('网络连接失败');
      return Promise.reject(error);
    }

    // 其他 HTTP 状态优先展示后端返回的 msg，避免静默失败。
    const errorMessage = error.response.data?.msg || '请求失败';
    ElMessage.error(errorMessage);
    return Promise.reject(error);
  }
);

export const get = (url, params) => service.get(url, { params });

export const post = (url, data) => service.post(url, data);

export const put = (url, data) => service.put(url, data);

export const del = (url) => service.delete(url);

// 上传文件专用，Content-Type 设置为 multipart/form-data。
export const upload = (url, formData) =>
  service.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
