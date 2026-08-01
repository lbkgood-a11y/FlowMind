package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SystemPageCapabilityManifestConfig {

    @Bean
    PageCapabilityManifestSyncRequest systemManagementPageCapabilities() {
        PageCapabilityManifestSyncRequest manifest = new PageCapabilityManifestSyncRequest();
        manifest.setCatalogCode("SYSTEM_MANAGEMENT");
        manifest.setCatalogVersion(5L);
        manifest.setSourceType("SYSTEM_MANIFEST");
        manifest.setSourceRef("service-auth/system-management");
        List<PageCapabilityManifestSyncRequest.Page> pages = new ArrayList<>(List.of(
                crudPage("SYSTEM.USER", "用户管理", "SystemUser", "PAGE_USER_MANAGEMENT",
                        "/api/v1/users", "/api/v1/users/*", "新增用户", "编辑用户", "删除用户"),
                tenantPage(),
                rolePage(),
                crudPage("SYSTEM.MENU", "菜单管理", "SystemMenu", "PAGE_MENU_MANAGEMENT",
                        "/api/v1/menus", "/api/v1/menus/*", "新增菜单", "编辑菜单", "删除菜单"),
                readOnlyPage("SYSTEM.AUDIT", "操作审计", "SystemAuditLog", "PAGE_AUDIT_LOG",
                        "/api/v1/audit-logs"),
                sessionPage(),
                configPage(),
                readOnlyPage("SYSTEM.PAGE_CAPABILITY", "页面能力目录", "SystemPageCapabilityCatalog",
                        "PAGE_CAPABILITY_CATALOG", "/api/v1/authz/**"),
                readOnlyPage("SYSTEM.AUTHORIZATION_RESOURCE", "资源注册中心",
                        "SystemAuthorizationResourceCatalog",
                        "PAGE_AUTHORIZATION_RESOURCE_CATALOG", "/api/v1/authz/**")));
        pages.addAll(OwnerPageCapabilityDeclarations.pages());
        manifest.setPages(pages);
        return manifest;
    }

    private PageCapabilityManifestSyncRequest.Page crudPage(
            String prefix, String pageName, String menuKey, String pageResource,
            String collectionResource, String itemResource,
            String createName, String editName, String deleteName) {
        PageCapabilityManifestSyncRequest.Page page = page(prefix, pageName, menuKey);
        String access = prefix + ".ACCESS";
        String read = prefix + ".READ";
        page.setCapabilities(List.of(
                capability(access, "进入" + pageName, "ACCESS", 10,
                        List.of(), target(pageResource, "ACCESS")),
                capability(read, "查看" + pageName.replace("管理", "信息"), "READ", 20,
                        List.of(access), target(collectionResource, "GET")),
                capability(prefix + ".CREATE", createName, "OPERATION", 30,
                        List.of(access), target(collectionResource, "POST")),
                capability(prefix + ".EDIT", editName, "OPERATION", 40,
                        List.of(access, read), target(itemResource, "PUT")),
                capability(prefix + ".DELETE", deleteName, "OPERATION", 50,
                        List.of(access, read), target(itemResource, "DELETE"))));
        return page;
    }

    private PageCapabilityManifestSyncRequest.Page readOnlyPage(
            String prefix, String pageName, String menuKey, String pageResource, String readResource) {
        PageCapabilityManifestSyncRequest.Page page = page(prefix, pageName, menuKey);
        String access = prefix + ".ACCESS";
        List<PageCapabilityManifestSyncRequest.Capability> capabilities = new ArrayList<>(List.of(
                capability(access, "进入" + pageName, "ACCESS", 10,
                        List.of(), target(pageResource, "ACCESS")),
                capability(prefix + ".READ", "查看" + pageName, "READ", 20,
                        List.of(access), target(readResource, "GET"))));
        page.setCapabilities(capabilities);
        return page;
    }

    private PageCapabilityManifestSyncRequest.Page sessionPage() {
        PageCapabilityManifestSyncRequest.Page page = readOnlyPage(
                "SYSTEM.SESSION", "登录会话", "SystemSession", "PAGE_LOGIN_SESSION", "/api/v1/sessions");
        page.getCapabilities().add(capability("SYSTEM.SESSION.REVOKE", "失效会话", "OPERATION", 30,
                List.of("SYSTEM.SESSION.ACCESS", "SYSTEM.SESSION.READ"),
                target("/api/v1/sessions/*", "PUT")));
        return page;
    }

    private PageCapabilityManifestSyncRequest.Page tenantPage() {
        PageCapabilityManifestSyncRequest.Page page = readOnlyPage(
                "SYSTEM.TENANT", "租户管理", "tenant", "PAGE_TENANT_MANAGEMENT", "/api/v1/tenants");
        page.getCapabilities().addAll(List.of(
                capability("SYSTEM.TENANT.CREATE", "新增租户", "OPERATION", 30,
                        List.of("SYSTEM.TENANT.ACCESS"), target("/api/v1/tenants", "POST")),
                capability("SYSTEM.TENANT.EDIT", "编辑租户", "OPERATION", 40,
                        List.of("SYSTEM.TENANT.ACCESS", "SYSTEM.TENANT.READ"),
                        target("/api/v1/tenants/*", "PUT")),
                capability("SYSTEM.TENANT.SETTING_SAVE", "保存租户设置", "OPERATION", 50,
                        List.of("SYSTEM.TENANT.ACCESS", "SYSTEM.TENANT.READ"),
                        target("/api/v1/tenants/*", "PUT")),
                capability("SYSTEM.TENANT.SETTING_DELETE", "删除租户设置", "OPERATION", 60,
                        List.of("SYSTEM.TENANT.ACCESS", "SYSTEM.TENANT.READ"),
                        target("/api/v1/tenants/*", "DELETE"))));
        return page;
    }

    private PageCapabilityManifestSyncRequest.Page rolePage() {
        PageCapabilityManifestSyncRequest.Page page = crudPage(
                "SYSTEM.ROLE", "角色管理", "SystemRole", "PAGE_ROLE_MANAGEMENT",
                "/api/v1/roles", "/api/v1/roles/*", "新增角色", "编辑角色", "删除角色");
        List<PageCapabilityManifestSyncRequest.Capability> capabilities =
                new ArrayList<>(page.getCapabilities());
        capabilities.add(capability("SYSTEM.ROLE.AUTHORIZE", "配置角色权限", "OPERATION", 60,
                List.of("SYSTEM.ROLE.ACCESS", "SYSTEM.ROLE.READ"),
                target("/api/v1/authz/**", "PUT")));
        page.setCapabilities(capabilities);
        return page;
    }

    private PageCapabilityManifestSyncRequest.Page configPage() {
        PageCapabilityManifestSyncRequest.Page page = readOnlyPage(
                "SYSTEM.CONFIG", "系统参数", "SystemConfig", "PAGE_SYSTEM_CONFIG", "/api/v1/system-configs");
        page.getCapabilities().add(capability("SYSTEM.CONFIG.EDIT", "修改系统参数", "OPERATION", 30,
                List.of("SYSTEM.CONFIG.ACCESS", "SYSTEM.CONFIG.READ"),
                target("/api/v1/system-configs/*", "PUT")));
        return page;
    }

    private PageCapabilityManifestSyncRequest.Page page(String pageCode, String pageName, String menuKey) {
        PageCapabilityManifestSyncRequest.Page page = new PageCapabilityManifestSyncRequest.Page();
        page.setPageCode(pageCode);
        page.setPageName(pageName);
        page.setMenuKey(menuKey);
        return page;
    }

    private PageCapabilityManifestSyncRequest.Capability capability(
            String code, String name, String category, int sortOrder,
            List<String> dependencies, PageCapabilityManifestSyncRequest.Target... targets) {
        PageCapabilityManifestSyncRequest.Capability capability = new PageCapabilityManifestSyncRequest.Capability();
        capability.setCapabilityCode(code);
        capability.setCapabilityName(name);
        capability.setCategory(category);
        capability.setSortOrder(sortOrder);
        capability.setRequiredCapabilityCodes(dependencies);
        capability.setTargets(List.of(targets));
        return capability;
    }

    private PageCapabilityManifestSyncRequest.Target target(String resourceCode, String actionCode) {
        PageCapabilityManifestSyncRequest.Target target = new PageCapabilityManifestSyncRequest.Target();
        target.setResourceCode(resourceCode);
        target.setActionCode(actionCode);
        target.setTargetKind("GRANT");
        target.setRequired(true);
        return target;
    }
}
