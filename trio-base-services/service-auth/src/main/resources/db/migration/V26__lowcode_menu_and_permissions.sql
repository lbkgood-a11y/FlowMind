-- Lowcode form management menu and workflow-mount permissions.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P084', '/api/v1/forms/*/publish', 'PUT', 'Publish lowcode form definition')
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
    ('M030', NULL, 'LowcodeCenter', U&'\5FEB\901F\5F00\53D1', '/lowcode', NULL,
     'mdi:application-braces-outline', NULL, NULL,
     'catalog', 'lowcode', 70, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'Lowcode form and page development center'),

    ('M031', 'M030', 'LowcodeForm', U&'\8868\5355\7BA1\7406', '/lowcode/form', '/lowcode/form/list',
     'mdi:form-select', NULL, NULL,
     'menu', 'lowcode', 10, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P011', '/api/v1/forms:GET', 'Manage lowcode forms for workflow mounting'),

    ('M031_BTN_CREATE', 'M031', 'LowcodeFormCreate', U&'\65B0\589E', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P012', '/api/v1/forms:POST', 'Create lowcode form definition'),

    ('M031_BTN_PUBLISH', 'M031', 'LowcodeFormPublish', U&'\53D1\5E03', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P084', '/api/v1/forms/*/publish:PUT', 'Publish lowcode form definition')
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
WHERE m.id IN ('M030', 'M031', 'M031_BTN_CREATE', 'M031_BTN_PUBLISH')
ON CONFLICT (role_id, menu_id) DO NOTHING;
