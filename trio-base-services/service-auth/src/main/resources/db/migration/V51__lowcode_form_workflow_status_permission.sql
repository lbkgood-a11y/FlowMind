-- Lowcode form workflow-status permissions. service-auth owns only RBAC/menu seed data here.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P139', '/api/v1/forms/*/instances/*/workflow-status', 'PUT', 'Update lowcode form instance workflow status')
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
    ('M034_BTN_WORKFLOW_STATUS', 'M030', 'LowcodeFormWorkflowStatusUpdate', U&'\6D41\7A0B\72B6\6001\56DE\5199',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 90, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P139', '/api/v1/forms/*/instances/*/workflow-status:PUT',
     'Update lowcode form instance workflow status')
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
VALUES ('01' || upper(substr(md5('R001:M034_BTN_WORKFLOW_STATUS'), 1, 24)),
        'R001', 'M034_BTN_WORKFLOW_STATUS', 'SYSTEM', 'SYSTEM')
ON CONFLICT (role_id, menu_id) DO NOTHING;
