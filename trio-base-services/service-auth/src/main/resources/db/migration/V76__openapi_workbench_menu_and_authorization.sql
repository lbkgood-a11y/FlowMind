-- Restore a dedicated OpenAPI workbench menu and move OpenAPI operations
-- authorization fully onto sys_auth_resource/action/grant.

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_code, description, created_by, updated_by
) VALUES
    ('OA_WORKBENCH', 'OA_MGMT_100', 'OpenApiIntegrationWorkbench', '集成工作台',
     '/openapi-operations/workbench', '/openapi/workbench/index', 'mdi:monitor-dashboard', NULL, NULL,
     'menu', 'integration', 5, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/openapi/management/operations:GET',
     'OpenAPI 结构、映射、路由、编排、应用、策略、执行与回调隔离运维入口', 'SYSTEM', 'SYSTEM'),

    ('OA_BTN_ACTION_READ', 'OA_WORKBENCH', 'OpenApiActionDefinitionRead', '读取 OpenAPI Action',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'integration', 10, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/openapi/management/actions:GET',
     '读取 OpenAPI owner-hosted Action 定义', 'SYSTEM', 'SYSTEM'),

    ('OA_BTN_ACTION_DISPATCH', 'OA_WORKBENCH', 'OpenApiActionDispatch', '执行 OpenAPI Action',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'integration', 20, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/openapi/management/actions:POST',
     '校验并执行 OpenAPI owner-hosted Action', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    active_icon = EXCLUDED.active_icon,
    active_path = EXCLUDED.active_path,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    keep_alive = EXCLUDED.keep_alive,
    affix_tab = EXCLUDED.affix_tab,
    hide_in_menu = EXCLUDED.hide_in_menu,
    hide_children_in_menu = EXCLUDED.hide_children_in_menu,
    hide_in_breadcrumb = EXCLUDED.hide_in_breadcrumb,
    hide_in_tab = EXCLUDED.hide_in_tab,
    badge = EXCLUDED.badge,
    badge_type = EXCLUDED.badge_type,
    badge_variant = EXCLUDED.badge_variant,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_menu
SET component = '/openapi/lifecycle/overview',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'OA_MGMT_WORKBENCH';

UPDATE sys_menu
SET component = '/openapi/operations/executions',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'OA_EXECUTIONS';

UPDATE sys_menu
SET component = '/openapi/operations/quarantine',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'OA_QUARANTINE';

WITH openapi_resources(resource_code, resource_type, owner_service, business_object_id, display_name) AS (
    VALUES
        ('/api/v1/openapi/management/structures', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI structure detail'),
        ('/api/v1/openapi/management/structures/*/publish', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI structure publish'),
        ('/api/v1/openapi/management/structures/*/deprecate', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI structure deprecate'),
        ('/api/v1/openapi/management/structures/*/archive', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI structure archive'),
        ('/api/v1/openapi/management/mappings', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI mappings'),
        ('/api/v1/openapi/management/connectors', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI connectors'),
        ('/api/v1/openapi/management/routes', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI routes and releases'),
        ('/api/v1/openapi/management/orchestrations', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI orchestrations'),
        ('/api/v1/openapi/management/callback-profiles', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI callback profiles'),
        ('/api/v1/openapi/management/callback-quarantine', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI callback quarantine'),
        ('/api/v1/openapi/management/products', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI products and policies'),
        ('/api/v1/openapi/management/applications', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI applications and subscriptions'),
        ('/api/v1/openapi/management/approvals', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI production approvals'),
        ('/api/v1/openapi/management/executions', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI execution center'),
        ('/api/v1/openapi/management/executions/diagnostics', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI execution diagnostics'),
        ('/api/v1/openapi/management/operations', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI operations catalog'),
        ('/api/v1/openapi/management/actions', 'API_OPERATION', 'service-openapi', NULL, 'OpenAPI owner-hosted actions')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default',
       resource_code,
       resource_type,
       owner_service,
       business_object_id,
       display_name,
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM openapi_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH openapi_actions(resource_code, action_code, action_category, description) AS (
    VALUES
        ('/api/v1/openapi/management/structures', 'GET', 'API', 'View OpenAPI structures'),
        ('/api/v1/openapi/management/structures', 'POST', 'API', 'Create OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*', 'PUT', 'API', 'Edit OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*/publish', 'POST', 'API', 'Publish OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*/deprecate', 'POST', 'API', 'Deprecate OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*/archive', 'POST', 'API', 'Archive OpenAPI structures'),
        ('/api/v1/openapi/management/mappings', 'GET', 'API', 'View OpenAPI mappings'),
        ('/api/v1/openapi/management/mappings', 'POST', 'API', 'Manage OpenAPI mappings'),
        ('/api/v1/openapi/management/connectors', 'GET', 'API', 'View OpenAPI connectors'),
        ('/api/v1/openapi/management/connectors', 'POST', 'API', 'Manage OpenAPI connectors'),
        ('/api/v1/openapi/management/routes', 'GET', 'API', 'View OpenAPI routes and releases'),
        ('/api/v1/openapi/management/routes', 'POST', 'API', 'Manage OpenAPI routes and releases'),
        ('/api/v1/openapi/management/orchestrations', 'GET', 'API', 'View OpenAPI orchestrations'),
        ('/api/v1/openapi/management/orchestrations', 'POST', 'API', 'Manage OpenAPI orchestrations'),
        ('/api/v1/openapi/management/callback-profiles', 'GET', 'API', 'View OpenAPI callback profiles'),
        ('/api/v1/openapi/management/callback-profiles', 'POST', 'API', 'Manage OpenAPI callback profiles'),
        ('/api/v1/openapi/management/callback-quarantine', 'GET', 'API', 'View OpenAPI callback quarantine'),
        ('/api/v1/openapi/management/callback-quarantine', 'POST', 'API', 'Resolve OpenAPI callback quarantine'),
        ('/api/v1/openapi/management/products', 'GET', 'API', 'View OpenAPI products and policies'),
        ('/api/v1/openapi/management/products', 'POST', 'API', 'Manage OpenAPI products and policies'),
        ('/api/v1/openapi/management/applications', 'GET', 'API', 'View OpenAPI applications and subscriptions'),
        ('/api/v1/openapi/management/applications', 'POST', 'API', 'Manage OpenAPI applications and subscriptions'),
        ('/api/v1/openapi/management/approvals', 'POST', 'API', 'Approve OpenAPI production assets'),
        ('/api/v1/openapi/management/executions', 'GET', 'API', 'View OpenAPI executions'),
        ('/api/v1/openapi/management/executions/diagnostics', 'POST', 'API', 'Capture OpenAPI execution diagnostics'),
        ('/api/v1/openapi/management/operations', 'GET', 'API', 'View OpenAPI lifecycle operations'),
        ('/api/v1/openapi/management/actions', 'GET', 'ACTION_RUNTIME', 'Read OpenAPI action definitions'),
        ('/api/v1/openapi/management/actions', 'POST', 'ACTION_RUNTIME', 'Validate and dispatch OpenAPI actions')
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5('default:' || resource_code || ':' || action_code), 1, 24)),
       'default',
       resource_code,
       action_code,
       action_category,
       description,
       1,
       'SYSTEM',
       'SYSTEM'
FROM openapi_actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH openapi_actions(resource_code, action_code, description) AS (
    VALUES
        ('/api/v1/openapi/management/structures', 'GET', 'View OpenAPI structures'),
        ('/api/v1/openapi/management/structures', 'POST', 'Create OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*', 'PUT', 'Edit OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*/publish', 'POST', 'Publish OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*/deprecate', 'POST', 'Deprecate OpenAPI structures'),
        ('/api/v1/openapi/management/structures/*/archive', 'POST', 'Archive OpenAPI structures'),
        ('/api/v1/openapi/management/mappings', 'GET', 'View OpenAPI mappings'),
        ('/api/v1/openapi/management/mappings', 'POST', 'Manage OpenAPI mappings'),
        ('/api/v1/openapi/management/connectors', 'GET', 'View OpenAPI connectors'),
        ('/api/v1/openapi/management/connectors', 'POST', 'Manage OpenAPI connectors'),
        ('/api/v1/openapi/management/routes', 'GET', 'View OpenAPI routes and releases'),
        ('/api/v1/openapi/management/routes', 'POST', 'Manage OpenAPI routes and releases'),
        ('/api/v1/openapi/management/orchestrations', 'GET', 'View OpenAPI orchestrations'),
        ('/api/v1/openapi/management/orchestrations', 'POST', 'Manage OpenAPI orchestrations'),
        ('/api/v1/openapi/management/callback-profiles', 'GET', 'View OpenAPI callback profiles'),
        ('/api/v1/openapi/management/callback-profiles', 'POST', 'Manage OpenAPI callback profiles'),
        ('/api/v1/openapi/management/callback-quarantine', 'GET', 'View OpenAPI callback quarantine'),
        ('/api/v1/openapi/management/callback-quarantine', 'POST', 'Resolve OpenAPI callback quarantine'),
        ('/api/v1/openapi/management/products', 'GET', 'View OpenAPI products and policies'),
        ('/api/v1/openapi/management/products', 'POST', 'Manage OpenAPI products and policies'),
        ('/api/v1/openapi/management/applications', 'GET', 'View OpenAPI applications and subscriptions'),
        ('/api/v1/openapi/management/applications', 'POST', 'Manage OpenAPI applications and subscriptions'),
        ('/api/v1/openapi/management/approvals', 'POST', 'Approve OpenAPI production assets'),
        ('/api/v1/openapi/management/executions', 'GET', 'View OpenAPI executions'),
        ('/api/v1/openapi/management/executions/diagnostics', 'POST', 'Capture OpenAPI execution diagnostics'),
        ('/api/v1/openapi/management/operations', 'GET', 'View OpenAPI lifecycle operations'),
        ('/api/v1/openapi/management/actions', 'GET', 'Read OpenAPI action definitions'),
        ('/api/v1/openapi/management/actions', 'POST', 'Validate and dispatch OpenAPI actions')
),
role_actions AS (
    SELECT role_row.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role_row
    CROSS JOIN openapi_actions action
    WHERE role_row.role_code IN ('ADMIN', 'TENANT_ADMIN')
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:' || role_id || ':' || resource_code || ':' || action_code), 1, 24)),
       'default',
       'ROLE',
       role_id,
       resource_code,
       action_code,
       'ALLOW',
       1,
       description,
       'SYSTEM',
       'SYSTEM'
FROM role_actions
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
