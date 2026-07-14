-- V14__supply_missing_api_permissions_for_admin.sql
-- ADMIN 用户权限码列表缺少 POST/PUT/DELETE 等写操作权限，
-- 导致 admin 修改用户角色时提示"权限不足"。
--
-- 原因：菜单 M004(用户管理) 只绑定了 permission_code='/api/v1/users:GET'，
-- 而 Controller 上的 @RequirePermission("/api/v1/users/*:PUT") 校验需要 PUT 权限。
--
-- 修复：在每个管理页面菜单下添加隐藏按钮菜单节点承载 API 写操作权限。
-- 这些按钮不显示在 UI 上（hide_in_menu=1），仅用于权限码推导。

-- =====================================================
-- 1. 用户管理 (M004) — 补充 POST/*:PUT/*:DELETE 权限
-- =====================================================
-- POST 权限: /api/v1/users:POST (P002)
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X300000000000000030', 'M004', 'SystemUserCreateApi', 'createUser(API)',
    NULL, NULL, NULL,
    'button', 'system', 5, 1, 1,
    1, 0, 0, 0,
    'P002', '/api/v1/users:POST', '用户管理-新增API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P002', permission_code = '/api/v1/users:POST',
    hide_in_menu = 1, menu_type = 'button';

-- PUT 权限: /api/v1/users/*:PUT (P003)
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X310000000000000031', 'M004', 'SystemUserEditApi', 'editUser(API)',
    NULL, NULL, NULL,
    'button', 'system', 6, 1, 1,
    1, 0, 0, 0,
    'P003', '/api/v1/users/*:PUT', '用户管理-编辑API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P003', permission_code = '/api/v1/users/*:PUT',
    hide_in_menu = 1, menu_type = 'button';

-- DELETE 权限: /api/v1/users/*:DELETE (P004)
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X320000000000000032', 'M004', 'SystemUserDeleteApi', 'deleteUser(API)',
    NULL, NULL, NULL,
    'button', 'system', 7, 1, 1,
    1, 0, 0, 0,
    'P004', '/api/v1/users/*:DELETE', '用户管理-删除API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P004', permission_code = '/api/v1/users/*:DELETE',
    hide_in_menu = 1, menu_type = 'button';

-- =====================================================
-- 2. 角色管理 (M005) — 补充 POST/*:PUT/*:DELETE 权限
-- =====================================================
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X330000000000000033', 'M005', 'SystemRoleCreateApi', 'createRole(API)',
    NULL, NULL, NULL,
    'button', 'system', 5, 1, 1,
    1, 0, 0, 0,
    'P006', '/api/v1/roles:POST', '角色管理-新增API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P006', permission_code = '/api/v1/roles:POST',
    hide_in_menu = 1, menu_type = 'button';

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X340000000000000034', 'M005', 'SystemRoleEditApi', 'editRole(API)',
    NULL, NULL, NULL,
    'button', 'system', 6, 1, 1,
    1, 0, 0, 0,
    'P007', '/api/v1/roles/*:PUT', '角色管理-编辑API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P007', permission_code = '/api/v1/roles/*:PUT',
    hide_in_menu = 1, menu_type = 'button';

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X350000000000000035', 'M005', 'SystemRoleDeleteApi', 'deleteRole(API)',
    NULL, NULL, NULL,
    'button', 'system', 7, 1, 1,
    1, 0, 0, 0,
    'P008', '/api/v1/roles/*:DELETE', '角色管理-删除API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P008', permission_code = '/api/v1/roles/*:DELETE',
    hide_in_menu = 1, menu_type = 'button';

-- =====================================================
-- 3. 菜单管理 (M006) — 补充 POST/*:DELETE 权限（PUT 已存在）
-- =====================================================
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X360000000000000036', 'M006', 'SystemMenuCreateApi', 'createMenu(API)',
    NULL, NULL, NULL,
    'button', 'system', 5, 1, 1,
    1, 0, 0, 0,
    'P014', '/api/v1/menus:POST', '菜单管理-新增API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P014', permission_code = '/api/v1/menus:POST',
    hide_in_menu = 1, menu_type = 'button';

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X370000000000000037', 'M006', 'SystemMenuEditApi', 'editMenu(API)',
    NULL, NULL, NULL,
    'button', 'system', 6, 1, 1,
    1, 0, 0, 0,
    '01HK153X0L000000000000000L', '/api/v1/menus/*:PUT', '菜单管理-编辑API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = '01HK153X0L000000000000000L', permission_code = '/api/v1/menus/*:PUT',
    hide_in_menu = 1, menu_type = 'button';

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES (
    '01HK153X380000000000000038', 'M006', 'SystemMenuDeleteApi', 'deleteMenu(API)',
    NULL, NULL, NULL,
    'button', 'system', 7, 1, 1,
    1, 0, 0, 0,
    'P015', '/api/v1/menus/*:DELETE', '菜单管理-删除API权限(隐藏)'
) ON CONFLICT (id) DO UPDATE SET
    permission_id = 'P015', permission_code = '/api/v1/menus/*:DELETE',
    hide_in_menu = 1, menu_type = 'button';

-- =====================================================
-- 4. 权限管理 (PermissionController) — 补充所有权限
--    PermissionController 在页面中无独立菜单，用隐藏节点挂在系统管理(M008)下
-- =====================================================
INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon,
    menu_type, menu_group, sort_order, visible, status,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    permission_id, permission_code, description
) VALUES
('01HK153X3A000000000000003A', 'M008', 'PermissionGetApi', 'viewPermission(API)',
 NULL, NULL, NULL,
 'button', 'system', 60, 1, 1,
 1, 0, 0, 0,
 'P016', '/api/v1/permissions:GET', '权限管理-查看API权限(隐藏)'),
('01HK153X3B000000000000003B', 'M008', 'PermissionPostApi', 'createPermission(API)',
 NULL, NULL, NULL,
 'button', 'system', 61, 1, 1,
 1, 0, 0, 0,
 'P017', '/api/v1/permissions:POST', '权限管理-新增API权限(隐藏)'),
('01HK153X3C000000000000003C', 'M008', 'PermissionDeleteApi', 'deletePermission(API)',
 NULL, NULL, NULL,
 'button', 'system', 62, 1, 1,
 1, 0, 0, 0,
 'P018', '/api/v1/permissions/*:DELETE', '权限管理-删除API权限(隐藏)')
ON CONFLICT (id) DO UPDATE SET
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    hide_in_menu = 1, menu_type = 'button';

-- =====================================================
-- 5. 为 ADMIN 角色授予所有新菜单的权限
-- =====================================================
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001', m.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu m
WHERE m.id IN (
    '01HK153X300000000000000030', '01HK153X310000000000000031', '01HK153X320000000000000032',
    '01HK153X330000000000000033', '01HK153X340000000000000034', '01HK153X350000000000000035',
    '01HK153X360000000000000036', '01HK153X370000000000000037', '01HK153X380000000000000038',
    '01HK153X3A000000000000003A', '01HK153X3B000000000000003B', '01HK153X3C000000000000003C'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;
