-- Add missing PUT permission for enterprise authorization management.
-- The role authorization drawer toggles guard template status through
-- PUT /api/v1/authz/guard-templates/{id}/status.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P_AUTHZ_UPDATE', '/api/v1/authz/**', 'PUT', '更新企业授权配置')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('M_AUTHZ_BTN_UPDATE', 'M_AUTHZ', 'SystemAuthorizationUpdate', '更新', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 15, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P_AUTHZ_UPDATE', '/api/v1/authz/**:PUT', '企业授权-更新')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    hide_in_menu = EXCLUDED.hide_in_menu,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT 'AZ' || upper(substr(md5(role_id || ':M_AUTHZ_BTN_UPDATE'), 1, 24)),
       role_id,
       'M_AUTHZ_BTN_UPDATE',
       'SYSTEM',
       'SYSTEM'
FROM (
    SELECT DISTINCT role_id
    FROM sys_role_menu
    WHERE menu_id IN ('M_AUTHZ_BTN_WRITE', 'M_AUTHZ_BTN_UPDATE')
    UNION
    SELECT 'R001'
) role_scope
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, display_name,
    lifecycle_status, global_flag, last_synced_at, created_by, updated_by
) VALUES
    ('AR_AUTHZ_API', 'default', '/api/v1/authz/**', 'API', 'service-auth',
     '企业授权管理 API', 'ACTIVE', 1, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    display_name = EXCLUDED.display_name,
    lifecycle_status = EXCLUDED.lifecycle_status,
    global_flag = EXCLUDED.global_flag,
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
) VALUES
    ('AA_AUTHZ_API_PUT', 'default', '/api/v1/authz/**', 'PUT', 'API',
     '更新企业授权配置', 1, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:' || role_id || ':/api/v1/authz/**:PUT'), 1, 24)),
       'default',
       'ROLE',
       role_id,
       '/api/v1/authz/**',
       'PUT',
       'ALLOW',
       1,
       'Enterprise authorization update permission',
       'SYSTEM',
       'SYSTEM'
FROM (
    SELECT DISTINCT role_id
    FROM sys_role_menu
    WHERE menu_id = 'M_AUTHZ_BTN_UPDATE'
) role_scope
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
