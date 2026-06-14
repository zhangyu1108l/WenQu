package com.kb.app.module.admin.controller;

import com.kb.app.module.admin.dto.CreateTenantRequest;
import com.kb.app.module.admin.dto.TenantStatusRequest;
import com.kb.app.module.admin.dto.TenantVO;
import com.kb.app.module.admin.dto.UpdateUserRoleRequest;
import com.kb.app.module.admin.dto.UserVO;
import com.kb.app.module.admin.service.AdminService;
import com.kb.common.dto.Result;
import com.kb.common.enums.UserRole;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/tenants")
    public Result<TenantVO> createTenant(
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole,
            @RequestBody CreateTenantRequest request) {
        assertSuperAdmin(userRole);
        return Result.ok(adminService.createTenant(request));
    }

    @GetMapping("/tenants")
    public Result<List<TenantVO>> listTenants(
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertSuperAdmin(userRole);
        return Result.ok(adminService.listTenants());
    }

    @PutMapping("/tenants/{id}/status")
    public Result<Void> updateTenantStatus(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole,
            @RequestBody TenantStatusRequest request) {
        assertSuperAdmin(userRole);
        adminService.updateTenantStatus(id, request == null ? null : request.getStatus());
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<List<UserVO>> listUsers(
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        assertAdmin(userRole);
        return Result.ok(adminService.listUsers(userRole));
    }

    @PutMapping("/users/{id}/role")
    public Result<Void> updateUserRole(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) Integer userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody UpdateUserRoleRequest request) {
        assertAdmin(userRole);
        adminService.updateUserRole(id, request == null ? null : request.getRole(), userRole, userId);
        return Result.ok();
    }

    private void assertAdmin(Integer role) {
        if (!Integer.valueOf(UserRole.SUPER_ADMIN.getCode()).equals(role)
                && !Integer.valueOf(UserRole.TENANT_ADMIN.getCode()).equals(role)) {
            throw BusinessException.of(403, "无权限，需管理员角色");
        }
    }

    private void assertSuperAdmin(Integer role) {
        if (!Integer.valueOf(UserRole.SUPER_ADMIN.getCode()).equals(role)) {
            throw BusinessException.of(403, "无权限，需超级管理员角色");
        }
    }
}
