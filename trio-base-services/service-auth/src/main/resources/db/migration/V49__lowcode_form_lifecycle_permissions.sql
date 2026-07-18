-- Lowcode form lifecycle permissions. This migration does not mutate lc_* tables.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P129', '/api/v1/forms/*', 'PUT', 'Update lowcode form draft'),
    ('P130', '/api/v1/forms/*/versions', 'POST', 'Derive lowcode form new version'),
    ('P131', '/api/v1/forms/*/versions', 'GET', 'List lowcode form versions'),
    ('P132', '/api/v1/forms/*/offline', 'PUT', 'Offline published lowcode form version')
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
    ('M031_BTN_UPDATE', 'M031', 'LowcodeFormUpdate', U&'\7F16\8F91', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P129', '/api/v1/forms/*:PUT', 'Update lowcode form draft'),
    ('M031_BTN_DERIVE_VERSION', 'M031', 'LowcodeFormDeriveVersion', U&'\6D3E\751F\65B0\7248\672C', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 40, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P130', '/api/v1/forms/*/versions:POST', 'Derive lowcode form new version'),
    ('M031_BTN_LIST_VERSION', 'M031', 'LowcodeFormListVersion', U&'\7248\672C\5217\8868', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 41, 0, 1, 0, 0, 1, 0, 1, 1,
     NULL, NULL, NULL, 'P131', '/api/v1/forms/*/versions:GET', 'List lowcode form versions'),
    ('M031_BTN_OFFLINE', 'M031', 'LowcodeFormOffline', U&'\4E0B\7EBF', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 50, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P132', '/api/v1/forms/*/offline:PUT', 'Offline lowcode form version')
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
    hide_in_breadcrumb = EXCLUDED.hide_in_breadcrumb,
    hide_in_tab = EXCLUDED.hide_in_tab,
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
    'M031_BTN_UPDATE',
    'M031_BTN_DERIVE_VERSION',
    'M031_BTN_LIST_VERSION',
    'M031_BTN_OFFLINE'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;
