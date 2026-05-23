/**
 * 管理接口：管理租户、租户状态、用户列表和用户角色。
 */
import { get, post, put } from '../utils/request';

// POST /api/admin/tenants：超级管理员创建租户。
export const createTenant = (data) => post('/admin/tenants', data);

// GET /api/admin/tenants：超级管理员获取租户列表。
export const getTenantList = () => get('/admin/tenants');

// PUT /api/admin/tenants/{id}/status：超级管理员启用或禁用租户。
export const updateTenantStatus = (id, data) => put(`/admin/tenants/${id}/status`, data);

// GET /api/admin/users：管理员获取用户列表。
export const getUserList = (params) => get('/admin/users', params);

// PUT /api/admin/users/{id}/role：租户管理员修改用户角色。
export const updateUserRole = (id, data) => put(`/admin/users/${id}/role`, data);
