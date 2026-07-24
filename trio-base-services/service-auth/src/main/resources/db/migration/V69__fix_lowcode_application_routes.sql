-- Align dynamic lowcode menus with real web-antd route components.

UPDATE sys_menu
SET path = '/lowcode/application',
    component = '/lowcode/application/list',
    menu_name = U&'\5E94\7528\7BA1\7406',
    visible = 1,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M033';

UPDATE sys_menu
SET path = '/lowcode/apps',
    component = '/lowcode/runtime/center',
    menu_name = U&'\5E94\7528\4E2D\5FC3',
    visible = 1,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M035';
