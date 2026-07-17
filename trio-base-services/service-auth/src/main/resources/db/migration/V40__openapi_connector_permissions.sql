INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P112', '/api/v1/openapi/management/connectors', 'GET', 'View integration connectors'),
    ('P113', '/api/v1/openapi/management/connectors', 'POST', 'Manage integration connectors')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES
    ('OA_MGMT_113', 'OA_MGMT_100', 'OpenApiConnectorRead', 'View connectors', NULL, NULL, NULL,
     'button', 'integration', 130, 0, 1, 1, 0, 0, 0, 'P112', '/api/v1/openapi/management/connectors:GET', 'OpenAPI connector read permission'),
    ('OA_MGMT_114', 'OA_MGMT_100', 'OpenApiConnectorManage', 'Manage connectors', NULL, NULL, NULL,
     'button', 'integration', 140, 0, 1, 1, 0, 0, 0, 'P113', '/api/v1/openapi/management/connectors:POST', 'OpenAPI connector manage permission')
ON CONFLICT (id) DO UPDATE SET
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    visible = EXCLUDED.visible,
    hide_in_menu = EXCLUDED.hide_in_menu,
    status = EXCLUDED.status,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT 'OA' || upper(substr(md5('R001' || ':' || menu.id), 1, 24)),
       'R001', menu.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu menu
WHERE menu.id IN ('OA_MGMT_113', 'OA_MGMT_114')
ON CONFLICT (role_id, menu_id) DO NOTHING;
