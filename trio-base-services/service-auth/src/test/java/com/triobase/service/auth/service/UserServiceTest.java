package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.context.DataScopeContextHolder;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
}
