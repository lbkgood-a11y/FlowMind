package com.triobase.service.auth.service;

import com.triobase.common.dto.internal.RoleParticipantsResponse;
import com.triobase.common.dto.internal.UserValidationResponse;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalParticipantQueryServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private InternalParticipantQueryService service;

    @Test
    void resolvesEnabledUsersReturnedByRoleQuery() {
        SysUser user = user("U001", "dept-head", 1);
        when(userMapper.selectEnabledUsersByRoleCode("DEPT_HEAD")).thenReturn(List.of(user));

        RoleParticipantsResponse response = service.resolveRole("DEPT_HEAD");

        assertEquals("DEPT_HEAD", response.getRoleCode());
        assertEquals(List.of("U001"), response.getUsers().stream().map(item -> item.getUserId()).toList());
    }

    @Test
    void validatesOnlyEnabledUser() {
        when(userMapper.selectById("U001")).thenReturn(user("U001", "enabled", 1));
        when(userMapper.selectById("U002")).thenReturn(user("U002", "disabled", 0));

        UserValidationResponse enabled = service.validateUser("U001");
        UserValidationResponse disabled = service.validateUser("U002");

        assertTrue(enabled.isEnabled());
        assertEquals("U001", enabled.getUser().getUserId());
        assertFalse(disabled.isEnabled());
        assertNull(disabled.getUser());
    }

    private SysUser user(String id, String username, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(status);
        return user;
    }
}
