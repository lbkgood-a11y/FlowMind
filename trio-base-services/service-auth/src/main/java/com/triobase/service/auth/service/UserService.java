package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.context.DataScopeContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.dto.ChangePasswordRequest;
import com.triobase.service.auth.dto.CreateUserRequest;
import com.triobase.service.auth.dto.UpdateProfileRequest;
import com.triobase.service.auth.dto.UpdateUserRequest;
import com.triobase.service.auth.dto.UserProfileResponse;
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

    private static final int MAX_REAL_NAME_LENGTH = 64;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_AVATAR_LENGTH = 512;
    private static final int MAX_INTRODUCTION_LENGTH = 512;
    private static final String DEFAULT_HOME_PATH = "/dashboard/analytics";

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

    public UserProfileResponse findProfile(String userId) {
        return toProfileResponse(requireActiveUser(userId));
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
        if (!applyDataScope(queryWrapper, DataScopeContextHolder.get())) {
            return PageResult.empty(page, size);
        }

        IPage<SysUser> userPage = userMapper.selectPage(new Page<>(page, size), queryWrapper);
        List<UserInfoPayload> records = userPage.getRecords().stream()
                .map(this::toPayload)
                .collect(Collectors.toList());
        return PageResult.of(records, userPage.getTotal(), page, size);
    }

    boolean applyDataScope(LambdaQueryWrapper<SysUser> queryWrapper, DataScope dataScope) {
        if (dataScope == null) {
            return true;
        }
        if (dataScope.restrictive()) {
            return false;
        }
        if (dataScope.allowsAll()) {
            return true;
        }
        if (dataScope.allowsSelf() && StringUtils.hasText(dataScope.userId())) {
            queryWrapper.eq(SysUser::getId, dataScope.userId());
            return true;
        }
        return false;
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
        user.setTenantId("default");
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(normalizeOptional(request.getUsername(), MAX_REAL_NAME_LENGTH, "realName"));
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
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        if (request == null) {
            throw new BizException(400, "Profile request is required");
        }
        SysUser user = requireActiveUser(userId);

        if (request.getRealName() != null) {
            user.setRealName(normalizeOptional(request.getRealName(), MAX_REAL_NAME_LENGTH, "realName"));
        }
        if (request.getEmail() != null) {
            user.setEmail(normalizeEmail(request.getEmail()));
        }
        if (request.getPhone() != null) {
            String phone = normalizeOptional(request.getPhone(), MAX_PHONE_LENGTH, "phone");
            ensurePhoneAvailable(userId, phone);
            user.setPhone(phone);
        }
        if (request.getAvatar() != null) {
            user.setAvatar(normalizeOptional(request.getAvatar(), MAX_AVATAR_LENGTH, "avatar"));
        }
        if (request.getIntroduction() != null) {
            user.setIntroduction(normalizeOptional(request.getIntroduction(), MAX_INTRODUCTION_LENGTH, "introduction"));
        }

        userMapper.updateById(user);
        return toProfileResponse(user);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        if (request == null) {
            throw new BizException(400, "Password request is required");
        }
        if (!StringUtils.hasText(request.getOldPassword())
                || !StringUtils.hasText(request.getNewPassword())
                || !StringUtils.hasText(request.getConfirmPassword())) {
            throw new BizException(400, "Password fields are required");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException(400, "PASSWORD_CONFIRM_MISMATCH");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BizException(400, "NEW_PASSWORD_MUST_BE_DIFFERENT");
        }
        validatePassword(request.getNewPassword());

        SysUser user = requireActiveUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(AuthErrorCode.BAD_CREDENTIALS);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
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

    private SysUser requireActiveUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private void ensurePhoneAvailable(String userId, String phone) {
        if (!StringUtils.hasText(phone)) {
            return;
        }
        Long duplicated = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(SysUser::getId, userId));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(400, "PHONE_ALREADY_EXISTS");
        }
    }

    private String normalizeEmail(String email) {
        String value = normalizeOptional(email, MAX_EMAIL_LENGTH, "email");
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BizException(400, "EMAIL_INVALID");
        }
        return value;
    }

    private String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new BizException(400, fieldName + " is too long");
        }
        return normalized;
    }

    private UserInfoPayload toPayload(SysUser user) {
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        UserInfoPayload payload = new UserInfoPayload();
        payload.setId(user.getId());
        payload.setUsername(user.getUsername());
        payload.setRealName(displayRealName(user));
        payload.setEmail(user.getEmail());
        payload.setPhone(user.getPhone());
        payload.setAvatar(user.getAvatar());
        payload.setIntroduction(user.getIntroduction());
        payload.setStatus(user.getStatus());
        payload.setRoles(roles);
        payload.setCreatedAt(user.getCreatedAt());
        payload.setUpdatedAt(user.getUpdatedAt());
        return payload;
    }

    private UserProfileResponse toProfileResponse(SysUser user) {
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(displayRealName(user));
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setAvatar(user.getAvatar());
        response.setIntroduction(user.getIntroduction());
        response.setDesc(user.getIntroduction());
        response.setStatus(user.getStatus());
        response.setRoles(roles);
        response.setHomePath(DEFAULT_HOME_PATH);
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    private String displayRealName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }
}
