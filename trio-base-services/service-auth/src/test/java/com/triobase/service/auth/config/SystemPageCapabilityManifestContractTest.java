package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPageCapabilityManifestContractTest {

    @Test
    void manifestMatchesVisiblePageOperationsAndProtectedBackendActions() throws IOException {
        Path root = repositoryRoot();
        PageCapabilityManifestSyncRequest manifest =
                new SystemPageCapabilityManifestConfig().systemManagementPageCapabilities();

        assertEquals(7, manifest.getPages().size());
        assertFrontendControls(root, manifest);
        assertBackendTargets(root, manifest);
    }

    @Test
    void createCapabilityDoesNotImplicitlyGrantHistoryRead() {
        PageCapabilityManifestSyncRequest manifest =
                new SystemPageCapabilityManifestConfig().systemManagementPageCapabilities();
        PageCapabilityManifestSyncRequest.Page userPage = manifest.getPages().stream()
                .filter(page -> "SYSTEM.USER".equals(page.getPageCode()))
                .findFirst().orElseThrow();
        PageCapabilityManifestSyncRequest.Capability create = userPage.getCapabilities().stream()
                .filter(capability -> "SYSTEM.USER.CREATE".equals(capability.getCapabilityCode()))
                .findFirst().orElseThrow();

        assertEquals(List.of("SYSTEM.USER.ACCESS"), create.getRequiredCapabilityCodes());
    }

    @Test
    void readOnlyPageContainsNoBusinessOperation() {
        PageCapabilityManifestSyncRequest manifest =
                new SystemPageCapabilityManifestConfig().systemManagementPageCapabilities();
        PageCapabilityManifestSyncRequest.Page auditPage = manifest.getPages().stream()
                .filter(page -> "SYSTEM.AUDIT".equals(page.getPageCode()))
                .findFirst().orElseThrow();

        assertTrue(auditPage.getCapabilities().stream()
                .noneMatch(capability -> "OPERATION".equals(capability.getCategory())));
    }

    private void assertFrontendControls(Path root, PageCapabilityManifestSyncRequest manifest) throws IOException {
        Map<String, PageContract> contracts = Map.of(
                "SYSTEM.USER", contract("user", "新增用户", "编辑", "删除"),
                "SYSTEM.TENANT", contract("tenant", "新增租户", "编辑", "新增设置", "删除"),
                "SYSTEM.ROLE", contract("role", "新增角色", "编辑", "删除", "角色授权"),
                "SYSTEM.MENU", contract("menu", "新增菜单", "编辑", "删除"),
                "SYSTEM.AUDIT", contract("audit-log", "详情"),
                "SYSTEM.SESSION", contract("session", "失效"),
                "SYSTEM.CONFIG", contract("config", "编辑"));
        for (PageCapabilityManifestSyncRequest.Page page : manifest.getPages()) {
            PageContract contract = contracts.get(page.getPageCode());
            assertTrue(contract != null, () -> "Missing frontend contract for " + page.getPageCode());
            Path view = root.resolve("trio-base-frontend/apps/web-antd/src/views/system")
                    .resolve(contract.viewDirectory()).resolve("list.vue");
            String source = Files.readString(view);
            for (String label : contract.visibleLabels()) {
                assertTrue(source.contains(label),
                        () -> page.getPageName() + " no longer exposes operation label: " + label);
            }
        }
    }

    private void assertBackendTargets(Path root, PageCapabilityManifestSyncRequest manifest) throws IOException {
        String controllers = readJavaSources(root.resolve("trio-base-services/service-auth/src/main/java"))
                + readJavaSources(root.resolve("trio-base-services/service-tenant/src/main/java"));
        String pageResources = Files.readString(root.resolve(
                "trio-base-services/service-auth/src/main/resources/db/migration/V79__system_page_access_resources.sql"));
        for (PageCapabilityManifestSyncRequest.Page page : manifest.getPages()) {
            for (PageCapabilityManifestSyncRequest.Capability capability : page.getCapabilities()) {
                for (PageCapabilityManifestSyncRequest.Target target : capability.getTargets()) {
                    if (target.getResourceCode().startsWith("PAGE_")) {
                        assertTrue(pageResources.contains(target.getResourceCode()),
                                () -> "Missing page access resource: " + target.getResourceCode());
                    } else {
                        String annotation = "@RequirePermission(\"" + target.getResourceCode()
                                + ":" + target.getActionCode() + "\")";
                        assertTrue(controllers.contains(annotation),
                                () -> "No protected backend action for " + capability.getCapabilityCode()
                                        + ": " + annotation);
                    }
                }
            }
        }
    }

    private String readJavaSources(Path directory) throws IOException {
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(item -> item.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(path));
            }
        }
        return source.toString();
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

    private PageContract contract(String directory, String... labels) {
        return new PageContract(directory, List.of(labels));
    }

    private record PageContract(String viewDirectory, List<String> visibleLabels) {
    }
}
