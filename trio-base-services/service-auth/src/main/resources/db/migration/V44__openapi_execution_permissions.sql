INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P123', '/api/v1/openapi/management/executions/diagnostics', 'POST', 'Capture redacted execution diagnostics')
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES
    ('OA_MGMT_124', 'OA_MGMT_100', 'OpenApiExecutionDiagnostic', 'Capture diagnostics', NULL, NULL, NULL,
     'button', 'integration', 240, 0, 1, 1, 0, 0, 0, 'P123', '/api/v1/openapi/management/executions/diagnostics:POST', 'Execution diagnostics')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT 'OA' || upper(substr(md5('R001' || ':' || menu.id), 1, 24)),
       'R001', menu.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu menu WHERE menu.id IN ('OA_MGMT_124')
ON CONFLICT (role_id, menu_id) DO NOTHING;
