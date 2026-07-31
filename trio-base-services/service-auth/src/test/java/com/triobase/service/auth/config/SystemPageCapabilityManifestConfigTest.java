package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemPageCapabilityManifestConfigTest {

    @Test
    void systemManifestUsesPersistedStableMenuKeys() {
        PageCapabilityManifestSyncRequest manifest =
                new SystemPageCapabilityManifestConfig().systemManagementPageCapabilities();
        Map<String, String> menuKeysByPage = manifest.getPages().stream()
                .collect(Collectors.toMap(
                        PageCapabilityManifestSyncRequest.Page::getPageCode,
                        PageCapabilityManifestSyncRequest.Page::getMenuKey));

        assertEquals("SystemUser", menuKeysByPage.get("SYSTEM.USER"));
        assertEquals("SystemRole", menuKeysByPage.get("SYSTEM.ROLE"));
        assertEquals("SystemMenu", menuKeysByPage.get("SYSTEM.MENU"));
        assertEquals("SystemPageCapabilityCatalog",
                menuKeysByPage.get("SYSTEM.PAGE_CAPABILITY"));
    }
}
