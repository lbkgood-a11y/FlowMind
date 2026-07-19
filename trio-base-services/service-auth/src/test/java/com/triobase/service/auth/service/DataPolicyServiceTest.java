package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.DataPolicyDimensionRequest;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import com.triobase.service.auth.dto.SaveDataPolicyRequest;
import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysDataPolicyDimension;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import com.triobase.service.auth.mapper.DataPolicyDimensionMapper;
import com.triobase.service.auth.mapper.DataPolicyMapper;
import com.triobase.service.auth.mapper.OrgScopeMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPolicyServiceTest {

    @Mock
    private DataPolicyMapper dataPolicyMapper;

    @Mock
    private DataPolicyDimensionMapper dataPolicyDimensionMapper;

    @Mock
    private AuthResourceMapper authResourceMapper;

    @Mock
    private AuthActionMapper authActionMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private OrgScopeMapper orgScopeMapper;

    @InjectMocks
    private DataPolicyService dataPolicyService;

    @Test
    void create_shouldPersistPolicyAndDimensions_whenRequestValid() {
        SaveDataPolicyRequest request = baseRequest();

        SysRole role = new SysRole();
        role.setId("R001");
        role.setStatus((short) 1);
        when(roleMapper.selectById("R001")).thenReturn(role);
        when(authResourceMapper.selectCount(any())).thenReturn(1L);
        when(authActionMapper.selectCount(any())).thenReturn(1L);
        when(dataPolicyMapper.selectById(any())).thenAnswer(invocation -> {
            SysDataPolicy policy = new SysDataPolicy();
            policy.setId(invocation.getArgument(0));
            policy.setSubjectId("R001");
            policy.setResourceCode("USER");
            policy.setActionCode("QUERY");
            policy.setEffect("ALLOW");
            policy.setCombineMode("AND");
            policy.setStatus((short) 1);
            return policy;
        });
        when(dataPolicyDimensionMapper.selectList(any())).thenReturn(List.of(newDimension("ADMIN", "OWN_ORG")));

        assertEquals("R001", dataPolicyService.create(request).getRoleId());

        verify(dataPolicyMapper).insert(any(SysDataPolicy.class));
        verify(dataPolicyDimensionMapper).insert(any(SysDataPolicyDimension.class));
    }

    @Test
    void create_shouldRejectAssignedOrgsWithoutOrgUnitIds() {
        SaveDataPolicyRequest request = baseRequest();
        request.getDimensions().get(0).setScopeType("ASSIGNED_ORGS");
        request.getDimensions().get(0).setOrgUnitIds(List.of());

        SysRole role = new SysRole();
        role.setId("R001");
        role.setStatus((short) 1);
        when(roleMapper.selectById("R001")).thenReturn(role);
        when(authResourceMapper.selectCount(any())).thenReturn(1L);
        when(authActionMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> dataPolicyService.create(request));

        assertEquals(40066, ex.getCode());
        verify(dataPolicyMapper, never()).insert(any(SysDataPolicy.class));
    }

    @Test
    void create_shouldRejectUnregisteredResourceAction() {
        SaveDataPolicyRequest request = baseRequest();

        SysRole role = new SysRole();
        role.setId("R001");
        role.setStatus((short) 1);
        when(roleMapper.selectById("R001")).thenReturn(role);
        when(authResourceMapper.selectCount(any())).thenReturn(0L);
        when(authActionMapper.selectCount(any())).thenReturn(0L);

        BizException ex = assertThrows(BizException.class, () -> dataPolicyService.create(request));

        assertEquals(40468, ex.getCode());
        verify(dataPolicyMapper, never()).insert(any(SysDataPolicy.class));
    }

    @Test
    void resolveEffective_shouldReturnRestrictive_whenUserHasNoPolicies() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        EffectiveDataPolicyResponse response = dataPolicyService.resolveEffective("U001", "USER", "QUERY");

        assertTrue(response.isRestrictive());
        assertEquals(List.of(), response.getPolicies());
    }

    @Test
    void resolveEffective_shouldAllowAll_whenUserHasAdminRoleWithoutExplicitPolicy() {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId("U001");
        userRole.setRoleId("R001");
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));

        SysRole adminRole = new SysRole();
        adminRole.setId("R001");
        adminRole.setRoleCode("ADMIN");
        adminRole.setStatus((short) 1);
        when(roleMapper.selectOne(any())).thenReturn(adminRole);

        EffectiveDataPolicyResponse response = dataPolicyService.resolveEffective("U001", "USER", "QUERY");

        assertFalse(response.isRestrictive());
        assertEquals(List.of("R001"), response.getRoleIds());
        assertEquals("ALL", response.getPolicies().get(0).getDimensions().get(0).getScopeType());
        verify(dataPolicyMapper, never()).selectList(any());
    }

    @Test
    void resolveEffective_shouldExpandOwnOrgAndChildren() {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId("U002");
        userRole.setRoleId("R003");
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));

        SysDataPolicy policy = new SysDataPolicy();
        policy.setId("DP001");
        policy.setSubjectId("R003");
        policy.setResourceCode("ORDER");
        policy.setActionCode("QUERY");
        policy.setEffect("ALLOW");
        policy.setCombineMode("AND");
        policy.setStatus((short) 1);
        when(dataPolicyMapper.selectList(any())).thenReturn(List.of(policy));
        when(dataPolicyDimensionMapper.selectList(any()))
                .thenReturn(List.of(newDimension("DP001", "ADMIN", "OWN_ORG_AND_CHILDREN")));
        when(orgScopeMapper.selectDimensionId("default", "ADMIN")).thenReturn("ORG_DIM_ADMIN");
        when(orgScopeMapper.selectActiveUserOrgUnitIds("default", "ORG_DIM_ADMIN", "U002"))
                .thenReturn(List.of("ORG_A"));
        when(orgScopeMapper.selectOrgUnitAndDescendantIds("default", "ORG_DIM_ADMIN", List.of("ORG_A")))
                .thenReturn(List.of("ORG_A", "ORG_B"));

        EffectiveDataPolicyResponse response = dataPolicyService.resolveEffective("U002", "ORDER", "QUERY");

        assertTrue(response.isOrgContextResolved());
        assertEquals(List.of("ORG_A", "ORG_B"),
                response.getPolicies().get(0).getDimensions().get(0).getOrgUnitIds());
    }

    private SaveDataPolicyRequest baseRequest() {
        DataPolicyDimensionRequest dimension = new DataPolicyDimensionRequest();
        dimension.setDimensionCode("ADMIN");
        dimension.setScopeType("OWN_ORG");

        SaveDataPolicyRequest request = new SaveDataPolicyRequest();
        request.setRoleId("R001");
        request.setResourceCode("USER");
        request.setActionCode("QUERY");
        request.setEffect("ALLOW");
        request.setCombineMode("AND");
        request.setStatus(1);
        request.setDimensions(List.of(dimension));
        return request;
    }

    private SysDataPolicyDimension newDimension(String dimensionCode, String scopeType) {
        return newDimension("P001", dimensionCode, scopeType);
    }

    private SysDataPolicyDimension newDimension(String policyId, String dimensionCode, String scopeType) {
        SysDataPolicyDimension dimension = new SysDataPolicyDimension();
        dimension.setId("D001");
        dimension.setPolicyId(policyId);
        dimension.setDimensionCode(dimensionCode);
        dimension.setScopeType(scopeType);
        dimension.setSortOrder(10);
        return dimension;
    }
}
