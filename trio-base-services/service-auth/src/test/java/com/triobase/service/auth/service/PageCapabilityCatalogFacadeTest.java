package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.PageCapabilityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageCapabilityCatalogFacadeTest {

    @Mock private PageCapabilityCatalogService catalogService;
    @Mock private PageCapabilityManifestMaterializer manifestMaterializer;
    @Mock private AuthorizationRegistryService authorizationRegistryService;
    @InjectMocks private PageCapabilityCatalogFacade facade;

    @Test
    void missingActiveCatalogIsMaterializedForAuthenticatedTenantAndRetried() {
        PageCapabilityResponse capability = new PageCapabilityResponse();
        when(authorizationRegistryService.effectiveTenant(null)).thenReturn("tenant-a");
        when(catalogService.implementationCatalog("tenant-a", null, "SYSTEM.MENU"))
                .thenThrow(new BizException(40497, "PAGE_CAPABILITY_CATALOG_NOT_FOUND"))
                .thenReturn(List.of(capability));

        List<PageCapabilityResponse> result =
                facade.implementationCatalog(null, null, "SYSTEM.MENU");

        assertEquals(1, result.size());
        verify(manifestMaterializer).materializeAndActivateTenant("tenant-a");
    }
}
