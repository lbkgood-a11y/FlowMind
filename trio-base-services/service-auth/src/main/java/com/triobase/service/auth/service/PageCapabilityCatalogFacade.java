package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.PageCapabilityDiagnosticResponse;
import com.triobase.service.auth.dto.PageCapabilityResponse;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read facade that repairs a missing tenant catalog before configuration UIs consume it.
 * Catalog facts still come exclusively from registered manifests.
 */
@Service
@RequiredArgsConstructor
public class PageCapabilityCatalogFacade {

    private static final Logger logger = LoggerFactory.getLogger(PageCapabilityCatalogFacade.class);
    private static final int CATALOG_NOT_FOUND = 40497;

    private final PageCapabilityCatalogService catalogService;
    private final PageCapabilityManifestMaterializer manifestMaterializer;
    private final AuthorizationRegistryService authorizationRegistryService;

    public List<PageCapabilityResponse> implementationCatalog(
            String requestedTenantId, String requestedCatalogId, String pageCode) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        try {
            return catalogService.implementationCatalog(tenantId, requestedCatalogId, pageCode);
        } catch (BizException exception) {
            if (requestedCatalogId != null || exception.getCode() != CATALOG_NOT_FOUND) {
                throw exception;
            }
            materialize(tenantId);
            return catalogService.implementationCatalog(tenantId, null, pageCode);
        }
    }

    public List<PageCapabilityDiagnosticResponse> diagnostics(
            String requestedTenantId, String requestedCatalogId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        if (requestedCatalogId == null) {
            ensureActiveCatalog(tenantId);
        }
        return catalogService.diagnostics(tenantId, requestedCatalogId);
    }

    public List<SysAuthPageCatalog> catalogs(String requestedTenantId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        if (catalogService.catalogs(tenantId).isEmpty()) {
            try {
                materialize(tenantId);
            } catch (RuntimeException exception) {
                logger.warn("Page capability catalog bootstrap failed for tenant={}: {}",
                        tenantId, exception.getMessage());
            }
        }
        return catalogService.catalogs(tenantId);
    }

    public boolean pageExists(String requestedTenantId, String pageCode) {
        return pageCode != null
                && !implementationCatalog(requestedTenantId, null, pageCode).isEmpty();
    }

    private void ensureActiveCatalog(String tenantId) {
        try {
            catalogService.implementationCatalog(tenantId, null, null);
        } catch (BizException exception) {
            if (exception.getCode() != CATALOG_NOT_FOUND) {
                throw exception;
            }
            materialize(tenantId);
        }
    }

    private synchronized void materialize(String tenantId) {
        manifestMaterializer.materializeAndActivateTenant(tenantId);
    }
}
