package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class UpdateRoleRequest {
    private String roleName;
    private String description;
    private Integer status;
}
