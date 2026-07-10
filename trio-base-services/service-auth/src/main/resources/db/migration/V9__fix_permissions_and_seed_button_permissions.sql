-- Fix menu management permissions and seed common page action nodes.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('01HK153X0M000000000000000M', '/api/v1/menus/*', 'PUT', 'Edit menu')
ON CONFLICT DO NOTHING;

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('01HK153X190000000000000019', 'System:Menu', 'Create', 'Menu management create action'),
    ('01HK153X1A000000000000001A', 'System:Menu', 'Edit',   'Menu management edit action'),
    ('01HK153X1B000000000000001B', 'System:Menu', 'Delete', 'Menu management delete action'),
    ('01HK153X1C000000000000001C', 'System:User', 'Create', 'User management create action'),
    ('01HK153X1D000000000000001D', 'System:User', 'Edit',   'User management edit action'),
    ('01HK153X1E000000000000001E', 'System:User', 'Delete', 'User management delete action'),
    ('01HK153X1F000000000000001F', 'System:Role', 'Create', 'Role management create action'),
    ('01HK153X1G000000000000001G', 'System:Role', 'Edit',   'Role management edit action'),
    ('01HK153X1H000000000000001H', 'System:Role', 'Delete', 'Role management delete action')
ON CONFLICT DO NOTHING;

UPDATE sys_menu SET permission_id = '01HK153X190000000000000019' WHERE id = '01HK153X100000000000000010';
UPDATE sys_menu SET permission_id = '01HK153X1A000000000000001A' WHERE id = '01HK153X110000000000000011';
UPDATE sys_menu SET permission_id = '01HK153X1B000000000000001B' WHERE id = '01HK153X120000000000000012';

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('01HK153X130000000000000013', 'M004', 'SystemUserCreate', '新增', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '01HK153X1C000000000000001C', 'System:User:Create', 'User management create action'),
    ('01HK153X140000000000000014', 'M004', 'SystemUserEdit', '修改', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 20, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '01HK153X1D000000000000001D', 'System:User:Edit', 'User management edit action'),
    ('01HK153X150000000000000015', 'M004', 'SystemUserDelete', '删除', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 30, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '01HK153X1E000000000000001E', 'System:User:Delete', 'User management delete action'),
    ('01HK153X160000000000000016', 'M005', 'SystemRoleCreate', '新增', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '01HK153X1F000000000000001F', 'System:Role:Create', 'Role management create action'),
    ('01HK153X170000000000000017', 'M005', 'SystemRoleEdit', '修改', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 20, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '01HK153X1G000000000000001G', 'System:Role:Edit', 'Role management edit action'),
    ('01HK153X180000000000000018', 'M005', 'SystemRoleDelete', '删除', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 30, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, '01HK153X1H000000000000001H', 'System:Role:Delete', 'Role management delete action')
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
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description;

INSERT INTO sys_role_permission(id, role_id, permission_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || sp.id), 1, 24)),
       'R001', sp.id, 'SYSTEM', 'SYSTEM'
FROM sys_permission sp
ON CONFLICT (role_id, permission_id) DO NOTHING;
