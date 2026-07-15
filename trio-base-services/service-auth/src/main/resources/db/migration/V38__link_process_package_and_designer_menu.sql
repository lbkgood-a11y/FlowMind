-- Link process package management to the visual designer detail route.

UPDATE sys_menu
SET menu_name = '流程包管理',
    description = '流程包清单与版本管理',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M022';

UPDATE sys_menu
SET parent_id = 'M021',
    menu_name = '流程设计器',
    hide_in_menu = 1,
    permission_id = 'P065',
    permission_code = '/api/v1/process-packages:GET',
    description = '从流程包清单进入的可视化设计详情页',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M025';

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(source.role_id || ':M025'), 1, 24)),
       source.role_id,
       'M025',
       'SYSTEM',
       'SYSTEM'
FROM sys_role_menu source
WHERE source.menu_id = 'M022'
ON CONFLICT (role_id, menu_id) DO NOTHING;
