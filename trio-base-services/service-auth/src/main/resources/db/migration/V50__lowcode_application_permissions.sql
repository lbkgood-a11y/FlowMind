-- Lowcode application management permissions. service-auth owns only RBAC/menu seed data here.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P133', '/api/v1/lowcode-applications', 'GET', 'Query lowcode applications'),
    ('P134', '/api/v1/lowcode-applications', 'POST', 'Create lowcode application'),
    ('P135', '/api/v1/lowcode-applications/*', 'PUT', 'Update lowcode application draft'),
    ('P136', '/api/v1/lowcode-applications/*/versions', 'POST', 'Derive lowcode application version'),
    ('P137', '/api/v1/lowcode-applications/*/publish', 'PUT', 'Publish lowcode application version'),
    ('P138', '/api/v1/lowcode-applications/*/offline', 'PUT', 'Offline lowcode application version')
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
    ('M033', 'M030', 'LowcodeApplication', U&'\5E94\7528\7BA1\7406', '/lowcode/application', '/lowcode/application/list',
     'mdi:application-cog-outline', NULL, NULL,
     'menu', 'lowcode', 30, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P133', '/api/v1/lowcode-applications:GET',
     'Manage rapid-development applications'),
    ('M033_BTN_CREATE', 'M033', 'LowcodeApplicationCreate', U&'\65B0\5EFA', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P134', '/api/v1/lowcode-applications:POST',
     'Create lowcode application'),
    ('M033_BTN_UPDATE', 'M033', 'LowcodeApplicationUpdate', U&'\7F16\8F91', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P135', '/api/v1/lowcode-applications/*:PUT',
     'Update lowcode application draft'),
    ('M033_BTN_DERIVE', 'M033', 'LowcodeApplicationDeriveVersion', U&'\6D3E\751F\7248\672C', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P136', '/api/v1/lowcode-applications/*/versions:POST',
     'Derive lowcode application version'),
    ('M033_BTN_PUBLISH', 'M033', 'LowcodeApplicationPublish', U&'\53D1\5E03', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 40, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P137', '/api/v1/lowcode-applications/*/publish:PUT',
     'Publish lowcode application'),
    ('M033_BTN_OFFLINE', 'M033', 'LowcodeApplicationOffline', U&'\4E0B\7EBF', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 50, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P138', '/api/v1/lowcode-applications/*/offline:PUT',
     'Offline lowcode application')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
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
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001', m.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu m
WHERE m.id IN (
    'M033',
    'M033_BTN_CREATE',
    'M033_BTN_UPDATE',
    'M033_BTN_DERIVE',
    'M033_BTN_PUBLISH',
    'M033_BTN_OFFLINE'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;
