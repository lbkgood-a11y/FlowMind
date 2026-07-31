package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.service.PageCapabilityCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageCapabilityStartupSyncRunnerTest {

    @Mock private PageCapabilityCatalogService catalogService;

    @Test
    void tenantNeutralManifestMaterializesOnlyConfiguredTenants() {
        PageCapabilityManifestSyncRequest template = new PageCapabilityManifestSyncRequest();
        template.setCatalogCode("SYSTEM");
        template.setCatalogVersion(1L);
        template.setSourceType("SYSTEM_MANIFEST");
        template.setPages(List.of());
        PageCapabilityStartupSyncRunner runner =
                new PageCapabilityStartupSyncRunner(List.of(template), catalogService);
        ReflectionTestUtils.setField(runner, "configuredManifestTenants", "tenant-a, tenant-b,tenant-a");
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("CAT");
        when(catalogService.synchronize(any())).thenReturn(catalog);

        runner.synchronizeCatalogs();

        ArgumentCaptor<PageCapabilityManifestSyncRequest> captor =
                ArgumentCaptor.forClass(PageCapabilityManifestSyncRequest.class);
        verify(catalogService, times(2)).synchronize(captor.capture());
        assertThat(captor.getAllValues()).extracting(PageCapabilityManifestSyncRequest::getTenantId)
                .containsExactly("tenant-a", "tenant-b");
        verify(catalogService).activate("tenant-a", "CAT");
        verify(catalogService).activate("tenant-b", "CAT");
        assertThat(template.getTenantId()).isNull();
    }
}
