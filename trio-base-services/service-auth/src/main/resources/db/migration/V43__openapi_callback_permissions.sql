INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P118', '/api/v1/openapi/management/callback-profiles', 'GET', 'View callback profiles'),
    ('P119', '/api/v1/openapi/management/callback-profiles', 'POST', 'Manage callback profiles'),
    ('P120', '/api/v1/openapi/management/callback-quarantine', 'GET', 'View callback quarantine'),
    ('P121', '/api/v1/openapi/management/callback-quarantine', 'POST', 'Resolve callback quarantine')
ON CONFLICT (id) DO UPDATE SET resource = EXCLUDED.resource, action = EXCLUDED.action,
    description = EXCLUDED.description, updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES
    ('OA_MGMT_119', 'OA_MGMT_100', 'OpenApiCallbackRead', 'View callbacks', NULL, NULL, NULL,
     'button', 'integration', 190, 0, 1, 1, 0, 0, 0, 'P118', '/api/v1/openapi/management/callback-profiles:GET', 'Callback profile read'),
    ('OA_MGMT_120', 'OA_MGMT_100', 'OpenApiCallbackManage', 'Manage callbacks', NULL, NULL, NULL,
     'button', 'integration', 200, 0, 1, 1, 0, 0, 0, 'P119', '/api/v1/openapi/management/callback-profiles:POST', 'Callback profile manage'),
    ('OA_MGMT_121', 'OA_MGMT_100', 'OpenApiCallbackQuarantineRead', 'View callback quarantine', NULL, NULL, NULL,
     'button', 'integration', 210, 0, 1, 1, 0, 0, 0, 'P120', '/api/v1/openapi/management/callback-quarantine:GET', 'Callback quarantine read'),
    ('OA_MGMT_122', 'OA_MGMT_100', 'OpenApiCallbackQuarantineManage', 'Resolve callback quarantine', NULL, NULL, NULL,
     'button', 'integration', 220, 0, 1, 1, 0, 0, 0, 'P121', '/api/v1/openapi/management/callback-quarantine:POST', 'Callback quarantine manage')
ON CONFLICT (id) DO UPDATE SET permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code, visible = EXCLUDED.visible,
    hide_in_menu = EXCLUDED.hide_in_menu, status = EXCLUDED.status,
    updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT 'OA' || upper(substr(md5('R001' || ':' || menu.id), 1, 24)),
       'R001', menu.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu menu
WHERE menu.id IN ('OA_MGMT_119', 'OA_MGMT_120', 'OA_MGMT_121', 'OA_MGMT_122')
ON CONFLICT (role_id, menu_id) DO NOTHING;
