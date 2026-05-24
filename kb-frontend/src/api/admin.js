import { get, post, put } from '../utils/request';

export const createTenant = (data) => post('/admin/tenants', data);

export const getTenantList = () => get('/admin/tenants');

export const updateTenantStatus = (id, data) => put(`/admin/tenants/${id}/status`, data);

export const getUserList = (params) => get('/admin/users', params);

export const updateUserRole = (id, data) => put(`/admin/users/${id}/role`, data);
