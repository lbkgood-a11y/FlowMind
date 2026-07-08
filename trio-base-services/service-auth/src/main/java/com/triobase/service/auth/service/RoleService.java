package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.RoleDetailResponse;
import com.triobase.service.auth.dto.UpdateRoleRequest;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysRolePermission;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public List<SysRole> list() {
        return roleMapper.selectList(null);
    }

    public RoleDetailResponse findById(String id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        List<String> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, id))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
        return RoleDetailResponse.from(role, permissionIds);
    }

    @Transactional
    public SysRole create(String roleCode, String roleName, String description, List<String> permissionIds) {
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)) > 0) {
            throw new BizException(400, "角色编码已存在: " + roleCode);
        }
        SysRole role = new SysRole();
        role.setId("R" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDescription(description);
        roleMapper.insert(role);
        if (permissionIds != null) {
            for (String permId : permissionIds) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(role.getId());
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
        return role;
    }

    @Transactional
    public void delete(String id) {
        if (roleMapper.selectById(id) == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Transactional
    public SysRole update(String id, UpdateRoleRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        roleMapper.updateById(role);

        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));

        if (request.getPermissionIds() != null) {
            for (String permissionId : request.getPermissionIds()) {
                SysRolePermission relation = new SysRolePermission();
                relation.setRoleId(id);
                relation.setPermissionId(permissionId);
                rolePermissionMapper.insert(relation);
            }
        }
        return role;
    }
}
