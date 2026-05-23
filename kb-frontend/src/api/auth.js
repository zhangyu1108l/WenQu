/**
 * 认证接口：管理注册、登录、登出和 token 刷新。
 */
import { post } from '../utils/request';

// POST /api/auth/register：公开注册接口。
export const register = (data) => post('/auth/register', data);

// POST /api/auth/login：公开登录接口，返回 accessToken 和 refreshToken。
export const login = (data) => post('/auth/login', data);

// POST /api/auth/logout：登录用户登出，将 JWT 加入 Redis 黑名单。
export const logout = () => post('/auth/logout');

// POST /api/auth/refresh：登录用户刷新 accessToken。
export const refresh = () => post('/auth/refresh');
