package com.triobase.service.auth.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.context.SecurityContextHolder.SecurityContext;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.entity.SysUserSession;
import com.triobase.service.auth.mapper.LoginLogMapper;
import com.triobase.service.auth.mapper.UserSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    @Mock
    private LoginLogMapper loginLogMapper;

    @Mock
    private UserSessionMapper userSessionMapper;

    @InjectMocks
    private LoginSessionService loginSessionService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void revoke_shouldMarkSessionRevoked() {
        SysUserSession session = new SysUserSession();
        session.setId("S001");
        session.setSessionStatus("ACTIVE");
        when(userSessionMapper.selectById("S001")).thenReturn(session);
        SecurityContextHolder.set(new SecurityContext("U_ADMIN", "admin", List.of()));

        SysUserSession result = loginSessionService.revoke("S001");

        assertEquals("REVOKED", result.getSessionStatus());
        assertEquals("U_ADMIN", result.getRevokedBy());
        assertNotNull(result.getRevokedAt());
        verify(userSessionMapper).updateById(session);
    }

    @Test
    void revoke_shouldThrow_whenSessionMissing() {
        when(userSessionMapper.selectById("S404")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> loginSessionService.revoke("S404"));

        assertEquals(40472, ex.getCode());
    }

    @Test
    void isAccessJtiInactive_shouldReturnTrue_whenSessionRevoked() {
        SysUserSession session = new SysUserSession();
        session.setAccessJti("jti-1");
        session.setSessionStatus("REVOKED");
        when(userSessionMapper.selectOne(any())).thenReturn(session);

        assertTrue(loginSessionService.isAccessJtiInactive("jti-1"));
    }
}
