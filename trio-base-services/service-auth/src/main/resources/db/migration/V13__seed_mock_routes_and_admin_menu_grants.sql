-- Register the remaining frontend template/mock routes as real backend menus.
-- Existing seed menu rows keep their original IDs to preserve deployed references.

ALTER TABLE sys_menu ALTER COLUMN icon TYPE VARCHAR(256);
ALTER TABLE sys_menu ALTER COLUMN active_icon TYPE VARCHAR(256);

UPDATE sys_menu
SET menu_key = 'Dashboard',
    menu_name = '仪表盘',
    path = '/dashboard',
    component = NULL,
    icon = 'lucide:layout-dashboard',
    menu_type = 'catalog',
    menu_group = 'general',
    sort_order = 10,
    visible = 1,
    status = 1,
    permission_id = NULL,
    permission_code = NULL,
    description = '工作台与分析概览'
WHERE id = 'M001';

UPDATE sys_menu
SET parent_id = 'M001',
    menu_key = 'Analytics',
    menu_name = '分析页',
    path = '/dashboard/analytics',
    component = '/dashboard/analytics/index',
    icon = 'lucide:area-chart',
    menu_type = 'menu',
    menu_group = 'general',
    sort_order = 20,
    visible = 1,
    status = 1,
    affix_tab = 1,
    permission_id = NULL,
    permission_code = NULL,
    description = '经营指标与趋势分析'
WHERE id = 'M002';

UPDATE sys_menu
SET parent_id = 'M001',
    menu_key = 'Workspace',
    menu_name = '工作台',
    path = '/dashboard/workspace',
    component = '/dashboard/workspace/index',
    icon = 'carbon:workspace',
    menu_type = 'menu',
    menu_group = 'general',
    sort_order = 30,
    visible = 1,
    status = 1,
    permission_id = NULL,
    permission_code = NULL,
    description = '个人工作台'
WHERE id = 'M003';

UPDATE sys_menu
SET parent_id = 'M008',
    menu_key = 'SystemUser',
    menu_name = '用户管理',
    path = '/system/user',
    component = '/system/user/list',
    icon = 'mdi:user',
    menu_type = 'menu',
    menu_group = 'system',
    sort_order = 20,
    visible = 1,
    status = 1,
    permission_id = 'P001',
    permission_code = '/api/v1/users:GET',
    description = '用户状态与角色分配'
WHERE id = 'M004';

UPDATE sys_menu
SET parent_id = 'M008',
    menu_key = 'SystemRole',
    menu_name = '角色管理',
    path = '/system/role',
    component = '/system/role/list',
    icon = 'mdi:account-group',
    menu_type = 'menu',
    menu_group = 'system',
    sort_order = 30,
    visible = 1,
    status = 1,
    permission_id = 'P005',
    permission_code = '/api/v1/roles:GET',
    description = '角色与菜单授权'
WHERE id = 'M005';

UPDATE sys_menu
SET parent_id = 'M008',
    menu_key = 'SystemMenu',
    menu_name = '菜单管理',
    path = '/system/menu',
    component = '/system/menu/list',
    icon = 'mdi:menu',
    menu_type = 'menu',
    menu_group = 'system',
    sort_order = 40,
    visible = 1,
    status = 1,
    permission_id = 'P013',
    permission_code = '/api/v1/menus:GET',
    description = '后台菜单目录管理'
WHERE id = 'M006';

UPDATE sys_menu
SET menu_key = 'System',
    menu_name = '系统管理',
    path = '/system',
    component = NULL,
    icon = 'ion:settings-outline',
    menu_type = 'catalog',
    menu_group = 'system',
    sort_order = 80,
    visible = 1,
    status = 1,
    permission_id = NULL,
    permission_code = NULL,
    description = '系统基础配置'
WHERE id = 'M008';

UPDATE sys_menu
SET parent_id = 'M008',
    menu_key = 'SystemDept',
    menu_name = '部门管理',
    path = '/system/dept',
    component = '/system/dept/list',
    icon = 'charm:organisation',
    menu_type = 'menu',
    menu_group = 'system',
    sort_order = 50,
    visible = 1,
    status = 1,
    permission_id = NULL,
    permission_code = NULL,
    description = '组织与部门维护'
WHERE id = 'M009';

UPDATE sys_menu SET parent_id = 'M004' WHERE id IN (
    '01HK153X130000000000000013',
    '01HK153X140000000000000014',
    '01HK153X150000000000000015'
);

UPDATE sys_menu SET parent_id = 'M005' WHERE id IN (
    '01HK153X160000000000000016',
    '01HK153X170000000000000017',
    '01HK153X180000000000000018'
);

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('01HK153X200000000000000020', NULL, 'VbenProject', 'Vben Admin', '/vben-admin', NULL,
     'https://unpkg.com/@vbenjs/static-source@0.1.7/source/logo-v1.webp', NULL, NULL,
     'catalog', 'demo', 9998, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, 'dot', NULL, NULL, NULL, 'Vben project resources'),
    ('01HK153X210000000000000021', '01HK153X200000000000000020', 'VbenDocument', '文档', NULL, 'https://doc.vben.pro',
     'lucide:book-open-text', NULL, NULL,
     'link', 'demo', 10, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'Vben documentation'),
    ('01HK153X220000000000000022', '01HK153X200000000000000020', 'VbenGithub', 'Github', NULL, 'https://github.com/vbenjs/vue-vben-admin',
     'mdi:github', NULL, NULL,
     'link', 'demo', 20, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'Vben GitHub repository'),
    ('01HK153X230000000000000023', '01HK153X200000000000000020', 'VbenAntdVNext', 'Ant Design Vue', NULL, 'https://antdv-next.vben.pro',
     'logos:ant-design', NULL, NULL,
     'link', 'demo', 30, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, 'dot', NULL, NULL, NULL, 'Ant Design Vue preview'),
    ('01HK153X240000000000000024', '01HK153X200000000000000020', 'VbenNaive', 'Naive UI', NULL, 'https://naive.vben.pro',
     'logos:naiveui', NULL, NULL,
     'link', 'demo', 40, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, 'dot', NULL, NULL, NULL, 'Naive UI preview'),
    ('01HK153X250000000000000025', '01HK153X200000000000000020', 'VbenTDesign', 'TDesign', NULL, 'https://tdesign.vben.pro',
     'logos:tdesign', NULL, NULL,
     'link', 'demo', 50, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, 'dot', NULL, NULL, NULL, 'TDesign preview'),
    ('01HK153X260000000000000026', '01HK153X200000000000000020', 'VbenElementPlus', 'Element Plus', NULL, 'https://ele.vben.pro',
     'logos:element', NULL, NULL,
     'link', 'demo', 60, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, 'dot', NULL, NULL, NULL, 'Element Plus preview'),
    ('01HK153X270000000000000027', '01HK153X200000000000000020', 'VbenAbout', '关于', '/vben-admin/about', '/_core/about/index',
     'lucide:copyright', NULL, NULL,
     'menu', 'demo', 70, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'About Vben'),
    ('01HK153X280000000000000028', NULL, 'Demos', '演示', '/demos', NULL,
     'ic:baseline-view-in-ar', NULL, NULL,
     'catalog', 'demo', 1000, 1, 1, 1, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'Template demos'),
    ('01HK153X290000000000000029', '01HK153X280000000000000028', 'AntDesignDemos', 'Ant Design', '/demos/ant-design', '/demos/antd/index',
     NULL, NULL, NULL,
     'menu', 'demo', 10, 1, 1, 0, 0,
     0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'Ant Design component demos'),
    ('01HK153X2A000000000000002A', NULL, 'Profile', '个人中心', '/profile', '/_core/profile/index',
     'lucide:user', NULL, NULL,
     'menu', 'user', 10000, 1, 1, 0, 0,
     1, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'User profile')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    active_icon = EXCLUDED.active_icon,
    active_path = EXCLUDED.active_path,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    keep_alive = EXCLUDED.keep_alive,
    affix_tab = EXCLUDED.affix_tab,
    hide_in_menu = EXCLUDED.hide_in_menu,
    hide_children_in_menu = EXCLUDED.hide_children_in_menu,
    hide_in_breadcrumb = EXCLUDED.hide_in_breadcrumb,
    hide_in_tab = EXCLUDED.hide_in_tab,
    badge = EXCLUDED.badge,
    badge_type = EXCLUDED.badge_type,
    badge_variant = EXCLUDED.badge_variant,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001', m.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu m
ON CONFLICT (role_id, menu_id) DO NOTHING;
