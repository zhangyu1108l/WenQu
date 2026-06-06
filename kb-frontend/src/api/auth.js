import { post } from '../utils/request';

export const register = (data) => post('/auth/register', data);

export const login = (data) => post('/auth/login', data);

export const logout = () => post('/auth/logout');

export const refresh = () => post('/auth/refresh', null, { useRefreshToken: true });
