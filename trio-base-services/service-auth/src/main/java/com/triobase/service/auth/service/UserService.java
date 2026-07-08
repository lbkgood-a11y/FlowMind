package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public UserInfoPayload findById(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        return toPayload(user);
    }

    public PageResult<UserInfoPayload> list(int page, int size) {
        IPage<SysUser> userPage = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getCreatedAt));
        List<UserInfoPayload> records = userPage.getRecords().stream()
                .map(this::toPayload).collect(Collectors.toList());
        return PageResult.of(records, userPage.getTotal(), page, size);
    }

    @Transactional
    public void assignRoles(String userId, List<String> roleIds) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND);
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (String roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
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

    private UserInfoPayload toPayload(SysUser user) {
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        UserInfoPayload payload = new UserInfoPayload();
        payload.setId(user.getId());
        payload.setUsername(user.getUsername());
        payload.setEmail(user.getEmail());
        payload.setStatus(user.getStatus());
        payload.setRoles(roles);
        payload.setCreatedAt(user.getCreatedAt());
        return payload;
    }
}
