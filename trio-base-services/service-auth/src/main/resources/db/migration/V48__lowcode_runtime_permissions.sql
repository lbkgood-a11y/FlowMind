-- Lowcode runtime permissions only. service-auth owns RBAC/menu seed data, not lc_* tables.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P125', '/api/v1/forms/*/instances', 'GET', 'Query lowcode form instances'),
    ('P126', '/api/v1/forms/*/submit', 'POST', 'Submit lowcode form instance'),
    ('P127', '/api/v1/forms/*/instances/*/process', 'PUT', 'Bind lowcode form instance to workflow'),
    ('P128', '/api/v1/form-instances/*', 'GET', 'View lowcode form instance detail')
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
    ('M030_API_INSTANCE_QUERY', 'M030', 'LowcodeRuntimeInstanceQuery', 'queryLowcodeInstances(API)',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 80, 0, 1, 0, 0, 1, 0, 1, 1,
     NULL, NULL, NULL, 'P125', '/api/v1/forms/*/instances:GET',
     'Query lowcode form instances'),
    ('M030_API_INSTANCE_SUBMIT', 'M030', 'LowcodeRuntimeInstanceSubmit', 'submitLowcodeInstance(API)',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 81, 0, 1, 0, 0, 1, 0, 1, 1,
     NULL, NULL, NULL, 'P126', '/api/v1/forms/*/submit:POST',
     'Submit lowcode form instance'),
    ('M030_API_INSTANCE_BIND_PROCESS', 'M030', 'LowcodeRuntimeBindProcess', 'bindLowcodeProcess(API)',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 82, 0, 1, 0, 0, 1, 0, 1, 1,
     NULL, NULL, NULL, 'P127', '/api/v1/forms/*/instances/*/process:PUT',
     'Bind lowcode form instance to workflow'),
    ('M030_API_INSTANCE_DETAIL', 'M030', 'LowcodeRuntimeInstanceDetail', 'viewLowcodeInstance(API)',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 83, 0, 1, 0, 0, 1, 0, 1, 1,
     NULL, NULL, NULL, 'P128', '/api/v1/form-instances/*:GET',
     'View lowcode form instance detail')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    status = EXCLUDED.status,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(r.id || ':' || m.id), 1, 24)), r.id, m.id, 'SYSTEM', 'SYSTEM'
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.id IN ('R001', 'R002', 'R003')
  AND m.id IN (
      'M030_API_INSTANCE_QUERY',
      'M030_API_INSTANCE_SUBMIT',
      'M030_API_INSTANCE_BIND_PROCESS',
      'M030_API_INSTANCE_DETAIL'
  )
ON CONFLICT (role_id, menu_id) DO NOTHING;
