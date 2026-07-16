-- OpenAPI integration service management permissions.
INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P100', '/api/v1/openapi/management/structures', 'GET', 'View integration structures'),
    ('P101', '/api/v1/openapi/management/structures', 'POST', 'Create integration structures'),
    ('P102', '/api/v1/openapi/management/structures/*', 'PUT', 'Edit integration structures'),
    ('P103', '/api/v1/openapi/management/structures/*/publish', 'POST', 'Publish integration structures'),
    ('P104', '/api/v1/openapi/management/mappings', 'GET', 'View integration mappings'),
    ('P105', '/api/v1/openapi/management/mappings', 'POST', 'Manage integration mappings'),
    ('P106', '/api/v1/openapi/management/products', 'GET', 'View API products'),
    ('P107', '/api/v1/openapi/management/products', 'POST', 'Manage API products'),
    ('P108', '/api/v1/openapi/management/applications', 'GET', 'View integration applications'),
    ('P109', '/api/v1/openapi/management/applications', 'POST', 'Manage integration applications'),
    ('P110', '/api/v1/openapi/management/approvals', 'POST', 'Approve production integration assets'),
    ('P111', '/api/v1/openapi/management/executions', 'GET', 'View integration executions')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

-- Hidden menu nodes carry permission codes into the current role-menu authorization model.
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES
    ('OA_MGMT_100', NULL, 'OpenApiManagementPermissions', 'OpenAPI integration permissions', NULL, NULL, NULL,
     'catalog', 'integration', 900, 0, 1, 1, 1, 1, 1, NULL, NULL, 'Hidden OpenAPI permission root'),
    ('OA_MGMT_101', 'OA_MGMT_100', 'OpenApiStructureRead', 'View structures', NULL, NULL, NULL,
     'button', 'integration', 10, 0, 1, 1, 0, 0, 0, 'P100', '/api/v1/openapi/management/structures:GET', 'OpenAPI structure read permission'),
    ('OA_MGMT_102', 'OA_MGMT_100', 'OpenApiStructureCreate', 'Create structures', NULL, NULL, NULL,
     'button', 'integration', 20, 0, 1, 1, 0, 0, 0, 'P101', '/api/v1/openapi/management/structures:POST', 'OpenAPI structure create permission'),
    ('OA_MGMT_103', 'OA_MGMT_100', 'OpenApiStructureEdit', 'Edit structures', NULL, NULL, NULL,
     'button', 'integration', 30, 0, 1, 1, 0, 0, 0, 'P102', '/api/v1/openapi/management/structures/*:PUT', 'OpenAPI structure edit permission'),
    ('OA_MGMT_104', 'OA_MGMT_100', 'OpenApiStructurePublish', 'Publish structures', NULL, NULL, NULL,
     'button', 'integration', 40, 0, 1, 1, 0, 0, 0, 'P103', '/api/v1/openapi/management/structures/*/publish:POST', 'OpenAPI structure publish permission'),
    ('OA_MGMT_105', 'OA_MGMT_100', 'OpenApiMappingRead', 'View mappings', NULL, NULL, NULL,
     'button', 'integration', 50, 0, 1, 1, 0, 0, 0, 'P104', '/api/v1/openapi/management/mappings:GET', 'OpenAPI mapping read permission'),
    ('OA_MGMT_106', 'OA_MGMT_100', 'OpenApiMappingManage', 'Manage mappings', NULL, NULL, NULL,
     'button', 'integration', 60, 0, 1, 1, 0, 0, 0, 'P105', '/api/v1/openapi/management/mappings:POST', 'OpenAPI mapping manage permission'),
    ('OA_MGMT_107', 'OA_MGMT_100', 'OpenApiProductRead', 'View API products', NULL, NULL, NULL,
     'button', 'integration', 70, 0, 1, 1, 0, 0, 0, 'P106', '/api/v1/openapi/management/products:GET', 'OpenAPI product read permission'),
    ('OA_MGMT_108', 'OA_MGMT_100', 'OpenApiProductManage', 'Manage API products', NULL, NULL, NULL,
     'button', 'integration', 80, 0, 1, 1, 0, 0, 0, 'P107', '/api/v1/openapi/management/products:POST', 'OpenAPI product manage permission'),
    ('OA_MGMT_109', 'OA_MGMT_100', 'OpenApiApplicationRead', 'View applications', NULL, NULL, NULL,
     'button', 'integration', 90, 0, 1, 1, 0, 0, 0, 'P108', '/api/v1/openapi/management/applications:GET', 'OpenAPI application read permission'),
    ('OA_MGMT_110', 'OA_MGMT_100', 'OpenApiApplicationManage', 'Manage applications', NULL, NULL, NULL,
     'button', 'integration', 100, 0, 1, 1, 0, 0, 0, 'P109', '/api/v1/openapi/management/applications:POST', 'OpenAPI application manage permission'),
    ('OA_MGMT_111', 'OA_MGMT_100', 'OpenApiApproval', 'Approve assets', NULL, NULL, NULL,
     'button', 'integration', 110, 0, 1, 1, 0, 0, 0, 'P110', '/api/v1/openapi/management/approvals:POST', 'OpenAPI approval permission'),
    ('OA_MGMT_112', 'OA_MGMT_100', 'OpenApiExecutionRead', 'View executions', NULL, NULL, NULL,
     'button', 'integration', 120, 0, 1, 1, 0, 0, 0, 'P111', '/api/v1/openapi/management/executions:GET', 'OpenAPI execution read permission')
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
WHERE menu.id LIKE 'OA_MGMT_%'
ON CONFLICT (role_id, menu_id) DO NOTHING;
