package com.triobase.service.auth.service;

import com.triobase.service.auth.dto.AuthorizationCompatibilityDashboardResponse;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthAuditMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthDriftMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthorizationCompatibilityServiceTest {

    @Mock private AuthorizationRegistryService registryService;
    @Mock private AuthPageCatalogMapper catalogMapper;
    @Mock private AuthPageCapabilityMapper capabilityMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private RoleAuthActiveReleaseMapper activeReleaseMapper;
    @Mock private RoleAuthCompiledEvidenceMapper evidenceMapper;
    @Mock private AuthGrantMapper grantMapper;
    @Mock private RoleAuthDraftMapper draftMapper;
    @Mock private RoleAuthDriftMapper driftMapper;
    @Mock private RoleAuthAuditMapper auditMapper;
    @Mock private LowcodeOwnershipDiagnosticsClient lowcodeOwnershipDiagnosticsClient;
    private AuthorizationCompatibilityService service;

    @BeforeEach
    void setUp() {
        MybatisPlusTestMetadata.initialize();
        service = new AuthorizationCompatibilityService(registryService, catalogMapper,
                capabilityMapper, roleMapper, activeReleaseMapper, evidenceMapper, grantMapper,
                draftMapper, driftMapper, auditMapper, lowcodeOwnershipDiagnosticsClient);
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        when(lowcodeOwnershipDiagnosticsClient.unresolvedCount("tenant-a")).thenReturn(0L);
    }

    @Test
    void cutoverIsReadyOnlyWhenPublishedEvidenceMatchesRuntimeExactly() {
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("catalog-1");
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        SysAuthPageCapability capability = new SysAuthPageCapability();
        capability.setReadinessStatus("READY");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability));
        SysRole role = new SysRole();
        role.setId("role-1"); role.setRoleName("实施角色"); role.setStatus((short) 1);
        SysRole role2 = new SysRole();
        role2.setId("role-2"); role2.setRoleName("实施角色二"); role2.setStatus((short) 1);
        when(roleMapper.selectList(any())).thenReturn(List.of(role, role2));
        SysRoleAuthActiveRelease active = new SysRoleAuthActiveRelease();
        active.setRoleId("role-1"); active.setReleaseId("release-1");
        SysRoleAuthActiveRelease active2 = new SysRoleAuthActiveRelease();
        active2.setRoleId("role-2"); active2.setReleaseId("release-2");
        when(activeReleaseMapper.selectList(any())).thenReturn(List.of(active, active2));
        SysRoleAuthCompiledEvidence evidence = new SysRoleAuthCompiledEvidence();
        evidence.setReleaseId("release-1");
        evidence.setResourceCode("/api/v1/users"); evidence.setActionCode("GET"); evidence.setEffect("ALLOW");
        SysRoleAuthCompiledEvidence evidence2 = new SysRoleAuthCompiledEvidence();
        evidence2.setReleaseId("release-2");
        evidence2.setResourceCode("/api/v1/users"); evidence2.setActionCode("GET"); evidence2.setEffect("ALLOW");
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence, evidence2));
        SysAuthGrant grant = new SysAuthGrant();
        grant.setSubjectId("role-1");
        grant.setResourceCode("/api/v1/users"); grant.setActionCode("GET"); grant.setEffect("ALLOW");
        SysAuthGrant grant2 = new SysAuthGrant();
        grant2.setSubjectId("role-2");
        grant2.setResourceCode("/api/v1/users"); grant2.setActionCode("GET"); grant2.setEffect("ALLOW");
        when(grantMapper.selectList(any())).thenReturn(List.of(grant, grant2));
        when(draftMapper.selectCount(any())).thenReturn(0L);
        when(driftMapper.selectCount(any())).thenReturn(0L);
        when(auditMapper.selectCount(any())).thenReturn(0L);

        AuthorizationCompatibilityDashboardResponse result = service.assess("tenant-a");

        assertThat(result.isCutoverReady()).isTrue();
        assertThat(result.getDecisionEquivalentRoleCount()).isEqualTo(2);
        assertThat(result.getBlockers()).isEmpty();
        assertThat(result.getCompatibilityQueryCount()).isEqualTo(10);
        verify(evidenceMapper, times(1)).selectList(any());
        verify(grantMapper, times(1)).selectList(any());
    }

    @Test
    void reportsDirectRuntimePermissionAsUnintendedExpansion() {
        SysAuthPageCatalog catalog = new SysAuthPageCatalog(); catalog.setId("catalog-1");
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        SysAuthPageCapability capability = new SysAuthPageCapability(); capability.setReadinessStatus("READY");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability));
        SysRole role = new SysRole(); role.setId("role-1"); role.setRoleName("实施角色"); role.setStatus((short) 1);
        when(roleMapper.selectList(any())).thenReturn(List.of(role));
        SysRoleAuthActiveRelease active = new SysRoleAuthActiveRelease(); active.setRoleId("role-1"); active.setReleaseId("release-1");
        when(activeReleaseMapper.selectList(any())).thenReturn(List.of(active));
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        SysAuthGrant bypass = new SysAuthGrant();
        bypass.setSubjectId("role-1");
        bypass.setResourceCode("/api/v1/users"); bypass.setActionCode("DELETE"); bypass.setEffect("ALLOW");
        when(grantMapper.selectList(any())).thenReturn(List.of(bypass));
        when(draftMapper.selectCount(any())).thenReturn(0L);
        when(driftMapper.selectCount(any())).thenReturn(0L);
        when(auditMapper.selectCount(any())).thenReturn(0L);

        AuthorizationCompatibilityDashboardResponse result = service.assess("tenant-a");

        assertThat(result.isCutoverReady()).isFalse();
        assertThat(result.getUnintendedExpansionCount()).isEqualTo(1);
        assertThat(result.getBlockers()).anyMatch(item -> item.contains("发布版本之外"));
    }
}
