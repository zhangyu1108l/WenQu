import { defineStore } from 'pinia';
import * as authApi from '../api/auth';
import router from '../router';
import {
  clearAuthStorage,
  getAccessToken,
  getRefreshToken,
  normalizeRole,
  normalizeUserInfo,
  persistAuth,
  readStoredUserInfo,
  ROLE_NAME_MAP
} from '../utils/authStorage';

export { normalizeRole };

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getAccessToken(),
    refreshToken: getRefreshToken(),
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
      return this.applyAuthResponse(data);
    },

    async register(registerData) {
      const data = await authApi.register(registerData);
      return this.applyAuthResponse(data);
    },

    applyAuthResponse(data) {
      const token = data.accessToken || data.token || '';
      const refreshToken = data.refreshToken || this.refreshToken || '';
      const userInfo = normalizeUserInfo({
        ...this.userInfo,
        ...(data.userInfo || data.user || data)
      });

      this.token = token;
      this.refreshToken = refreshToken;
      this.userInfo = userInfo;

      persistAuth(token, refreshToken, userInfo);
      return userInfo;
    },

    async refreshSession() {
      const data = await authApi.refresh();
      return this.applyAuthResponse(data);
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
      const token = getAccessToken();
      const refreshToken = getRefreshToken();
      const userInfo = readStoredUserInfo();

      this.token = token;
      this.refreshToken = refreshToken;
      this.userInfo = userInfo;
    }
  }
});
