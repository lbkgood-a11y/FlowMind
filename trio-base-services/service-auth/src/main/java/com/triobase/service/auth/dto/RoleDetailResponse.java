package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysRole;
import lombok.Data;

import java.util.List;

@Data
public class RoleDetailResponse {
    private String id;
    private String roleCode;
    private String roleName;
    private String description;
    private List<String> permissionIds;

    public static RoleDetailResponse from(SysRole role, List<String> permissionIds) {
        RoleDetailResponse response = new RoleDetailResponse();
        response.setId(role.getId());
        response.setRoleCode(role.getRoleCode());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());
        response.setPermissionIds(permissionIds);
        return response;
    }
}
