package com.kb.app.module.admin.service;

import com.kb.app.module.admin.dto.CreateTenantRequest;
import com.kb.app.module.admin.dto.TenantVO;
import com.kb.app.module.admin.dto.UserVO;

import java.util.List;

public interface AdminService {

    TenantVO createTenant(CreateTenantRequest request);

    List<TenantVO> listTenants();

    void updateTenantStatus(Long id, Integer status);

    List<UserVO> listUsers(Integer operatorRole);

    void updateUserRole(Long id, Integer role, Integer operatorRole, Long operatorUserId);
}
