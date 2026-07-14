package com.triobase.service.auth.service;

import com.triobase.common.dto.internal.ResolvedUserDto;
import com.triobase.common.dto.internal.RoleParticipantsResponse;
import com.triobase.common.dto.internal.UserValidationResponse;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InternalParticipantQueryService {

    private final UserMapper userMapper;

    public RoleParticipantsResponse resolveRole(String roleCode) {
        RoleParticipantsResponse response = new RoleParticipantsResponse();
        response.setRoleCode(roleCode);
        response.setUsers(userMapper.selectEnabledUsersByRoleCode(roleCode).stream()
                .map(this::toResolvedUser)
                .toList());
        return response;
    }

    public UserValidationResponse validateUser(String userId) {
        SysUser user = userMapper.selectById(userId);
        UserValidationResponse response = new UserValidationResponse();
        boolean enabled = user != null && Integer.valueOf(1).equals(user.getStatus());
        response.setEnabled(enabled);
        response.setUser(enabled ? toResolvedUser(user) : null);
        return response;
    }

    private ResolvedUserDto toResolvedUser(SysUser user) {
        return new ResolvedUserDto(user.getId(), user.getUsername());
    }
}
