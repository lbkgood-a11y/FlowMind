-- Expose the OpenAPI operations workbench in backend-driven menu mode.
-- OA_MGMT_100 was originally created as a hidden permission-only root.
UPDATE sys_menu
SET menu_key = 'OpenApiOperations',
    menu_name = 'OpenAPI 集成管理',
    path = '/openapi-operations',
    component = NULL,
    icon = 'mdi:connection',
    menu_type = 'catalog',
    menu_group = 'integration',
    sort_order = 70,
    visible = 1,
    status = 1,
    keep_alive = 0,
    affix_tab = 0,
    hide_in_menu = 0,
    hide_children_in_menu = 0,
    hide_in_breadcrumb = 0,
    hide_in_tab = 0,
    permission_id = NULL,
    permission_code = NULL,
    description = 'OpenAPI 集成资产、运行与安全治理',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'OA_MGMT_100';

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    'OA_MGMT_WORKBENCH', 'OA_MGMT_100', 'OpenApiOperationsWorkbench', '集成工作台',
    '/openapi-operations/workbench', '/openapi/workbench/index', 'mdi:monitor-dashboard',
    'menu', 'integration', 10, 1, 1, 0, 0,
    0, 0, 0, 0,
    'P111', '/api/v1/openapi/management/executions:GET',
    'OpenAPI 结构、映射、路由、编排、应用、策略、执行与回调隔离运维工作台'
)
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
    hide_children_in_menu = EXCLUDED.hide_children_in_menu,
    hide_in_breadcrumb = EXCLUDED.hide_in_breadcrumb,
    hide_in_tab = EXCLUDED.hide_in_tab,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT 'OA' || upper(substr(md5('R001' || ':' || menu.id), 1, 24)),
       'R001', menu.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu menu
WHERE menu.id IN ('OA_MGMT_100', 'OA_MGMT_WORKBENCH')
ON CONFLICT (role_id, menu_id) DO NOTHING;
