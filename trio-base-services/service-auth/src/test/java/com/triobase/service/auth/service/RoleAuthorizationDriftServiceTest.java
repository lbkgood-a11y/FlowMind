package com.triobase.service.auth.service;

import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRoleAuthActiveRelease;
import com.triobase.service.auth.entity.SysRoleAuthCompiledEvidence;
import com.triobase.service.auth.entity.SysRoleAuthDrift;
import com.triobase.service.auth.entity.SysRoleAuthRelease;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.RoleAuthActiveReleaseMapper;
import com.triobase.service.auth.mapper.RoleAuthCompiledEvidenceMapper;
import com.triobase.service.auth.mapper.RoleAuthDriftMapper;
import com.triobase.service.auth.mapper.RoleAuthReleaseMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAuthorizationDriftServiceTest {

    @Mock private RoleAuthActiveReleaseMapper activeMapper;
    @Mock private RoleAuthReleaseMapper releaseMapper;
    @Mock private RoleAuthCompiledEvidenceMapper evidenceMapper;
    @Mock private RoleAuthDriftMapper driftMapper;
    @Mock private AuthPageCapabilityMapper capabilityMapper;
    @Mock private AuthPageCapabilityTargetMapper targetMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private AuthorizationRegistryService registryService;
    @Mock private RoleAuthorizationAuditService auditService;
    private RoleAuthorizationDriftService service;

    @BeforeEach
    void setUp() {
        MybatisPlusTestMetadata.initialize();
        service = new RoleAuthorizationDriftService(activeMapper, releaseMapper, evidenceMapper,
                driftMapper, capabilityMapper, targetMapper, userRoleMapper, registryService, auditService);
    }

    @Test
    void detectsMappingImpactWithoutChangingActiveRelease() {
        SysRoleAuthActiveRelease active = new SysRoleAuthActiveRelease();
        active.setTenantId("tenant-a"); active.setRoleId("role-1"); active.setReleaseId("release-1");
        when(activeMapper.selectList(any())).thenReturn(List.of(active));
        SysRoleAuthRelease release = new SysRoleAuthRelease();
        release.setId("release-1"); release.setTenantId("tenant-a"); release.setRoleId("role-1");
        release.setCatalogVersion(1L);
        when(releaseMapper.selectOne(any())).thenReturn(release);
        SysAuthPageCapability capability = new SysAuthPageCapability();
        capability.setId("cap-new"); capability.setCapabilityCode("SYSTEM.USER.READ");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability));
        SysAuthPageCapabilityTarget target = new SysAuthPageCapabilityTarget();
        target.setCapabilityId("cap-new"); target.setResourceCode("/api/v1/users"); target.setActionCode("LIST");
        when(targetMapper.selectList(any())).thenReturn(List.of(target));
        SysRoleAuthCompiledEvidence old = new SysRoleAuthCompiledEvidence();
        old.setCapabilityCode("SYSTEM.USER.READ"); old.setResourceCode("/api/v1/users"); old.setActionCode("GET");
        when(evidenceMapper.selectList(any())).thenReturn(List.of(old));
        when(userRoleMapper.selectCount(any())).thenReturn(2L);
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("catalog-2"); catalog.setTenantId("tenant-a"); catalog.setCatalogVersion(2L);

        assertThat(service.detectForActivatedCatalog(catalog)).isEqualTo(1);

        ArgumentCaptor<SysRoleAuthDrift> captor = ArgumentCaptor.forClass(SysRoleAuthDrift.class);
        verify(driftMapper).insert(captor.capture());
        assertThat(captor.getValue().getDriftType()).isEqualTo("TARGET_CHANGED");
        assertThat(captor.getValue().getAffectedUserCount()).isEqualTo(2L);
        assertThat(captor.getValue().getImpactSummary()).contains("线上权限保持不变");
        verify(activeMapper, org.mockito.Mockito.never()).update(any(), any());
    }
}
