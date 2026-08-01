-- Publish the read-only authorization resource catalog through governed navigation.

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, display_name,
    lifecycle_status, global_flag, last_synced_at, created_by, updated_by
) VALUES
    ('AR_AUTHZ_RESOURCE_CATALOG', 'default', 'PAGE_AUTHORIZATION_RESOURCE_CATALOG',
     'PAGE', 'service-auth', '授权资源目录页面', 'ACTIVE', 1,
     CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
) VALUES
    ('AA_AUTHZ_RESOURCE_CATALOG_ACCESS', 'default',
     'PAGE_AUTHORIZATION_RESOURCE_CATALOG', 'ACCESS', 'PAGE_ACCESS',
     '进入授权资源目录', 1, 'SYSTEM', 'SYSTEM')
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
    ('M_AUTHZ_RESOURCE_CATALOG', 'M008', 'SystemAuthorizationResourceCatalog',
     '授权资源目录', '/system/authorization-resource-catalog',
     '/system/authorization-resource-catalog/list', 'lucide:boxes', NULL, NULL,
     'menu', 'system', 65, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/authz/**:GET',
     '查看授权资源、动作、字段、守卫、Owner 与同步状态',
     'SYSTEM.AUTHORIZATION_RESOURCE')
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
    hide_in_menu = 0,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    page_code = EXCLUDED.page_code,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH admin_roles AS (
    SELECT id AS role_id, tenant_id
    FROM sys_role
    WHERE role_code = 'ADMIN'
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5(role.tenant_id || ':ROLE:' || role.role_id
           || ':PAGE_AUTHORIZATION_RESOURCE_CATALOG:ACCESS'), 1, 24)),
       role.tenant_id, 'ROLE', role.role_id, 'PAGE_AUTHORIZATION_RESOURCE_CATALOG',
       'ACCESS', 'ALLOW', 1, '进入授权资源目录', 'SYSTEM', 'SYSTEM'
FROM admin_roles role
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect)
DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
