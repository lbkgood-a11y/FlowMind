-- Align enterprise authorization menu with the actual frontend route/component.
UPDATE sys_menu
SET menu_key = 'SystemAuthz',
    path = '/system/authz',
    component = '/system/authz/index',
    icon = 'mdi:shield-account',
    menu_name = '企业授权',
    description = '企业级资源授权、数据范围、字段权限和决策预览',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M_AUTHZ';
