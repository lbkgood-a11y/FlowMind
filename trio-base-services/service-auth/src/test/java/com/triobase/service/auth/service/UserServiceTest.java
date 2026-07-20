package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.context.DataScopeContextHolder;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.dto.ChangePasswordRequest;
import com.triobase.service.auth.dto.CreateUserRequest;
import com.triobase.service.auth.dto.UpdateProfileRequest;
import com.triobase.service.auth.dto.UserProfileResponse;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void tearDown() {
        DataScopeContextHolder.clear();
        SecurityContextHolder.clear();
    }

    @Test
    void list_shouldReturnEmpty_whenDataScopeRestrictive() {
        DataScopeContextHolder.set(DataScope.restrictive("U001", "USER", "QUERY"));

        PageResult<UserInfoPayload> result = userService.list(1, 20, null, null, null, null, null, null);

        assertEquals(0, result.getTotal());
        assertEquals(List.of(), result.getRecords());
        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    void applyDataScope_shouldAllowAll_whenPolicyContainsAllScope() {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        assertTrue(userService.applyDataScope(queryWrapper, allScope("U001")));
        assertTrue(queryWrapper.getParamNameValuePairs().isEmpty());
    }

    @Test
    void applyDataScope_shouldLimitToCurrentUser_whenPolicyContainsSelfScope() {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        assertTrue(userService.applyDataScope(queryWrapper, selfScope("U002")));

        assertFalse(queryWrapper.getExpression().getNormal().isEmpty());
    }

    @Test
    void applyDataScope_shouldDenyUnsupportedScopeTypes() {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        DataScope orgScope = new DataScope(
                "U003",
                "USER",
                "QUERY",
                false,
                false,
                List.of("R003"),
                List.of(new DataScope.Policy(
                        "DP003",
                        "R003",
                        "ALLOW",
                        "AND",
                        List.of(new DataScope.Dimension("ADMIN", "OWN_ORG", List.of()))
                ))
        );

        assertFalse(userService.applyDataScope(queryWrapper, orgScope));
    }

    @Test
    void findProfile_shouldExposePersonalCenterFields() {
        SysUser user = activeUser();
        user.setRealName(null);
        user.setAvatar("https://cdn.example/avatar.png");
        user.setIntroduction("Builder");
        when(userMapper.selectById("U001")).thenReturn(user);
        when(userMapper.selectRoleCodesByUserId("U001")).thenReturn(List.of("USER"));

        UserProfileResponse response = userService.findProfile("U001");

        assertEquals("U001", response.getUserId());
        assertEquals("admin", response.getRealName());
        assertEquals("https://cdn.example/avatar.png", response.getAvatar());
        assertEquals("Builder", response.getIntroduction());
        assertEquals("Builder", response.getDesc());
        assertEquals(List.of("USER"), response.getRoles());
        assertEquals("/dashboard/analytics", response.getHomePath());
    }

    @Test
    void create_shouldUseCurrentTenantWhenRequestDoesNotSpecifyTenant() {
        setContext("tenant-a", List.of("TENANT_ADMIN"));
        CreateUserRequest request = createUserRequest();
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Strong123")).thenReturn("encoded");
        when(userMapper.selectRoleCodesByUserId(anyString())).thenReturn(List.of("USER"));

        UserInfoPayload response = userService.create(request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("tenant-a", captor.getValue().getTenantId());
        assertEquals("tenant-a", response.getTenantId());
    }

    @Test
    void create_shouldRejectCrossTenantRequestForTenantAdmin() {
        setContext("tenant-a", List.of("TENANT_ADMIN"));
        CreateUserRequest request = createUserRequest();
        request.setTenantId("tenant-b");

        BizException ex = assertThrows(BizException.class, () -> userService.create(request));

        assertEquals(40361, ex.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void findById_shouldRejectCrossTenantUserForTenantAdmin() {
        setContext("tenant-a", List.of("TENANT_ADMIN"));
        SysUser user = activeUser();
        user.setTenantId("tenant-b");
        when(userMapper.selectById("U001")).thenReturn(user);

        BizException ex = assertThrows(BizException.class, () -> userService.findById("U001"));

        assertEquals(40361, ex.getCode());
    }

    @Test
    void updateProfile_shouldNormalizeAndPersistEditableFields() {
        SysUser user = activeUser();
        when(userMapper.selectById("U001")).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectRoleCodesByUserId("U001")).thenReturn(List.of("USER"));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setRealName(" Ada ");
        request.setEmail(" ada@example.com ");
        request.setPhone(" 13800138000 ");
        request.setAvatar(" https://cdn.example/avatar.png ");
        request.setIntroduction(" Flow builder ");

        UserProfileResponse response = userService.updateProfile("U001", request);

        assertEquals("Ada", user.getRealName());
        assertEquals("ada@example.com", user.getEmail());
        assertEquals("13800138000", user.getPhone());
        assertEquals("https://cdn.example/avatar.png", user.getAvatar());
        assertEquals("Flow builder", user.getIntroduction());
        assertEquals("Ada", response.getRealName());
        verify(userMapper).updateById(user);
    }

    @Test
    void updateProfile_shouldClearBlankOptionalFields() {
        SysUser user = activeUser();
        user.setPhone("13800138000");
        when(userMapper.selectById("U001")).thenReturn(user);
        when(userMapper.selectRoleCodesByUserId("U001")).thenReturn(List.of("USER"));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone(" ");

        UserProfileResponse response = userService.updateProfile("U001", request);

        assertNull(user.getPhone());
        assertNull(response.getPhone());
        verify(userMapper).updateById(user);
    }

    @Test
    void updateProfile_shouldRejectDuplicatedPhone() {
        SysUser user = activeUser();
        when(userMapper.selectById("U001")).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(1L);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone("13800138000");

        BizException ex = assertThrows(BizException.class, () -> userService.updateProfile("U001", request));

        assertEquals(400, ex.getCode());
        assertEquals("PHONE_ALREADY_EXISTS", ex.getMessage());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePassword_shouldVerifyOldPasswordAndEncodeNewPassword() {
        SysUser user = activeUser();
        user.setPassword("$2a$old");
        when(userMapper.selectById("U001")).thenReturn(user);
        when(passwordEncoder.matches("OldPass123", "$2a$old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$new");

        userService.changePassword("U001", passwordRequest("OldPass123", "NewPass123", "NewPass123"));

        assertEquals("$2a$new", user.getPassword());
        verify(userMapper).updateById(user);
    }

    @Test
    void changePassword_shouldThrowBadCredentials_whenOldPasswordWrong() {
        SysUser user = activeUser();
        user.setPassword("$2a$old");
        when(userMapper.selectById("U001")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> userService.changePassword("U001", passwordRequest("WrongPass123", "NewPass123", "NewPass123")));

        assertEquals(AuthErrorCode.BAD_CREDENTIALS.getCode(), ex.getCode());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    private DataScope allScope(String userId) {
        return scope(userId, "ALL");
    }

    private DataScope selfScope(String userId) {
        return scope(userId, "SELF");
    }

    private DataScope scope(String userId, String scopeType) {
        return new DataScope(
                userId,
                "USER",
                "QUERY",
                false,
                false,
                List.of("R001"),
                List.of(new DataScope.Policy(
                        "DP001",
                        "R001",
                        "ALLOW",
                        "AND",
                        List.of(new DataScope.Dimension("ADMIN", scopeType, List.of()))
                ))
        );
    }

    private SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId("U001");
        user.setTenantId("default");
        user.setUsername("admin");
        user.setRealName("Admin");
        user.setEmail("admin@triobase.local");
        user.setStatus(1);
        return user;
    }

    private ChangePasswordRequest passwordRequest(String oldPassword, String newPassword, String confirmPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    private CreateUserRequest createUserRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("ada");
        request.setPassword("Strong123");
        request.setEmail("ada@example.com");
        return request;
    }

    private void setContext(String tenantId, List<String> roles) {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001",
                "admin",
                tenantId,
                roles,
                List.of("*"),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null));
    }
}
