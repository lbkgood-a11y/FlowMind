-- Lowcode generic runtime permissions. service-auth owns only RBAC/menu seed data here.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P140', '/api/v1/lowcode-runtime/apps', 'GET', 'List visible lowcode runtime applications'),
    ('P141', '/api/v1/lowcode-runtime/apps/*', 'GET', 'Read lowcode runtime application descriptor'),
    ('P142', '/api/v1/lowcode-runtime/apps/*/instances', 'GET', 'List lowcode runtime application instances'),
    ('P143', '/api/v1/lowcode-runtime/apps/*/instances/*', 'GET', 'Read lowcode runtime application instance'),
    ('P144', '/api/v1/lowcode-runtime/apps/*/actions/*', 'POST', 'Run lowcode runtime application action'),
    ('P145', '/api/v1/lowcode-runtime/apps/*/instances/*/retry-workflow', 'POST', 'Retry lowcode runtime workflow launch')
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
    ('M035', 'M030', 'LowcodeRuntimeCenter', U&'\5E94\7528\4E2D\5FC3', '/lowcode/runtime', '/lowcode/runtime/index',
     'mdi:application-brackets-outline', NULL, NULL,
     'menu', 'lowcode', 40, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P140', '/api/v1/lowcode-runtime/apps:GET',
     'Open published lowcode applications'),
    ('M035_BTN_DESCRIPTOR', 'M035', 'LowcodeRuntimeDescriptor', U&'\8BFB\53D6\5E94\7528', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P141', '/api/v1/lowcode-runtime/apps/*:GET',
     'Read published lowcode application descriptor'),
    ('M035_BTN_LIST', 'M035', 'LowcodeRuntimeListInstances', U&'\67E5\8BE2\6570\636E', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P142', '/api/v1/lowcode-runtime/apps/*/instances:GET',
     'List lowcode runtime form instances'),
    ('M035_BTN_DETAIL', 'M035', 'LowcodeRuntimeReadInstance', U&'\67E5\770B\8BE6\60C5', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P143', '/api/v1/lowcode-runtime/apps/*/instances/*:GET',
     'Read lowcode runtime form instance'),
    ('M035_BTN_ACTION', 'M035', 'LowcodeRuntimeRunAction', U&'\6267\884C\52A8\4F5C', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 40, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P144', '/api/v1/lowcode-runtime/apps/*/actions/*:POST',
     'Run lowcode runtime action'),
    ('M035_BTN_RETRY', 'M035', 'LowcodeRuntimeRetryWorkflow', U&'\91CD\8BD5\6D41\7A0B', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 50, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P145', '/api/v1/lowcode-runtime/apps/*/instances/*/retry-workflow:POST',
     'Retry lowcode runtime workflow launch')
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
WHERE m.id IN (
    'M035',
    'M035_BTN_DESCRIPTOR',
    'M035_BTN_LIST',
    'M035_BTN_DETAIL',
    'M035_BTN_ACTION',
    'M035_BTN_RETRY'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;
