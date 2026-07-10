package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.dto.CreateUserRequest;
import com.triobase.service.auth.dto.UpdateUserRequest;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserInfoPayload findById(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        return toPayload(user);
    }

    public PageResult<UserInfoPayload> list(int page,
                                            int size,
                                            String keyword,
                                            String username,
                                            String userId,
                                            Integer status,
                                            LocalDateTime createdStart,
                                            LocalDateTime createdEnd) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt);
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getEmail, keyword)
                    .or()
                    .like(SysUser::getPhone, keyword));
        }
        if (status != null) {
            queryWrapper.eq(SysUser::getStatus, status);
        }
        if (StringUtils.hasText(username)) {
            queryWrapper.like(SysUser::getUsername, username);
        }
        if (StringUtils.hasText(userId)) {
            queryWrapper.like(SysUser::getId, userId);
        }
        if (createdStart != null) {
            queryWrapper.ge(SysUser::getCreatedAt, createdStart);
        }
        if (createdEnd != null) {
            queryWrapper.le(SysUser::getCreatedAt, createdEnd);
        }

        IPage<SysUser> userPage = userMapper.selectPage(new Page<>(page, size), queryWrapper);
        List<UserInfoPayload> records = userPage.getRecords().stream()
                .map(this::toPayload)
                .collect(Collectors.toList());
        return PageResult.of(records, userPage.getTotal(), page, size);
    }

    @Transactional
    public UserInfoPayload create(CreateUserRequest request) {
        if (!StringUtils.hasText(request.getUsername())) {
            throw new BizException(400, "Username is required");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BizException(400, "Password is required");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())) > 0) {
            throw new BizException(AuthErrorCode.USER_ALREADY_EXISTS);
        }
        validatePassword(request.getPassword());

        SysUser user = new SysUser();
        user.setId(UlidGenerator.nextUlid());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        userMapper.insert(user);

        replaceRoles(user.getId(), request.getRoleIds());
        return toPayload(user);
    }

    @Transactional
    public UserInfoPayload update(String id, UpdateUserRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        if (StringUtils.hasText(request.getPassword())) {
            validatePassword(request.getPassword());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        userMapper.updateById(user);

        if (request.getRoleIds() != null) {
            replaceRoles(id, request.getRoleIds());
        }
        return toPayload(user);
    }

    @Transactional
    public void delete(String id) {
        if (userMapper.selectById(id) == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
    }

    @Transactional
    public void assignRoles(String userId, List<String> roleIds) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        replaceRoles(userId, roleIds);
    }

    @Transactional
    public void updateStatus(String id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    private void replaceRoles(String userId, List<String> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (String roleId : roleIds) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
            }
            if (role.getStatus() != null && role.getStatus() == 0) {
                throw new BizException(40044, "ROLE_DISABLED");
            }
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    private void validatePassword(String password) {
        if (password.length() < 8
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*")) {
            throw new BizException(AuthErrorCode.PASSWORD_TOO_WEAK);
        }
    }

    private UserInfoPayload toPayload(SysUser user) {
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        UserInfoPayload payload = new UserInfoPayload();
        payload.setId(user.getId());
        payload.setUsername(user.getUsername());
        payload.setEmail(user.getEmail());
        payload.setPhone(user.getPhone());
        payload.setStatus(user.getStatus());
        payload.setRoles(roles);
        payload.setCreatedAt(user.getCreatedAt());
        return payload;
    }
}
