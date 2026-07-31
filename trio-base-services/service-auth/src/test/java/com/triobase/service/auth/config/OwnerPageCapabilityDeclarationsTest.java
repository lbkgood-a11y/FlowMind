package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPageCapabilityDeclarationsTest {

    @Test
    void declarationsAreUniqueOwnedBoundAndTargeted() throws IOException {
        List<PageCapabilityManifestSyncRequest.Page> pages = OwnerPageCapabilityDeclarations.pages();
        assertEquals(42, pages.size());

        Set<String> pageCodes = new HashSet<>();
        Set<String> menuKeys = new HashSet<>();
        String migration = Files.readString(repositoryRoot().resolve(
                "trio-base-services/service-auth/src/main/resources/db/migration/"
                        + "V86__bind_owner_pages_and_remove_retired_permission_route.sql"));

        for (PageCapabilityManifestSyncRequest.Page page : pages) {
            assertTrue(pageCodes.add(page.getPageCode()), () -> "Duplicate pageCode " + page.getPageCode());
            assertTrue(menuKeys.add(page.getMenuKey()), () -> "Duplicate menuKey " + page.getMenuKey());
            assertTrue(page.getMetadataJson().contains("ownerService"),
                    () -> "Missing owner metadata for " + page.getPageCode());
            assertTrue(migration.contains("'" + page.getMenuKey() + "', '" + page.getPageCode() + "'"),
                    () -> "Missing menu binding for " + page.getPageCode());
            assertEquals(2, page.getCapabilities().size());
            page.getCapabilities().forEach(capability -> {
                assertEquals(1, capability.getTargets().size());
                PageCapabilityManifestSyncRequest.Target target = capability.getTargets().getFirst();
                assertTrue(target.getResourceCode() != null && !target.getResourceCode().isBlank());
                assertTrue(target.getActionCode() != null && !target.getActionCode().isBlank());
            });
        }
    }

    @Test
    void composedCatalogContainsCoreAndOwnerPages() {
        PageCapabilityManifestSyncRequest manifest =
                new SystemPageCapabilityManifestConfig().systemManagementPageCapabilities();
        assertEquals(3L, manifest.getCatalogVersion());
        assertEquals(50, manifest.getPages().size());
        assertEquals(50, manifest.getPages().stream().map(PageCapabilityManifestSyncRequest.Page::getPageCode).distinct().count());
    }

    private Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("trio-base-frontend"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("TrioBase repository root not found");
        }
        return current;
    }
}
