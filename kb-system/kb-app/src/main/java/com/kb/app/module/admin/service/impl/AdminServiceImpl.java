package com.kb.app.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.app.module.admin.dto.CreateTenantRequest;
import com.kb.app.module.admin.dto.TenantVO;
import com.kb.app.module.admin.dto.UserVO;
import com.kb.app.module.admin.entity.TenantDO;
import com.kb.app.module.admin.mapper.TenantMapper;
import com.kb.app.module.admin.service.AdminService;
import com.kb.app.module.auth.entity.UserDO;
import com.kb.app.module.auth.mapper.UserMapper;
import com.kb.common.enums.UserRole;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public TenantVO createTenant(CreateTenantRequest request) {
        validateTenantRequest(request);
        TenantDO existingTenant = tenantMapper.selectOne(new LambdaQueryWrapper<TenantDO>()
                .eq(TenantDO::getCode, request.getCode()));
        if (existingTenant != null) {
            throw BusinessException.of(8001, "租户标识已存在");
        }

        TenantDO tenant = TenantDO.builder()
                .name(request.getName().trim())
                .code(request.getCode().trim())
                .status(1)
                .build();
        tenantMapper.insert(tenant);

        UserDO admin = UserDO.builder()
                .tenantId(tenant.getId())
                .username(request.getAdminUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .role(UserRole.TENANT_ADMIN.getCode())
                .status(1)
                .build();
        userMapper.insert(admin);
        return TenantVO.from(tenant);
    }

    @Override
    public List<TenantVO> listTenants() {
        return tenantMapper.selectList(new LambdaQueryWrapper<TenantDO>()
                        .orderByDesc(TenantDO::getCreatedAt)
                        .orderByDesc(TenantDO::getId))
                .stream()
                .map(TenantVO::from)
                .toList();
    }

    @Override
    public void updateTenantStatus(Long id, Integer status) {
        if (id == null) {
            throw BusinessException.of(400, "tenantId 不能为空");
        }
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw BusinessException.of(400, "租户状态只能为 0 或 1");
        }

        TenantDO tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw BusinessException.of(8002, "租户不存在");
        }

        TenantDO update = TenantDO.builder()
                .id(id)
                .status(status)
                .build();
        tenantMapper.updateById(update);
    }

    @Override
    public List<UserVO> listUsers(Integer operatorRole) {
        List<UserDO> users;
        if (isSuperAdmin(operatorRole)) {
            users = userMapper.selectAllIgnoreTenant();
        } else {
            users = userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                    .orderByAsc(UserDO::getId));
        }
        return users.stream()
                .map(UserVO::from)
                .toList();
    }

    @Override
    public void updateUserRole(Long id, Integer role, Integer operatorRole, Long operatorUserId) {
        if (id == null) {
            throw BusinessException.of(400, "userId 不能为空");
        }
        if (!Integer.valueOf(UserRole.TENANT_ADMIN.getCode()).equals(role)
                && !Integer.valueOf(UserRole.USER.getCode()).equals(role)) {
            throw BusinessException.of(400, "用户角色只能为租户管理员或普通用户");
        }
        if (operatorUserId != null && operatorUserId.equals(id)) {
            throw BusinessException.of(8003, "不能修改自己的角色");
        }

        UserDO target = isSuperAdmin(operatorRole)
                ? userMapper.selectByIdIgnoreTenant(id)
                : userMapper.selectById(id);
        if (target == null) {
            throw BusinessException.of(8004, "用户不存在");
        }
        if (Integer.valueOf(UserRole.SUPER_ADMIN.getCode()).equals(target.getRole())) {
            throw BusinessException.of(8005, "不能修改超级管理员角色");
        }

        int updated = isSuperAdmin(operatorRole)
                ? userMapper.updateRoleIgnoreTenant(id, role)
                : userMapper.updateRole(id, role);
        if (updated == 0) {
            throw BusinessException.of(8004, "用户不存在");
        }
    }

    private void validateTenantRequest(CreateTenantRequest request) {
        if (request == null) {
            throw BusinessException.of(400, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw BusinessException.of(400, "租户名称不能为空");
        }
        if (!StringUtils.hasText(request.getCode())) {
            throw BusinessException.of(400, "租户标识不能为空");
        }
        if (!TENANT_CODE_PATTERN.matcher(request.getCode()).matches()) {
            throw BusinessException.of(400, "租户标识只能包含小写字母、数字和短横线");
        }
        if (!StringUtils.hasText(request.getAdminUsername())) {
            throw BusinessException.of(400, "初始管理员用户名不能为空");
        }
        if (!StringUtils.hasText(request.getAdminPassword())) {
            throw BusinessException.of(400, "初始管理员密码不能为空");
        }
    }

    private boolean isSuperAdmin(Integer role) {
        return Integer.valueOf(UserRole.SUPER_ADMIN.getCode()).equals(role);
    }
}
