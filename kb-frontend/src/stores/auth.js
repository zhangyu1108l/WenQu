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

export const normalizeRole = (role) => {
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
  return Number.isNaN(numberRole) ? null : numberRole;
};

const normalizeUserInfo = (userInfo = {}) => ({
  userId: userInfo.userId ?? userInfo.id ?? null,
  username: userInfo.username ?? '',
  role: normalizeRole(userInfo.role),
  tenantId: userInfo.tenantId ?? userInfo.tenant_id ?? null,
  tenantCode: userInfo.tenantCode ?? userInfo.tenant_code ?? '',
  tenantName: userInfo.tenantName ?? userInfo.tenant_name ?? ''
});

const readStoredUserInfo = () => normalizeUserInfo(parseStorageJson('userInfo', {}));

const persistAuth = (token, refreshToken, userInfo) => {
  if (typeof localStorage === 'undefined') {
    return;
  }

  localStorage.setItem('token', token);
  localStorage.setItem('accessToken', token);
  localStorage.setItem('refreshToken', refreshToken || '');
  localStorage.setItem('userInfo', JSON.stringify(userInfo));
  localStorage.setItem('role', ROLE_NAME_MAP[userInfo.role] || '');
};

const clearAuthStorage = () => {
  if (typeof localStorage !== 'undefined') {
    localStorage.removeItem('token');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('role');
  }
};

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getStorageItem('accessToken') || getStorageItem('token'),
    refreshToken: getStorageItem('refreshToken'),
    userInfo: readStoredUserInfo()
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isSuperAdmin: (state) => state.userInfo.role === 0,
    isTenantAdmin: (state) => state.userInfo.role === 1,
    isAdmin: (state) => state.userInfo.role === 0 || state.userInfo.role === 1,
    roleName: (state) => ROLE_NAME_MAP[state.userInfo.role] || ''
  },

  actions: {
    async login(loginData) {
      const data = await authApi.login(loginData);
      const token = data.accessToken || data.token || '';
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
        clearAuthStorage();
        router.push('/login');
      }
    },

    restoreFromStorage() {
      const token = getStorageItem('accessToken') || getStorageItem('token');
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
