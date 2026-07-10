package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateRoleRequest {
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    private List<String> menuIds;
}
