package com.triobase.service.auth.service;

import com.triobase.service.auth.dto.LegacyRoleAuthorizationAnalysisResponse;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyRoleAuthorizationMigrationServiceTest {

    @Mock private AuthGrantMapper grantMapper;
    @Mock private AuthPageCatalogMapper catalogMapper;
    @Mock private AuthPageCapabilityMapper capabilityMapper;
    @Mock private AuthPageCapabilityTargetMapper targetMapper;
    @Mock private AuthorizationRegistryService registryService;
    @Mock private RolePageCapabilityStore capabilityStore;
    @Mock private RoleAuthorizationCompiler compiler;
    @Mock private RoleAuthorizationAuditService auditService;
    @Mock private RoleAuthDraftMapper draftMapper;
    private LegacyRoleAuthorizationMigrationService service;

    @BeforeEach
    void setUp() {
        MybatisPlusTestMetadata.initialize();
        service = new LegacyRoleAuthorizationMigrationService(grantMapper, catalogMapper,
                capabilityMapper, targetMapper, registryService, capabilityStore, compiler,
                auditService, draftMapper);
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("catalog-1"); catalog.setTenantId("tenant-a");
        catalog.setLifecycleStatus("ACTIVE"); catalog.setCatalogVersion(1L);
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
    }

    @Test
    void classifiesExactAmbiguousAndUnmappedWithoutAutomaticExpansion() {
        SysAuthPageCapability view = capability("view", "查看用户");
        SysAuthPageCapability alternate = capability("alternate", "查看用户摘要");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(view, alternate));
        when(targetMapper.selectList(any())).thenReturn(List.of(
                target("view", "/api/v1/users", "GET"),
                target("alternate", "/api/v1/users", "GET")));
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("/api/v1/users", "GET"),
                grant("/api/v1/legacy-export", "POST")));

        LegacyRoleAuthorizationAnalysisResponse result = service.analyze("tenant-a", "role-1");

        assertThat(result.isReviewRequired()).isTrue();
        assertThat(result.getAmbiguousCount()).isEqualTo(1L);
        assertThat(result.getUnmappedCount()).isEqualTo(1L);
        assertThat(result.getEntries()).extracting(LegacyRoleAuthorizationAnalysisResponse.Entry::getResult)
                .containsExactlyInAnyOrder("AMBIGUOUS", "UNMAPPED");
    }

    private SysAuthPageCapability capability(String id, String name) {
        SysAuthPageCapability capability = new SysAuthPageCapability();
        capability.setId(id); capability.setCapabilityCode("CAP_" + id);
        capability.setCapabilityName(name); return capability;
    }

    private SysAuthPageCapabilityTarget target(String capabilityId, String resource, String action) {
        SysAuthPageCapabilityTarget target = new SysAuthPageCapabilityTarget();
        target.setCapabilityId(capabilityId); target.setResourceCode(resource); target.setActionCode(action);
        return target;
    }

    private SysAuthGrant grant(String resource, String action) {
        SysAuthGrant grant = new SysAuthGrant();
        grant.setTenantId("tenant-a"); grant.setSubjectType("ROLE"); grant.setSubjectId("role-1");
        grant.setResourceCode(resource); grant.setActionCode(action); grant.setEffect("ALLOW"); grant.setStatus((short) 1);
        return grant;
    }
}
