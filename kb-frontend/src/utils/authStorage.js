export const ROLE_NAME_MAP = {
  0: 'SUPER_ADMIN',
  1: 'TENANT_ADMIN',
  2: 'USER'
};

export const ROLE_VALUE_MAP = {
  SUPER_ADMIN: 0,
  TENANT_ADMIN: 1,
  USER: 2
};

export const getStorageItem = (key) => {
  if (typeof localStorage === 'undefined') {
    return '';
  }

  return localStorage.getItem(key) || '';
};

export const parseStorageJson = (key, fallback) => {
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

export const normalizeUserInfo = (userInfo = {}) => ({
  userId: userInfo.userId ?? userInfo.id ?? null,
  username: userInfo.username ?? '',
  role: normalizeRole(userInfo.role),
  tenantId: userInfo.tenantId ?? userInfo.tenant_id ?? null,
  tenantCode: userInfo.tenantCode ?? userInfo.tenant_code ?? '',
  tenantName: userInfo.tenantName ?? userInfo.tenant_name ?? ''
});

export const readStoredUserInfo = () => normalizeUserInfo(parseStorageJson('userInfo', {}));

export const getAccessToken = () => getStorageItem('accessToken') || getStorageItem('token');

export const getRefreshToken = () => getStorageItem('refreshToken');

export const persistAuth = (token, refreshToken, userInfo) => {
  if (typeof localStorage === 'undefined') {
    return;
  }

  const normalizedUserInfo = normalizeUserInfo(userInfo);

  localStorage.setItem('token', token || '');
  localStorage.setItem('accessToken', token || '');
  localStorage.setItem('refreshToken', refreshToken || '');
  localStorage.setItem('userInfo', JSON.stringify(normalizedUserInfo));
  localStorage.setItem('role', ROLE_NAME_MAP[normalizedUserInfo.role] || '');

  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('wenqu:auth-updated'));
  }
};

export const clearAuthStorage = () => {
  if (typeof localStorage === 'undefined') {
    return;
  }

  localStorage.removeItem('token');
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('userInfo');
  localStorage.removeItem('role');

  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('wenqu:auth-cleared'));
  }
};

export const readAuthState = () => ({
  token: getAccessToken(),
  refreshToken: getRefreshToken(),
  userInfo: readStoredUserInfo()
});
