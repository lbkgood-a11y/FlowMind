package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class CreateRoleRequest {
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
}
