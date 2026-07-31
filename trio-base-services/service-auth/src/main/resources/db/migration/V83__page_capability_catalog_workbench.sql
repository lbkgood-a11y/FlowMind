-- Dedicated read-only Page Capability Catalog navigation and page access resource.

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, display_name,
    lifecycle_status, global_flag, last_synced_at, created_by, updated_by
) VALUES
    ('AR_PAGE_CAP_CATALOG', 'default', 'PAGE_CAPABILITY_CATALOG', 'PAGE', 'service-auth',
     '能力目录页面', 'ACTIVE', 1, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, display_name,
    lifecycle_status, global_flag, last_synced_at, created_by, updated_by
) VALUES
    ('AR_AUTHZ_API_READ', 'default', '/api/v1/authz/**', 'API', 'service-auth',
     '企业授权只读 API', 'ACTIVE', 1, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
) VALUES
    ('AA_PAGE_CAP_ACCESS', 'default', 'PAGE_CAPABILITY_CATALOG', 'ACCESS', 'PAGE_ACCESS',
     '进入能力目录', 1, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
) VALUES
    ('AA_AUTHZ_API_GET', 'default', '/api/v1/authz/**', 'GET', 'API',
     '查看企业授权与能力目录', 1, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_code, description, page_code
) VALUES
    ('M_PAGE_CAP_CATALOG', 'M008', 'SystemPageCapabilityCatalog', '能力目录',
     '/system/capability-catalog', '/system/capability-catalog/list',
     'lucide:panels-top-left', NULL, NULL, 'menu', 'system', 64, 1, 1, 0, 0,
     0, 0, 0, 0, NULL, NULL, NULL, '/api/v1/authz/**:GET',
     '查看页面能力目录、版本生命周期和就绪诊断', 'SYSTEM.PAGE_CAPABILITY')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    page_code = EXCLUDED.page_code,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH catalog_actions(resource_code, action_code, description) AS (
    VALUES
        ('PAGE_CAPABILITY_CATALOG', 'ACCESS', '进入能力目录页面'),
        ('/api/v1/authz/**', 'GET', '查看企业授权与能力目录')
),
admin_actions AS (
    SELECT role.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role
    CROSS JOIN catalog_actions action
    WHERE role.role_code = 'ADMIN'
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:' || role_id || ':' || resource_code || ':' || action_code), 1, 24)),
       'default', 'ROLE', role_id, resource_code, action_code,
       'ALLOW', 1, description, 'SYSTEM', 'SYSTEM'
FROM admin_actions
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
