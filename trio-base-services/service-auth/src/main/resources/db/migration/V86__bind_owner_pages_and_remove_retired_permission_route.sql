-- Bind every active page-bearing navigation row to an explicit Owner declaration.
-- The retired permission-management route is removed instead of being promoted
-- into the new Page Capability model.

DELETE FROM sys_menu WHERE id = 'M007' OR menu_key = 'permissions';

WITH bindings(menu_key, page_code) AS (
    VALUES
        ('AiAssistantWorkbench', 'AI.ASSISTANT'),
        ('DataCatalog', 'DATA.CATALOG'),
        ('HybridQuery', 'DATA.HYBRID_QUERY'),
        ('AntDesignDemos', 'FRONTEND.ANT_DESIGN_DEMOS'),
        ('VbenAbout', 'FRONTEND.ABOUT'),
        ('Analytics', 'DASHBOARD.ANALYTICS'),
        ('Workspace', 'DASHBOARD.WORKSPACE'),
        ('OpenApiApplications', 'OPENAPI.APPLICATIONS'),
        ('OpenApiCallbackQuarantine', 'OPENAPI.CALLBACK_QUARANTINE'),
        ('OpenApiCallbacks', 'OPENAPI.CALLBACKS'),
        ('OpenApiConnectors', 'OPENAPI.CONNECTORS'),
        ('OpenApiExecutions', 'OPENAPI.EXECUTIONS'),
        ('OpenApiIntegrationWorkbench', 'OPENAPI.WORKBENCH'),
        ('OpenApiLifecycleOverview', 'OPENAPI.LIFECYCLE'),
        ('OpenApiMappings', 'OPENAPI.MAPPINGS'),
        ('OpenApiOrchestrations', 'OPENAPI.ORCHESTRATIONS'),
        ('OpenApiPolicies', 'OPENAPI.POLICIES'),
        ('OpenApiProducts', 'OPENAPI.PRODUCTS'),
        ('OpenApiRoutes', 'OPENAPI.ROUTES'),
        ('OpenApiStructures', 'OPENAPI.STRUCTURES'),
        ('OpenApiSubscriptions', 'OPENAPI.SUBSCRIPTIONS'),
        ('OpenApiValueMaps', 'OPENAPI.VALUE_MAPS'),
        ('LowcodeAppCenter', 'LOWCODE.APP_CENTER'),
        ('LowcodeApplication', 'LOWCODE.APPLICATION'),
        ('LowcodeExpense', 'LOWCODE.EXPENSE'),
        ('LowcodeForm', 'LOWCODE.FORM'),
        ('LowcodeLeave', 'LOWCODE.LEAVE'),
        ('LowcodeRuntimeApp', 'LOWCODE.RUNTIME_APP'),
        ('OperationsAnnouncement', 'OPS.ANNOUNCEMENT'),
        ('OperationsFile', 'OPS.FILE'),
        ('OperationsImportExport', 'OPS.IMPORT_EXPORT'),
        ('OperationsJob', 'OPS.JOB'),
        ('OperationsMessage', 'OPS.MESSAGE'),
        ('ProcessDesigner', 'WORKFLOW.DESIGNER'),
        ('ProcessInstance', 'WORKFLOW.INSTANCE'),
        ('ProcessPackage', 'WORKFLOW.PACKAGE'),
        ('TaskCenter', 'WORKFLOW.TASK'),
        ('SystemAuthz', 'SYSTEM.AUTHZ'),
        ('SystemDataPermission', 'SYSTEM.DATA_PERMISSION'),
        ('SystemDictionary', 'SYSTEM.DICTIONARY'),
        ('SystemOrg', 'SYSTEM.ORG'),
        ('Profile', 'FRONTEND.PROFILE')
)
UPDATE sys_menu menu
SET page_code = bindings.page_code,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
FROM bindings
WHERE menu.menu_key = bindings.menu_key;

CREATE INDEX IF NOT EXISTS idx_sys_menu_page_code ON sys_menu(page_code);
