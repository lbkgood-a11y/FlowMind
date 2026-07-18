package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.auth.dto.CreatePermissionRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final MenuMapper menuMapper;

    public List<SysPermission> list() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getResource)
                .orderByAsc(SysPermission::getAction));
    }

    public PageResult<SysPermission> page(int pageNo, int pageSize) {
        IPage<SysPermission> permPage = permissionMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getResource)
                        .orderByAsc(SysPermission::getAction));
        return PageResult.of(permPage.getRecords(), permPage.getTotal(), pageNo, pageSize);
    }

    public List<String> missingRegisteredCodes(List<String> codes) {
        Set<String> requested = normalizeCodes(codes);
        if (requested.isEmpty()) {
            return List.of();
        }
        Set<String> registered = permissionMapper.selectList(null).stream()
                .filter(permission -> StringUtils.hasText(permission.getResource())
                        && StringUtils.hasText(permission.getAction()))
                .map(permission -> permission.getResource().trim() + ":" + permission.getAction().trim())
                .collect(Collectors.toSet());
        return requested.stream()
                .filter(code -> !registered.contains(code))
                .toList();
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
        permission.setId(UlidGenerator.nextUlid());
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
        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPermissionId, id)) > 0) {
            throw new BizException(40023, "PERMISSION_BOUND_TO_MENU");
        }
        permissionMapper.deleteById(id);
    }

    private Set<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
