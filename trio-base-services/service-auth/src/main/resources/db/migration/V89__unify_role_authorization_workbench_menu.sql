-- Expose the unified role-and-authorization workbench through the backend-driven menu.
-- Keep the legacy route rows active but hidden so existing bookmarks remain resolvable.

UPDATE sys_menu
SET menu_name = '角色与授权',
    path = '/system/role-workbench',
    component = '/system/role-workbench/index',
    icon = 'lucide:shield-user',
    sort_order = 30,
    visible = 1,
    status = 1,
    hide_in_menu = 0,
    description = '角色基本信息、页面功能、业务功能、数据与字段权限统一工作台',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M005';

UPDATE sys_menu
SET hide_in_menu = 1,
    description = CASE id
        WHEN 'M010' THEN '兼容入口：数据权限已迁移至角色与授权工作台'
        WHEN 'M_AUTHZ' THEN '兼容入口：企业授权已迁移至角色与授权工作台'
        ELSE description
    END,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN ('M010', 'M_AUTHZ');
