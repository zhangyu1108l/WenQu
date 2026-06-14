package com.kb.app.module.admin.dto;

import lombok.Data;

@Data
public class CreateTenantRequest {

    private String name;

    private String code;

    private String adminUsername;

    private String adminPassword;
}
