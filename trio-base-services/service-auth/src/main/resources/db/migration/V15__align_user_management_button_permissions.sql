-- Align user-management button permissions with backend @RequirePermission codes.
-- Keep one canonical button node per visible protected action and remove older
-- frontend-only / duplicate API helper nodes.

-- 1. Convert the original visible button nodes to backend API permission codes.
UPDATE sys_menu
SET menu_key = 'SystemUserCreate',
    menu_name = '新增',
    menu_type = 'button',
    parent_id = 'M004',
    path = NULL,
    component = NULL,
    permission_id = 'P002',
    permission_code = '/api/v1/users:POST',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 10,
    description = '用户管理-新增用户'
WHERE id = '01HK153X130000000000000013';

UPDATE sys_menu
SET menu_key = 'SystemUserEdit',
    menu_name = '修改',
    menu_type = 'button',
    parent_id = 'M004',
    path = NULL,
    component = NULL,
    permission_id = 'P003',
    permission_code = '/api/v1/users/*:PUT',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 20,
    description = '用户管理-编辑、状态切换、角色分配'
WHERE id = '01HK153X140000000000000014';

UPDATE sys_menu
SET menu_key = 'SystemUserDelete',
    menu_name = '删除',
    menu_type = 'button',
    parent_id = 'M004',
    path = NULL,
    component = NULL,
    permission_id = 'P004',
    permission_code = '/api/v1/users/*:DELETE',
    hide_in_menu = 1,
    status = 1,
    visible = 1,
    sort_order = 30,
    description = '用户管理-删除用户'
WHERE id = '01HK153X150000000000000015';

-- 2. Preserve existing grants that were assigned to duplicate API helper nodes.
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(rm.role_id || ':' || mapping.target_menu_id), 1, 24)),
       rm.role_id,
       mapping.target_menu_id,
       'SYSTEM',
       'SYSTEM'
FROM sys_role_menu rm
JOIN (
    VALUES
        ('01HK153X300000000000000030', '01HK153X130000000000000013'),
        ('01HK153X310000000000000031', '01HK153X140000000000000014'),
        ('01HK153X320000000000000032', '01HK153X150000000000000015')
) AS mapping(source_menu_id, target_menu_id)
    ON rm.menu_id = mapping.source_menu_id
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 3. Delete duplicate API helper nodes; their grants have been migrated above.
DELETE FROM sys_role_menu
WHERE menu_id IN (
    '01HK153X300000000000000030',
    '01HK153X310000000000000031',
    '01HK153X320000000000000032'
);

DELETE FROM sys_menu
WHERE id IN (
    '01HK153X300000000000000030',
    '01HK153X310000000000000031',
    '01HK153X320000000000000032'
);

-- 4. Remove obsolete frontend-only System:User permission records.
DELETE FROM sys_permission p
WHERE p.id IN (
    '01HK153X1C000000000000001C',
    '01HK153X1D000000000000001D',
    '01HK153X1E000000000000001E'
)
AND NOT EXISTS (
    SELECT 1
    FROM sys_menu m
    WHERE m.permission_id = p.id
);

-- 5. Backfill ancestor menu grants for all roles.
-- Button permissions need their page/catalog ancestors so route visibility and
-- page-level GET permissions stay consistent with button-level grants.
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
