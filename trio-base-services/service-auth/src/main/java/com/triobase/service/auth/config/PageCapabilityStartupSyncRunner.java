package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.service.PageCapabilityManifestMaterializer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Arrays;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PageCapabilityStartupSyncRunner {

    private static final Logger logger = LoggerFactory.getLogger(PageCapabilityStartupSyncRunner.class);

    private final List<PageCapabilityManifestSyncRequest> manifests;
    private final PageCapabilityManifestMaterializer manifestMaterializer;

    @Value("${triobase.authorization.manifest-tenants:default}")
    private String configuredManifestTenants;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeCatalogs() {
        for (PageCapabilityManifestSyncRequest manifest : manifests) {
            List<String> targetTenants = targetTenants(manifest);
            if (targetTenants.isEmpty()) {
                logger.warn("Skipping tenant-neutral page capability manifest '{}': "
                                + "triobase.authorization.manifest-tenants is empty",
                        manifest.getCatalogCode());
                continue;
            }
            for (String tenantId : targetTenants) {
                try {
                    manifestMaterializer.materializeAndActivate(manifest, tenantId);
                    logger.info("Page capability catalog activated: tenant={}, code={}, version={}",
                            tenantId, manifest.getCatalogCode(), manifest.getCatalogVersion());
                } catch (RuntimeException exception) {
                    logger.error("Page capability catalog is not production-ready: tenant={}, code={}, reason={}",
                            tenantId, manifest.getCatalogCode(), exception.getMessage());
                }
            }
        }
    }

    private List<String> targetTenants(PageCapabilityManifestSyncRequest manifest) {
        if (StringUtils.hasText(manifest.getTenantId())) {
            return List.of(manifest.getTenantId().trim());
        }
        if (!StringUtils.hasText(configuredManifestTenants)) {
            return List.of();
        }
        return Arrays.stream(configuredManifestTenants.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

}
