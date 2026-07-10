package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.auth.dto.CreateRoleRequest;
import com.triobase.service.auth.dto.RoleDetailResponse;
import com.triobase.service.auth.dto.UpdateRoleRequest;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysRoleMenu;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.MenuMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.RoleMenuMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final UserRoleMapper userRoleMapper;

    public List<SysRole> list() {
        return list(null, null);
    }

    public List<SysRole> list(String keyword, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .orderByDesc(SysRole::getCreatedAt);
        String normalizedKeyword = normalizeBlank(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(SysRole::getRoleCode, normalizedKeyword)
                    .or()
                    .like(SysRole::getRoleName, normalizedKeyword)
                    .or()
                    .like(SysRole::getDescription, normalizedKeyword));
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, toStatus(status));
        }
        return roleMapper.selectList(wrapper);
    }

    public PageResult<SysRole> page(int page,
                                    int size,
                                    String keyword,
                                    String roleCode,
                                    String roleName,
                                    Integer status,
                                    LocalDateTime createdStart,
                                    LocalDateTime createdEnd) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .orderByDesc(SysRole::getCreatedAt);
        String normalizedKeyword = normalizeBlank(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(SysRole::getRoleCode, normalizedKeyword)
                    .or()
                    .like(SysRole::getRoleName, normalizedKeyword)
                    .or()
                    .like(SysRole::getDescription, normalizedKeyword));
        }
        String normalizedRoleCode = normalizeBlank(roleCode);
        if (normalizedRoleCode != null) {
            wrapper.like(SysRole::getRoleCode, normalizedRoleCode);
        }
        String normalizedRoleName = normalizeBlank(roleName);
        if (normalizedRoleName != null) {
            wrapper.like(SysRole::getRoleName, normalizedRoleName);
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, toStatus(status));
        }
        if (createdStart != null) {
            wrapper.ge(SysRole::getCreatedAt, createdStart);
        }
        if (createdEnd != null) {
            wrapper.le(SysRole::getCreatedAt, createdEnd);
        }

        IPage<SysRole> rolePage = roleMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(rolePage.getRecords(), rolePage.getTotal(), page, size);
    }

    public RoleDetailResponse findById(String id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        List<String> menuIds = roleMenuMapper.selectMenuIdsByRoleId(id);
        return RoleDetailResponse.from(role, menuIds);
    }

    public boolean existsRoleCode(String roleCode, String excludeId) {
        String normalizedCode = normalizeBlank(roleCode);
        if (normalizedCode == null) {
            return false;
        }
        return countRoleCode(normalizedCode, excludeId) > 0;
    }

    @Transactional
    public SysRole create(CreateRoleRequest request) {
        validateRequired(request.getRoleCode(), request.getRoleName());
        validateUniqueRoleCode(request.getRoleCode(), null);
        List<String> normalizedMenuIds = normalizeMenuIds(request.getMenuIds());

        SysRole role = new SysRole();
        role.setId(UlidGenerator.nextUlid());
        role.setRoleCode(request.getRoleCode().trim());
        role.setRoleName(request.getRoleName().trim());
        role.setDescription(normalizeBlank(request.getDescription()));
        role.setStatus(toStatus(request.getStatus()));
        roleMapper.insert(role);
        replaceMenus(role.getId(), normalizedMenuIds);
        return role;
    }

    @Transactional
    public void delete(String id) {
        if (roleMapper.selectById(id) == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        if (userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id)) > 0) {
            throw new BizException(40043, "ROLE_HAS_USERS");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Transactional
    public SysRole update(String id, UpdateRoleRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        validateRequired(role.getRoleCode(), request.getRoleName());
        List<String> normalizedMenuIds = normalizeMenuIds(request.getMenuIds());
        role.setRoleName(request.getRoleName().trim());
        role.setDescription(normalizeBlank(request.getDescription()));
        if (request.getStatus() != null) {
            role.setStatus(toStatus(request.getStatus()));
        }
        roleMapper.updateById(role);
        replaceMenus(id, normalizedMenuIds);
        return role;
    }

    @Transactional
    public SysRole updateStatus(String id, Integer status) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        role.setStatus(toStatus(status));
        roleMapper.updateById(role);
        return role;
    }

    private void replaceMenus(String roleId, List<String> normalizedMenuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        for (String normalizedMenuId : normalizedMenuIds) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(normalizedMenuId);
            roleMenuMapper.insert(relation);
        }
    }

    private List<String> normalizeMenuIds(List<String> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        List<String> normalizedMenuIds = new LinkedHashSet<>(menuIds).stream()
                .map(this::normalizeBlank)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        for (String menuId : normalizedMenuIds) {
            if (menuMapper.selectById(menuId) == null) {
                throw new BizException(40433, "MENU_NOT_FOUND");
            }
        }
        return normalizedMenuIds;
    }

    private void validateRequired(String roleCode, String roleName) {
        if (!StringUtils.hasText(roleCode) || !StringUtils.hasText(roleName)) {
            throw new BizException(40041, "ROLE_CODE_NAME_REQUIRED");
        }
    }

    private void validateUniqueRoleCode(String roleCode, String currentId) {
        String normalizedCode = normalizeBlank(roleCode);
        if (normalizedCode != null && countRoleCode(normalizedCode, currentId) > 0) {
            throw new BizException(40042, "ROLE_CODE_ALREADY_EXISTS");
        }
    }

    private long countRoleCode(String roleCode, String currentId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode);
        if (StringUtils.hasText(currentId)) {
            wrapper.ne(SysRole::getId, currentId);
        }
        return roleMapper.selectCount(wrapper);
    }

    private Short toStatus(Integer status) {
        return status != null && status == 0 ? (short) 0 : (short) 1;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
