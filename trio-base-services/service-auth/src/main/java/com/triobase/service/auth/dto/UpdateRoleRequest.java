package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateRoleRequest {
    private String roleName;
    private String description;
    private List<String> permissionIds;
}
