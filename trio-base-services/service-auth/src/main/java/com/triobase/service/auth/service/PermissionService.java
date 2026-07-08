package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.CreatePermissionRequest;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.entity.SysRolePermission;
import com.triobase.service.auth.mapper.PermissionMapper;
import com.triobase.service.auth.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public List<SysPermission> list() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getResource)
                .orderByAsc(SysPermission::getAction));
    }

    @Transactional
    public SysPermission create(CreatePermissionRequest request) {
        if (!StringUtils.hasText(request.getResource()) || !StringUtils.hasText(request.getAction())) {
            throw new BizException(40021, "RESOURCE_OR_ACTION_REQUIRED");
        }

        long exists = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getResource, request.getResource())
                .eq(SysPermission::getAction, request.getAction()));
        if (exists > 0) {
            throw new BizException(40022, "PERMISSION_ALREADY_EXISTS");
        }

        SysPermission permission = new SysPermission();
        permission.setId("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        permission.setDescription(request.getDescription());
        permissionMapper.insert(permission);
        return permission;
    }

    @Transactional
    public void delete(String id) {
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BizException(40421, "PERMISSION_NOT_FOUND");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getPermissionId, id));
        permissionMapper.deleteById(id);
    }
}
