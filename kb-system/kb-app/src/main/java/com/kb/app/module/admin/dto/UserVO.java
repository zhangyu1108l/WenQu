package com.kb.app.module.admin.dto;

import com.kb.app.module.auth.entity.UserDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserVO {

    private Long id;

    private Long tenantId;

    private String username;

    private Integer role;

    private Integer status;

    private LocalDateTime createdAt;

    public static UserVO from(UserDO user) {
        if (user == null) {
            return null;
        }
        return UserVO.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
