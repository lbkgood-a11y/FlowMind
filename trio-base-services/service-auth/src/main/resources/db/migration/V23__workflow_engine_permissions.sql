-- V23__workflow_engine_permissions.sql — 流程引擎权限码、菜单、角色种子数据
-- 配合 service-workflow-engine 的 V1 迁移一起使用

-- ============================================================
-- 1. 补充角色：费用报销种子数据需要 DEPT_HEAD 和 FINANCE
-- ============================================================
INSERT INTO sys_role(id, role_code, role_name, description) VALUES
    ('R004', 'DEPT_HEAD', '部门主管', '部门级审批权限'),
    ('R005', 'FINANCE',   '财务人员', '财务审批权限')
ON CONFLICT (role_code) DO UPDATE SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description;

-- ============================================================
-- 2. 流程引擎权限码
-- ============================================================
INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P065', '/api/v1/process-packages', 'GET',    '查看流程包列表'),
    ('P066', '/api/v1/process-packages', 'POST',   '新建流程包'),
    ('P067', '/api/v1/process-packages/*', 'PUT',  '编辑流程包'),
    ('P068', '/api/v1/process-packages/*', 'DELETE', '删除流程包'),
    ('P069', '/api/v1/process-packages/*/publish', 'PUT', '发布流程包'),
    ('P070', '/api/v1/process-packages/*/offline', 'PUT', '下架流程包'),
    ('P071', '/api/v1/process-instances/start', 'POST', '发起流程'),
    ('P072', '/api/v1/process-instances', 'GET',    '查看流程实例'),
    ('P073', '/api/v1/tasks/my-pending', 'GET',    '我的待办'),
    ('P074', '/api/v1/tasks/my-completed', 'GET',  '我的已办'),
    ('P075', '/api/v1/tasks/*/approve', 'POST',    '审批任务')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================
-- 3. 流程引擎菜单
-- ============================================================
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('M021', NULL, 'Process', '流程中心', '/process', NULL,
     'mdi:timeline-text-outline', NULL, NULL,
     'catalog', 'process', 80, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, '流程中心管理'),

    ('M022', 'M021', 'ProcessPackage', '流程管理', '/process/package', '/process/package/list',
     'mdi:file-document-multiple-outline', NULL, NULL,
     'menu', 'process', 10, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P065', '/api/v1/process-packages:GET', '流程包管理'),
    ('M022_BTN_CREATE', 'M022', 'ProcessPackageCreate', '新增', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P066', '/api/v1/process-packages:POST', '流程包-新增'),
    ('M022_BTN_EDIT', 'M022', 'ProcessPackageEdit', '编辑', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P067', '/api/v1/process-packages/*:PUT', '流程包-编辑'),
    ('M022_BTN_DELETE', 'M022', 'ProcessPackageDelete', '删除', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P068', '/api/v1/process-packages/*:DELETE', '流程包-删除'),
    ('M022_BTN_PUBLISH', 'M022', 'ProcessPackagePublish', '发布', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 40, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P069', '/api/v1/process-packages/*/publish:PUT', '流程包-发布'),
    ('M022_BTN_OFFLINE', 'M022', 'ProcessPackageOffline', '下架', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 50, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P070', '/api/v1/process-packages/*/offline:PUT', '流程包-下架'),

    ('M023', 'M021', 'ProcessInstance', '流程实例', '/process/instance', '/process/instance/list',
     'mdi:play-circle-outline', NULL, NULL,
     'menu', 'process', 20, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P072', '/api/v1/process-instances:GET', '流程实例管理'),
    ('M023_BTN_START', 'M023', 'ProcessInstanceStart', '发起', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P071', '/api/v1/process-instances/start:POST', '流程实例-发起'),

    ('M024', 'M021', 'TaskCenter', '任务中心', '/process/task', '/process/task/list',
     'mdi:clipboard-check-outline', NULL, NULL,
     'menu', 'process', 30, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P073', '/api/v1/tasks/my-pending:GET', '任务中心'),
    ('M024_BTN_APPROVE', 'M024', 'TaskApprove', '审批', NULL, NULL, NULL, NULL, NULL,
     'button', 'process', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P075', '/api/v1/tasks/*/approve:POST', '任务-审批')
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
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================
-- 4. ADMIN 角色自动授权所有流程中心菜单
-- ============================================================
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001', m.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu m
WHERE m.id IN (
    'M021', 'M022', 'M022_BTN_CREATE', 'M022_BTN_EDIT', 'M022_BTN_DELETE',
    'M022_BTN_PUBLISH', 'M022_BTN_OFFLINE',
    'M023', 'M023_BTN_START',
    'M024', 'M024_BTN_APPROVE'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ============================================================
-- 5. 向导：给 DEPT_HEAD 和 FINANCE 角色授权流程查看和审批权限
-- ============================================================
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(r.role_code || ':' || m.id), 1, 24)),
       r.id, m.id, 'SYSTEM', 'SYSTEM'
FROM sys_role r
CROSS JOIN (
    SELECT id FROM sys_menu WHERE id IN ('M023', 'M024')
) m
WHERE r.role_code IN ('DEPT_HEAD', 'FINANCE')
ON CONFLICT (role_id, menu_id) DO NOTHING;
