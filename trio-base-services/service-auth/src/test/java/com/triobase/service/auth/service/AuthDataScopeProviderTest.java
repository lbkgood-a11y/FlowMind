package com.triobase.service.auth.service;

import com.triobase.common.core.auth.DataScope;
import com.triobase.service.auth.dto.DataPolicyDimensionResponse;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthDataScopeProviderTest {

    @Mock
    private DataPolicyService dataPolicyService;

    @InjectMocks
    private AuthDataScopeProvider provider;

    @Test
    void resolve_shouldConvertEffectivePolicyToCommonDataScope() {
        DataPolicyDimensionResponse dimension = new DataPolicyDimensionResponse();
        dimension.setDimensionCode("ADMIN");
        dimension.setScopeType("ALL");

        DataPolicyResponse policy = new DataPolicyResponse();
        policy.setId("DP001");
        policy.setRoleId("R001");
        policy.setEffect("ALLOW");
        policy.setCombineMode("AND");
        policy.setDimensions(List.of(dimension));

        EffectiveDataPolicyResponse effective = new EffectiveDataPolicyResponse();
        effective.setUserId("U001");
        effective.setResourceCode("USER");
        effective.setActionCode("QUERY");
        effective.setRestrictive(false);
        effective.setRoleIds(List.of("R001"));
        effective.setPolicies(List.of(policy));

        when(dataPolicyService.resolveEffective("U001", "USER", "QUERY")).thenReturn(effective);

        DataScope dataScope = provider.resolve("U001", "USER", "QUERY");

        assertFalse(dataScope.restrictive());
        assertEquals("U001", dataScope.userId());
        assertEquals(List.of("R001"), dataScope.roleIds());
        assertEquals("DP001", dataScope.policies().get(0).policyId());
        assertTrue(dataScope.allowsAll());
    }
}
