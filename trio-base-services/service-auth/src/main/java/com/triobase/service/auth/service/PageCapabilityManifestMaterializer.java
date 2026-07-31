package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Materializes tenant-neutral capability manifests for an explicit tenant.
 * Templates are never mutated, so one system manifest can safely serve every tenant.
 */
@Service
@RequiredArgsConstructor
public class PageCapabilityManifestMaterializer {

    private final List<PageCapabilityManifestSyncRequest> manifests;
    private final PageCapabilityCatalogService catalogService;

    public List<SysAuthPageCatalog> materializeAndActivateTenant(String requestedTenantId) {
        String tenantId = requireTenant(requestedTenantId);
        return manifests.stream()
                .filter(template -> !StringUtils.hasText(template.getTenantId())
                        || tenantId.equals(template.getTenantId().trim()))
                .map(template -> materializeAndActivate(template, tenantId))
                .toList();
    }

    public SysAuthPageCatalog materializeAndActivate(
            PageCapabilityManifestSyncRequest template, String requestedTenantId) {
        String tenantId = requireTenant(requestedTenantId);
        if (template == null) {
            throw new BizException(40096, "PAGE_CAPABILITY_MANIFEST_REQUIRED");
        }
        if (StringUtils.hasText(template.getTenantId())
                && !tenantId.equals(template.getTenantId().trim())) {
            throw new BizException(40391, "PAGE_CAPABILITY_MANIFEST_TENANT_MISMATCH");
        }

        PageCapabilityManifestSyncRequest request = new PageCapabilityManifestSyncRequest();
        request.setTenantId(tenantId);
        request.setCatalogCode(template.getCatalogCode());
        request.setCatalogVersion(template.getCatalogVersion());
        request.setSourceType(template.getSourceType());
        request.setSourceRef(template.getSourceRef());
        request.setPages(template.getPages());

        SysAuthPageCatalog catalog = catalogService.synchronize(request);
        return catalogService.activate(tenantId, catalog.getId());
    }

    private String requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(40082, "AUTHZ_TENANT_REQUIRED");
        }
        return tenantId.trim();
    }
}
