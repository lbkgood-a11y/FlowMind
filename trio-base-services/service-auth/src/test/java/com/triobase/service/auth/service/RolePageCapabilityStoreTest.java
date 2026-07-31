package com.triobase.service.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.ReplaceRoleCapabilityIntentRequest;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityDependency;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.entity.SysRoleAuthIntent;
import com.triobase.service.auth.mapper.AuthPageCapabilityDependencyMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthIntentMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePageCapabilityStoreTest {

    @Mock private RoleAuthDraftMapper draftMapper;
    @Mock private RoleAuthIntentMapper intentMapper;
    @Mock private AuthPageCatalogMapper catalogMapper;
    @Mock private AuthPageCapabilityMapper capabilityMapper;
    @Mock private AuthPageCapabilityDependencyMapper dependencyMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private AuthorizationRegistryService registryService;
    @Mock private PageCapabilityManifestMaterializer manifestMaterializer;
    @Mock private RoleAuthorizationAuditService auditService;

    private RolePageCapabilityStore store;
    private SysAuthPageCapability access;
    private SysAuthPageCapability read;

    @BeforeEach
    void setUp() {
        MybatisPlusTestMetadata.initialize();
        store = new RolePageCapabilityStore(draftMapper, intentMapper, catalogMapper,
                capabilityMapper, dependencyMapper, roleMapper, registryService,
                manifestMaterializer, auditService,
                new ObjectMapper());
        when(registryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        SysRoleAuthDraft draft = new SysRoleAuthDraft();
        draft.setId("draft-1");
        draft.setTenantId("tenant-a");
        draft.setRoleId("role-1");
        draft.setCatalogId("catalog-1");
        draft.setDraftStatus("DRAFT");
        draft.setIntentVersion(1L);
        when(draftMapper.selectOne(any())).thenReturn(draft);
        access = capability("access", "进入用户管理", "ACCESS");
        read = capability("read", "查看用户信息", "READ");
        lenient().when(capabilityMapper.selectCount(any())).thenReturn(1L);
        lenient().when(capabilityMapper.selectList(any())).thenReturn(List.of(access, read));
        SysAuthPageCapabilityDependency dependency = new SysAuthPageCapabilityDependency();
        dependency.setTenantId("tenant-a");
        dependency.setCapabilityId("read");
        dependency.setRequiredCapabilityId("access");
        lenient().when(dependencyMapper.selectList(any())).thenReturn(List.of(dependency));
        lenient().when(draftMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    void automaticallyAddsRequiredCapabilityAsLockedDependency() {
        ReplaceRoleCapabilityIntentRequest request = request(false);

        store.replaceIntent("draft-1", request);

        ArgumentCaptor<SysRoleAuthIntent> captor = ArgumentCaptor.forClass(SysRoleAuthIntent.class);
        verify(intentMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SysRoleAuthIntent::getCapabilityId, SysRoleAuthIntent::getSelectionSource)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("read", "EXPLICIT"),
                        org.assertj.core.groups.Tuple.tuple("access", "DEPENDENCY"));
    }

    @Test
    void explainsWhyRequiredCapabilityCannotBeRemoved() {
        ReplaceRoleCapabilityIntentRequest request = request(true);

        assertThatThrownBy(() -> store.replaceIntent("draft-1", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("查看用户信息")
                .hasMessageContaining("进入用户管理");
        verify(intentMapper, never()).insert(any(SysRoleAuthIntent.class));
    }

    @Test
    void materializesCatalogForAuthenticatedTenantOnFirstUse() {
        when(draftMapper.selectOne(any())).thenReturn(null);
        when(roleMapper.selectCount(any())).thenReturn(1L);
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("catalog-1");
        catalog.setTenantId("tenant-a");
        catalog.setLifecycleStatus("ACTIVE");
        when(catalogMapper.selectOne(any())).thenReturn(null, catalog);

        SysRoleAuthDraft created = store.getOrCreateDraft("tenant-a", "role-1");

        verify(manifestMaterializer).materializeAndActivateTenant("tenant-a");
        ArgumentCaptor<SysRoleAuthDraft> captor = ArgumentCaptor.forClass(SysRoleAuthDraft.class);
        verify(draftMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().getCatalogId()).isEqualTo("catalog-1");
        assertThat(created).isSameAs(captor.getValue());
    }

    private ReplaceRoleCapabilityIntentRequest request(boolean removeAccess) {
        ReplaceRoleCapabilityIntentRequest.Selection selection =
                new ReplaceRoleCapabilityIntentRequest.Selection();
        selection.setCapabilityId("read");
        ReplaceRoleCapabilityIntentRequest request = new ReplaceRoleCapabilityIntentRequest();
        request.setTenantId("tenant-a");
        request.setExpectedVersion(1L);
        request.setSelections(List.of(selection));
        request.setRemovedCapabilityIds(removeAccess ? List.of("access") : List.of());
        return request;
    }

    private SysAuthPageCapability capability(String id, String name, String category) {
        SysAuthPageCapability capability = new SysAuthPageCapability();
        capability.setId(id);
        capability.setTenantId("tenant-a");
        capability.setCatalogId("catalog-1");
        capability.setPageCode("SYSTEM.USER");
        capability.setPageName("用户管理");
        capability.setCapabilityName(name);
        capability.setCapabilityCategory(category);
        capability.setStatus((short) 1);
        capability.setScopeSupported((short) 0);
        capability.setFieldPolicySupported((short) 0);
        return capability;
    }
}
