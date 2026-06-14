package com.kb.app.module.admin.dto;

import com.kb.app.module.admin.entity.TenantDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantVO {

    private Long id;

    private String name;

    private String code;

    private Integer status;

    private LocalDateTime createdAt;

    public static TenantVO from(TenantDO tenant) {
        if (tenant == null) {
            return null;
        }
        return TenantVO.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .code(tenant.getCode())
                .status(tenant.getStatus())
                .createdAt(tenant.getCreatedAt())
                .build();
    }
}
