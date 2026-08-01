-- Clarify the distinction between page intent and runtime resource registration.

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_code, description
) VALUES (
    'M_AUTHZ_GOVERNANCE', 'M008', 'SystemAuthorizationGovernance', '权限治理',
    '/system/authorization-governance', NULL, 'lucide:shield-check',
    'catalog', 'system', 63, 1, 1, 0, 0, 0, 0, 0, 0,
    NULL, '页面能力与运行时授权资源治理'
)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    sort_order = EXCLUDED.sort_order,
    visible = 1,
    status = 1,
    hide_in_menu = 0,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_menu
SET parent_id = 'M_AUTHZ_GOVERNANCE',
    menu_name = CASE id
        WHEN 'M_PAGE_CAP_CATALOG' THEN '页面能力目录'
        WHEN 'M_AUTHZ_RESOURCE_CATALOG' THEN '资源注册中心'
        ELSE menu_name
    END,
    sort_order = CASE id
        WHEN 'M_PAGE_CAP_CATALOG' THEN 10
        WHEN 'M_AUTHZ_RESOURCE_CATALOG' THEN 20
        ELSE sort_order
    END,
    description = CASE id
        WHEN 'M_PAGE_CAP_CATALOG' THEN '查看页面能力、依赖关系及运行时资源映射'
        WHEN 'M_AUTHZ_RESOURCE_CATALOG' THEN '查看 Owner 注册的资源、动作、字段、守卫与同步状态'
        ELSE description
    END,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN ('M_PAGE_CAP_CATALOG', 'M_AUTHZ_RESOURCE_CATALOG');

UPDATE sys_auth_resource
SET display_name = CASE resource_code
        WHEN 'PAGE_CAPABILITY_CATALOG' THEN '页面能力目录页面'
        WHEN 'PAGE_AUTHORIZATION_RESOURCE_CATALOG' THEN '资源注册中心页面'
        ELSE display_name
    END,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code IN ('PAGE_CAPABILITY_CATALOG', 'PAGE_AUTHORIZATION_RESOURCE_CATALOG');

UPDATE sys_auth_action
SET description = CASE resource_code
        WHEN 'PAGE_CAPABILITY_CATALOG' THEN '进入页面能力目录'
        WHEN 'PAGE_AUTHORIZATION_RESOURCE_CATALOG' THEN '进入资源注册中心'
        ELSE description
    END,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE action_code = 'ACCESS'
  AND resource_code IN ('PAGE_CAPABILITY_CATALOG', 'PAGE_AUTHORIZATION_RESOURCE_CATALOG');

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE');
