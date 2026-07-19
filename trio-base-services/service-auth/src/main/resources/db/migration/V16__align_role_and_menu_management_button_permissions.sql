-- Align role/menu-management button permissions with backend @RequirePermission codes.
-- Keep one canonical button node per protected action and remove older
-- frontend-only / duplicate API helper nodes.

-- 1. Convert the original visible role-management button nodes to backend API permission codes.
UPDATE sys_menu
SET menu_key = 'SystemRoleCreate',
    menu_name = '新增',
    menu_type = 'button',
    parent_id = 'M005',
    path = NULL,
    component = NULL,
    permission_id = 'P006',
    permission_code = '/api/v1/roles:POST',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 10,
    description = '角色管理-新增角色'
WHERE id = '01HK153X160000000000000016';

UPDATE sys_menu
SET menu_key = 'SystemRoleEdit',
    menu_name = '修改',
    menu_type = 'button',
    parent_id = 'M005',
    path = NULL,
    component = NULL,
    permission_id = 'P007',
    permission_code = '/api/v1/roles/*:PUT',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 20,
    description = '角色管理-编辑、启停、授权'
WHERE id = '01HK153X170000000000000017';

UPDATE sys_menu
SET menu_key = 'SystemRoleDelete',
    menu_name = '删除',
    menu_type = 'button',
    parent_id = 'M005',
    path = NULL,
    component = NULL,
    permission_id = 'P008',
    permission_code = '/api/v1/roles/*:DELETE',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 30,
    description = '角色管理-删除角色'
WHERE id = '01HK153X180000000000000018';

-- 2. Convert the original visible menu-management button nodes to backend API permission codes.
UPDATE sys_menu
SET menu_key = 'SystemMenuCreate',
    menu_name = '新增',
    menu_type = 'button',
    parent_id = 'M006',
    path = NULL,
    component = NULL,
    permission_id = 'P014',
    permission_code = '/api/v1/menus:POST',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 10,
    description = '菜单管理-新增菜单'
WHERE id = '01HK153X100000000000000010';

UPDATE sys_menu
SET menu_key = 'SystemMenuEdit',
    menu_name = '修改',
    menu_type = 'button',
    parent_id = 'M006',
    path = NULL,
    component = NULL,
    permission_id = '01HK153X0M000000000000000M',
    permission_code = '/api/v1/menus/*:PUT',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 20,
    description = '菜单管理-编辑、启停'
WHERE id = '01HK153X110000000000000011';

UPDATE sys_menu
SET menu_key = 'SystemMenuDelete',
    menu_name = '删除',
    menu_type = 'button',
    parent_id = 'M006',
    path = NULL,
    component = NULL,
    permission_id = 'P015',
    permission_code = '/api/v1/menus/*:DELETE',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 30,
    description = '菜单管理-删除菜单'
WHERE id = '01HK153X120000000000000012';

-- 3. Preserve grants that were assigned to duplicate API helper nodes.
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(rm.role_id || ':' || mapping.target_menu_id), 1, 24)),
       rm.role_id,
       mapping.target_menu_id,
       'SYSTEM',
       'SYSTEM'
FROM sys_role_menu rm
JOIN (
    VALUES
        ('01HK153X330000000000000033', '01HK153X160000000000000016'),
        ('01HK153X340000000000000034', '01HK153X170000000000000017'),
        ('01HK153X350000000000000035', '01HK153X180000000000000018'),
        ('01HK153X360000000000000036', '01HK153X100000000000000010'),
        ('01HK153X370000000000000037', '01HK153X110000000000000011'),
        ('01HK153X380000000000000038', '01HK153X120000000000000012')
) AS mapping(source_menu_id, target_menu_id)
    ON rm.menu_id = mapping.source_menu_id
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 4. Delete duplicate API helper nodes; their grants have been migrated above.
DELETE FROM sys_role_menu
WHERE menu_id IN (
    '01HK153X330000000000000033',
    '01HK153X340000000000000034',
    '01HK153X350000000000000035',
    '01HK153X360000000000000036',
    '01HK153X370000000000000037',
    '01HK153X380000000000000038'
);

DELETE FROM sys_menu
WHERE id IN (
    '01HK153X330000000000000033',
    '01HK153X340000000000000034',
    '01HK153X350000000000000035',
    '01HK153X360000000000000036',
    '01HK153X370000000000000037',
    '01HK153X380000000000000038'
);

-- 5. Remove obsolete frontend-only System:Role/System:Menu permission records.
DELETE FROM sys_permission p
WHERE p.id IN (
    '01HK153X190000000000000019',
    '01HK153X1A000000000000001A',
    '01HK153X1B000000000000001B',
    '01HK153X1F000000000000001F',
    '01HK153X1G000000000000001G',
    '01HK153X1H000000000000001H'
)
AND NOT EXISTS (
    SELECT 1
    FROM sys_menu m
    WHERE m.permission_id = p.id
);

-- 6. Backfill ancestor menu grants for all roles.
WITH RECURSIVE ancestors(role_id, menu_id) AS (
    SELECT rm.role_id, m.parent_id
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.id = rm.menu_id
    WHERE m.parent_id IS NOT NULL

    UNION

    SELECT ancestors.role_id, parent.parent_id
    FROM ancestors
    JOIN sys_menu parent ON parent.id = ancestors.menu_id
    WHERE parent.parent_id IS NOT NULL
)
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(role_id || ':' || menu_id), 1, 24)),
       role_id,
       menu_id,
       'SYSTEM',
       'SYSTEM'
FROM ancestors
WHERE menu_id IS NOT NULL
ON CONFLICT (role_id, menu_id) DO NOTHING;
