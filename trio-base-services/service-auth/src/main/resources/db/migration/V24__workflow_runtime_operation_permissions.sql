-- Complete workflow runtime permissions and align menu/button bindings with backend guards.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P076', '/api/v1/process-packages/*/versions', 'POST', '创建流程包新版本'),
    ('P077', '/api/v1/tasks/*/reject', 'POST', '驳回或退回任务'),
    ('P078', '/api/v1/tasks/*/transfer', 'POST', '转办任务'),
    ('P079', '/api/v1/tasks/*/add-sign', 'POST', '并行加签'),
    ('P080', '/api/v1/tasks/reject-targets/*', 'GET', '查询可退回节点'),
    ('P081', '/api/v1/process-instances/*/history', 'GET', '查看流程审批历史'),
    ('P082', '/api/v1/tasks/*', 'GET', '查看任务详情'),
    ('P083', '/api/v1/process-instances/*', 'GET', '查看流程实例详情')
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
    ('M022_BTN_VERSION', 'M022', 'ProcessPackageNewVersion', '新版本', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 60, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P076', '/api/v1/process-packages/*/versions:POST', '流程包-创建新版本'),
    ('M023_BTN_DETAIL', 'M023', 'ProcessInstanceDetail', '详情', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P083', '/api/v1/process-instances/*:GET', '流程实例-查看详情'),
    ('M023_BTN_HISTORY', 'M023', 'ProcessInstanceHistory', '历史', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P081', '/api/v1/process-instances/*/history:GET', '流程实例-审批历史'),
    ('M024_BTN_DETAIL', 'M024', 'TaskDetail', '详情', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P082', '/api/v1/tasks/*:GET', '任务-查看详情'),
    ('M024_BTN_REJECT', 'M024', 'TaskReject', '驳回', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P077', '/api/v1/tasks/*/reject:POST', '任务-驳回或退回'),
    ('M024_BTN_TRANSFER', 'M024', 'TaskTransfer', '转办', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 40, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P078', '/api/v1/tasks/*/transfer:POST', '任务-转办'),
    ('M024_BTN_ADD_SIGN', 'M024', 'TaskAddSign', '加签', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 50, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P079', '/api/v1/tasks/*/add-sign:POST', '任务-并行加签'),
    ('M024_BTN_REJECT_TARGET', 'M024', 'TaskRejectTarget', '退回节点', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 60, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P080', '/api/v1/tasks/reject-targets/*:GET', '任务-查询可退回节点')
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
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001', m.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu m
WHERE m.id IN (
    'M022_BTN_VERSION', 'M023_BTN_DETAIL', 'M023_BTN_HISTORY', 'M024_BTN_DETAIL',
    'M024_BTN_REJECT', 'M024_BTN_TRANSFER', 'M024_BTN_ADD_SIGN',
    'M024_BTN_REJECT_TARGET'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(r.id || ':' || m.id), 1, 24)),
       r.id, m.id, 'SYSTEM', 'SYSTEM'
FROM sys_role r
CROSS JOIN (
    SELECT id FROM sys_menu WHERE id IN (
        'M023_BTN_DETAIL', 'M023_BTN_HISTORY', 'M024_BTN_DETAIL', 'M024_BTN_APPROVE',
        'M024_BTN_REJECT', 'M024_BTN_TRANSFER', 'M024_BTN_ADD_SIGN',
        'M024_BTN_REJECT_TARGET'
    )
) m
WHERE r.role_code IN ('DEPT_HEAD', 'FINANCE')
ON CONFLICT (role_id, menu_id) DO NOTHING;
