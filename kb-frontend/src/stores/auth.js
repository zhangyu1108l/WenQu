import { defineStore } from 'pinia';
import * as authApi from '../api/auth';
import router from '../router';

const ROLE_NAME_MAP = {
  0: 'SUPER_ADMIN',
  1: 'TENANT_ADMIN',
  2: 'USER'
};

const ROLE_VALUE_MAP = {
  SUPER_ADMIN: 0,
  TENANT_ADMIN: 1,
  USER: 2
};

const getStorageItem = (key) => {
  if (typeof localStorage === 'undefined') {
    return '';
  }

  return localStorage.getItem(key) || '';
};

const parseStorageJson = (key, fallback) => {
  const value = getStorageItem(key);

  if (!value) {
    return fallback;
  }

  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
};

const normalizeRole = (role) => {
  if (role === null || role === undefined || role === '') {
    return null;
  }

  if (typeof role === 'number') {
    return role;
  }

  if (ROLE_VALUE_MAP[role] !== undefined) {
    return ROLE_VALUE_MAP[role];
  }

  const numberRole = Number(role);
  return Number.isNaN(numberRole) ? role : numberRole;
};

const normalizeUserInfo = (userInfo = {}) => ({
  userId: userInfo.userId ?? userInfo.id ?? null,
  username: userInfo.username ?? '',
  role: normalizeRole(userInfo.role),
  tenantId: userInfo.tenantId ?? null,
  tenantCode: userInfo.tenantCode ?? ''
});

const readStoredUserInfo = () => normalizeUserInfo(parseStorageJson('userInfo', {}));

const persistAuth = (token, refreshToken, userInfo) => {
  if (typeof localStorage === 'undefined') {
    return;
  }

  localStorage.setItem('token', token);
  localStorage.setItem('accessToken', token);
  localStorage.setItem('refreshToken', refreshToken);
  localStorage.setItem('userInfo', JSON.stringify(userInfo));
  // role 需要单独存入 localStorage，路由守卫可能在 Pinia 初始化前执行，只能先从 localStorage 读取权限。
  localStorage.setItem('role', ROLE_NAME_MAP[userInfo.role] || String(userInfo.role ?? ''));
};

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // token 同时存 Pinia 和 localStorage：Pinia 用于响应式更新，localStorage 用于页面刷新后恢复登录态。
    token: getStorageItem('token') || getStorageItem('accessToken'),
    refreshToken: getStorageItem('refreshToken'),
    userInfo: readStoredUserInfo()
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isSuperAdmin: (state) => state.userInfo.role === 0,
    isTenantAdmin: (state) => state.userInfo.role === 0 || state.userInfo.role === 1,
    isUser: (state) => state.userInfo.role === 2
  },

  actions: {
    async login(loginData) {
      const data = await authApi.login(loginData);
      const token = data.token || data.accessToken || '';
      const refreshToken = data.refreshToken || '';
      const userInfo = normalizeUserInfo(data.userInfo || data.user || data);

      this.token = token;
      this.refreshToken = refreshToken;
      this.userInfo = userInfo;

      persistAuth(token, refreshToken, userInfo);
      return userInfo;
    },

    async logout() {
      try {
        await authApi.logout();
      } finally {
        this.token = '';
        this.refreshToken = '';
        this.userInfo = normalizeUserInfo();

        if (typeof localStorage !== 'undefined') {
          localStorage.clear();
        }

        router.push('/login');
      }
    },

    restoreFromStorage() {
      // 页面刷新后 Pinia state 会重置，需要从 storage 恢复 token 和 userInfo。
      const token = getStorageItem('token') || getStorageItem('accessToken');
      const refreshToken = getStorageItem('refreshToken');
      const userInfo = readStoredUserInfo();

      this.token = token;
      this.refreshToken = refreshToken;
      this.userInfo = userInfo;

      if (token) {
        persistAuth(token, refreshToken, userInfo);
      }
    }
  }
});
