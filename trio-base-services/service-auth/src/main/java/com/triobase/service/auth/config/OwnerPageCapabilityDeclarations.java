package com.triobase.service.auth.config;

import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;

import java.util.List;

/**
 * Explicit page declarations contributed by platform Owner services.
 *
 * <p>The declarations are composed into the single active platform catalog because
 * the current authorization release contract is catalog-version scoped. Targets
 * remain explicit registered resource/action facts; no route or label inference is
 * performed at runtime.</p>
 */
final class OwnerPageCapabilityDeclarations {

    private OwnerPageCapabilityDeclarations() {
    }

    static List<PageCapabilityManifestSyncRequest.Page> pages() {
        return List.of(
                page("AI.ASSISTANT", "AI 助手", "AiAssistantWorkbench", "ai-agent-orchestrator", "/ai/assistant", "GET"),
                page("DATA.CATALOG", "数据目录", "DataCatalog", "data-analytics", "/api/v1/data/datasets", "GET"),
                page("DATA.HYBRID_QUERY", "混合查询", "HybridQuery", "data-analytics", "/api/v1/data/query/hybrid", "POST"),
                page("FRONTEND.ANT_DESIGN_DEMOS", "Ant Design", "AntDesignDemos", "web-antd", "/demos/ant-design", "GET"),
                page("FRONTEND.ABOUT", "关于", "VbenAbout", "web-antd", "/vben-admin/about", "GET"),
                page("DASHBOARD.ANALYTICS", "分析页", "Analytics", "web-antd", "/dashboard/analytics", "GET"),
                page("DASHBOARD.WORKSPACE", "工作台", "Workspace", "web-antd", "/dashboard/workspace", "GET"),
                page("OPENAPI.APPLICATIONS", "接入应用", "OpenApiApplications", "service-openapi", "/api/v1/openapi/management/applications", "GET"),
                page("OPENAPI.CALLBACK_QUARANTINE", "回调隔离区", "OpenApiCallbackQuarantine", "service-openapi", "/api/v1/openapi/management/callback-quarantine", "GET"),
                page("OPENAPI.CALLBACKS", "回调配置", "OpenApiCallbacks", "service-openapi", "/api/v1/openapi/management/callback-profiles", "GET"),
                page("OPENAPI.CONNECTORS", "连接器", "OpenApiConnectors", "service-openapi", "/api/v1/openapi/management/connectors", "GET"),
                page("OPENAPI.EXECUTIONS", "执行中心", "OpenApiExecutions", "service-openapi", "/api/v1/openapi/management/executions", "GET"),
                page("OPENAPI.WORKBENCH", "集成工作台", "OpenApiIntegrationWorkbench", "service-openapi", "/api/v1/openapi/management/operations", "GET"),
                page("OPENAPI.LIFECYCLE", "生命周期总览", "OpenApiLifecycleOverview", "service-openapi", "/api/v1/openapi/management/operations", "GET"),
                page("OPENAPI.MAPPINGS", "字段映射", "OpenApiMappings", "service-openapi", "/api/v1/openapi/management/mappings", "GET"),
                page("OPENAPI.ORCHESTRATIONS", "流程编排", "OpenApiOrchestrations", "service-openapi", "/api/v1/openapi/management/orchestrations", "GET"),
                page("OPENAPI.POLICIES", "流控与安全策略", "OpenApiPolicies", "service-openapi", "/api/v1/openapi/management/products", "GET"),
                page("OPENAPI.PRODUCTS", "API 产品", "OpenApiProducts", "service-openapi", "/api/v1/openapi/management/products", "GET"),
                page("OPENAPI.ROUTES", "路由与发布", "OpenApiRoutes", "service-openapi", "/api/v1/openapi/management/routes", "GET"),
                page("OPENAPI.STRUCTURES", "标准与外部结构", "OpenApiStructures", "service-openapi", "/api/v1/openapi/management/structures", "GET"),
                page("OPENAPI.SUBSCRIPTIONS", "订阅与审批", "OpenApiSubscriptions", "service-openapi", "/api/v1/openapi/management/applications", "GET"),
                page("OPENAPI.VALUE_MAPS", "值映射", "OpenApiValueMaps", "service-openapi", "/api/v1/openapi/management/mappings", "GET"),
                page("LOWCODE.APP_CENTER", "应用中心", "LowcodeAppCenter", "service-lowcode", "/api/v1/lowcode-runtime/apps", "GET"),
                page("LOWCODE.APPLICATION", "应用管理", "LowcodeApplication", "service-lowcode", "/api/v1/lowcode-applications", "GET"),
                page("LOWCODE.EXPENSE", "费用报销", "LowcodeExpense", "service-lowcode", "/api/v1/forms/expense/instances", "GET"),
                page("LOWCODE.FORM", "表单管理", "LowcodeForm", "service-lowcode", "/api/v1/forms", "GET"),
                page("LOWCODE.LEAVE", "请假申请", "LowcodeLeave", "service-lowcode", "/api/v1/lowcode-runtime/apps/*", "GET"),
                page("LOWCODE.RUNTIME_APP", "Runtime App", "LowcodeRuntimeApp", "service-lowcode", "/api/v1/lowcode-runtime/apps", "GET"),
                page("OPS.ANNOUNCEMENT", "通知公告", "OperationsAnnouncement", "service-ops", "/api/v1/announcements", "GET"),
                page("OPS.FILE", "文件中心", "OperationsFile", "service-ops", "/api/v1/files", "GET"),
                page("OPS.IMPORT_EXPORT", "导入导出", "OperationsImportExport", "service-ops", "/api/v1/import-export-tasks", "GET"),
                page("OPS.JOB", "后台任务", "OperationsJob", "service-ops", "/api/v1/jobs", "GET"),
                page("OPS.MESSAGE", "站内消息", "OperationsMessage", "service-ops", "/api/v1/messages", "GET"),
                page("WORKFLOW.DESIGNER", "流程设计器", "ProcessDesigner", "service-workflow-engine", "/api/v1/process-packages", "GET"),
                page("WORKFLOW.INSTANCE", "流程实例", "ProcessInstance", "service-workflow-engine", "/api/v1/process-instances", "GET"),
                page("WORKFLOW.PACKAGE", "流程包管理", "ProcessPackage", "service-workflow-engine", "/api/v1/process-packages", "GET"),
                page("WORKFLOW.TASK", "任务中心", "TaskCenter", "service-workflow-engine", "/api/v1/tasks/my-pending", "GET"),
                page("SYSTEM.AUTHZ", "企业授权", "SystemAuthz", "service-auth", "/api/v1/authz/**", "GET"),
                page("SYSTEM.DATA_PERMISSION", "数据权限", "SystemDataPermission", "service-auth", "/api/v1/data-policies", "GET"),
                page("SYSTEM.DICTIONARY", "数据字典", "SystemDictionary", "service-auth", "/api/v1/dictionaries", "GET"),
                page("SYSTEM.ORG", "组织管理", "SystemOrg", "service-org", "/api/v1/org/units", "GET"),
                page("FRONTEND.PROFILE", "个人中心", "Profile", "web-antd", "/profile", "GET")
        );
    }

    private static PageCapabilityManifestSyncRequest.Page page(
            String pageCode, String pageName, String menuKey, String owner,
            String resourceCode, String actionCode) {
        PageCapabilityManifestSyncRequest.Page page = new PageCapabilityManifestSyncRequest.Page();
        page.setPageCode(pageCode);
        page.setPageName(pageName);
        page.setMenuKey(menuKey);
        page.setMetadataJson("{\"ownerService\":\"" + owner + "\"}");
        String accessCode = pageCode + ".ACCESS";
        page.setCapabilities(List.of(
                capability(accessCode, "进入" + pageName, "ACCESS", 10, List.of(), resourceCode, actionCode),
                capability(pageCode + ".READ", "查看" + pageName, "READ", 20,
                        List.of(accessCode), resourceCode, actionCode)));
        return page;
    }

    private static PageCapabilityManifestSyncRequest.Capability capability(
            String code, String name, String category, int sortOrder, List<String> dependencies,
            String resourceCode, String actionCode) {
        PageCapabilityManifestSyncRequest.Target target = new PageCapabilityManifestSyncRequest.Target();
        target.setResourceCode(resourceCode);
        target.setActionCode(actionCode);
        target.setTargetKind("GRANT");
        target.setRequired(true);
        PageCapabilityManifestSyncRequest.Capability capability = new PageCapabilityManifestSyncRequest.Capability();
        capability.setCapabilityCode(code);
        capability.setCapabilityName(name);
        capability.setCategory(category);
        capability.setSortOrder(sortOrder);
        capability.setRequiredCapabilityCodes(dependencies);
        capability.setTargets(List.of(target));
        return capability;
    }
}
