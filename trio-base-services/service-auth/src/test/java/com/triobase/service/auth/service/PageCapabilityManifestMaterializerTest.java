package com.triobase.service.auth.service;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageCapabilityManifestMaterializerTest {

    @Mock private PageCapabilityCatalogService catalogService;

    @Test
    void copiesTenantNeutralTemplateWithoutMutatingIt() {
        PageCapabilityManifestSyncRequest template = manifest(null);
        PageCapabilityManifestMaterializer materializer =
                new PageCapabilityManifestMaterializer(List.of(template), catalogService);
        SysAuthPageCatalog synchronizedCatalog = new SysAuthPageCatalog();
        synchronizedCatalog.setId("catalog-1");
        SysAuthPageCatalog activeCatalog = new SysAuthPageCatalog();
        activeCatalog.setId("catalog-1");
        activeCatalog.setLifecycleStatus("ACTIVE");
        ArgumentCaptor<PageCapabilityManifestSyncRequest> captor =
                ArgumentCaptor.forClass(PageCapabilityManifestSyncRequest.class);
        when(catalogService.synchronize(captor.capture())).thenReturn(synchronizedCatalog);
        when(catalogService.activate("tenant-a", "catalog-1")).thenReturn(activeCatalog);

        List<SysAuthPageCatalog> result = materializer.materializeAndActivateTenant("tenant-a");

        assertThat(result).containsExactly(activeCatalog);
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().getCatalogCode()).isEqualTo("SYSTEM");
        assertThat(template.getTenantId()).isNull();
    }

    @Test
    void ignoresManifestOwnedByAnotherTenant() {
        PageCapabilityManifestSyncRequest template = manifest("tenant-b");
        PageCapabilityManifestMaterializer materializer =
                new PageCapabilityManifestMaterializer(List.of(template), catalogService);

        assertThat(materializer.materializeAndActivateTenant("tenant-a")).isEmpty();

        verify(catalogService, never()).synchronize(org.mockito.ArgumentMatchers.any());
    }

    private PageCapabilityManifestSyncRequest manifest(String tenantId) {
        PageCapabilityManifestSyncRequest request = new PageCapabilityManifestSyncRequest();
        request.setTenantId(tenantId);
        request.setCatalogCode("SYSTEM");
        request.setCatalogVersion(1L);
        request.setSourceType("SYSTEM_MANIFEST");
        request.setPages(List.of());
        return request;
    }
}
