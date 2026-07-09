package com.triobase.service.auth.service;

import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.jwt.JwtUtil;
import com.triobase.common.dto.auth.LoginRequest;
import com.triobase.common.dto.auth.LoginResponse;
import com.triobase.common.dto.auth.TokenValidateResult;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    private static final String SECRET = "test-secret-key-min-32-chars!!!!!";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(authService, "accessTokenTtl", 300);
        ReflectionTestUtils.setField(authService, "refreshTokenTtl", 1800);
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        lenient().when(redis.hasKey(anyString())).thenReturn(false);
    }

    @Test
    void register_shouldCreateUser_whenValidInput() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(userMapper.insert(any(SysUser.class))).thenReturn(1);

        LoginResponse resp = authService.register("newuser", "Pass1234", "test@triobase.local", null);

        assertNotNull(resp.getAccessToken());
        assertNotNull(resp.getRefreshToken());
        assertEquals("newuser", resp.getUsername());
        assertEquals(List.of("USER"), resp.getRoles());
    }

    @Test
    void register_shouldThrow_whenPasswordTooWeak() {
        BizException ex = assertThrows(BizException.class,
                () -> authService.register("user", "123", null, null));
        assertEquals(AuthErrorCode.PASSWORD_TOO_WEAK.getCode(), ex.getCode());
    }

    @Test
    void login_shouldReturnToken_whenCredentialsValid() {
        SysUser user = new SysUser();
        user.setId("U001");
        user.setUsername("admin");
        user.setPassword("$2a$encoded");
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Pass1234", "$2a$encoded")).thenReturn(true);
        when(userMapper.selectRoleCodesByUserId("U001")).thenReturn(List.of("ADMIN"));

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Pass1234");
        LoginResponse resp = authService.login(req);

        assertNotNull(resp.getAccessToken());
        assertEquals("admin", resp.getUsername());
    }

    @Test
    void login_shouldThrow_whenBadCredentials() {
        when(userMapper.selectOne(any())).thenReturn(null);
        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("whatever");

        assertThrows(BizException.class, () -> authService.login(req));
    }

    @Test
    void validate_shouldReturnValid_whenTokenOk() {
        String token = JwtUtil.createAccessToken("U001", "admin", List.of("ADMIN"), SECRET, 300);
        when(userMapper.selectPermissionsByUserId("U001")).thenReturn(List.of("GET:/api/v1/users"));

        TokenValidateResult result = authService.validate(token);

        assertTrue(result.isValid());
        assertEquals("U001", result.getUserId());
        assertEquals("admin", result.getUsername());
    }

    @Test
    void validate_shouldReturnInvalid_whenTokenExpired() {
        String token = JwtUtil.createAccessToken("U001", "admin", List.of(), SECRET, -1);
        TokenValidateResult result = authService.validate(token);
        assertFalse(result.isValid());
    }
}
