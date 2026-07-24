-- Register LowcodeRuntimeApp menu so the named route survives frontend access filtering.
-- (permission_id column was retired in V64; sys_role_menu is now a VIEW derived from sys_auth_grant.)

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_code, description
) VALUES
    ('M036', 'M030', 'LowcodeRuntimeApp', 'Runtime App', '/lowcode/apps/:appKey',
     '/lowcode/runtime/app', 'mdi:application-braces-outline', NULL, NULL,
     'menu', 'lowcode', 41, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, '/api/v1/lowcode-runtime/apps:GET',
     'Lowcode runtime application page — hidden in menu, route only')
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
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
