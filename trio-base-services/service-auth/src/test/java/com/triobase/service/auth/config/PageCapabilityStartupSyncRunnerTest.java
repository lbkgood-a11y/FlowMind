package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.service.PageCapabilityManifestMaterializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PageCapabilityStartupSyncRunnerTest {

    @Mock private PageCapabilityManifestMaterializer manifestMaterializer;

    @Test
    void tenantNeutralManifestMaterializesOnlyConfiguredTenants() {
        PageCapabilityManifestSyncRequest template = new PageCapabilityManifestSyncRequest();
        template.setCatalogCode("SYSTEM");
        template.setCatalogVersion(1L);
        template.setSourceType("SYSTEM_MANIFEST");
        template.setPages(List.of());
        PageCapabilityStartupSyncRunner runner =
                new PageCapabilityStartupSyncRunner(List.of(template), manifestMaterializer);
        ReflectionTestUtils.setField(runner, "configuredManifestTenants", "tenant-a, tenant-b,tenant-a");

        runner.synchronizeCatalogs();

        verify(manifestMaterializer).materializeAndActivate(template, "tenant-a");
        verify(manifestMaterializer).materializeAndActivate(template, "tenant-b");
    }

    @Test
    void tenantNeutralManifestMaterializesDefaultTenantByDefault() {
        PageCapabilityManifestSyncRequest template = new PageCapabilityManifestSyncRequest();
        template.setCatalogCode("SYSTEM");
        template.setCatalogVersion(1L);
        template.setSourceType("SYSTEM_MANIFEST");
        template.setPages(List.of());
        PageCapabilityStartupSyncRunner runner =
                new PageCapabilityStartupSyncRunner(List.of(template), manifestMaterializer);
        ReflectionTestUtils.setField(runner, "configuredManifestTenants", "default");

        runner.synchronizeCatalogs();

        verify(manifestMaterializer).materializeAndActivate(template, "default");
    }
}
