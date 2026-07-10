CREATE TABLE IF NOT EXISTS sys_menu (
    id            VARCHAR(32)  PRIMARY KEY,
    parent_id     VARCHAR(32)  REFERENCES sys_menu(id),
    menu_key      VARCHAR(64)  NOT NULL,
    menu_name     VARCHAR(128) NOT NULL,
    path          VARCHAR(256) NOT NULL,
    icon          VARCHAR(64),
    menu_group    VARCHAR(32)  NOT NULL DEFAULT 'general',
    sort_order    INTEGER      NOT NULL DEFAULT 100,
    visible       SMALLINT     NOT NULL DEFAULT 1,
    permission_id VARCHAR(32)  REFERENCES sys_permission(id),
    description   VARCHAR(256),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_menu_key ON sys_menu(menu_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_menu_path ON sys_menu(path);
CREATE INDEX IF NOT EXISTS idx_sys_menu_group_sort ON sys_menu(menu_group, sort_order);

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('01HK153X0F000000000000000F', '/api/v1/forms',       'GET',    '查看表单列表'),
    ('01HK153X0G000000000000000G', '/api/v1/forms',       'POST',   '创建表单'),
    ('01HK153X0H000000000000000H', '/api/v1/menus',       'GET',    '查看菜单列表'),
    ('01HK153X0J000000000000000J', '/api/v1/menus',       'POST',   '创建菜单'),
    ('01HK153X0K000000000000000K', '/api/v1/menus/*',     'DELETE', '删除菜单'),
    ('01HK153X0M000000000000000M', '/api/v1/permissions', 'GET',    '查看权限列表'),
    ('01HK153X0N000000000000000N', '/api/v1/permissions', 'POST',   '创建权限'),
    ('01HK153X0P000000000000000P', '/api/v1/permissions/*', 'DELETE', '删除权限')
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu(id, parent_id, menu_key, menu_name, path, icon, menu_group, sort_order, visible, permission_id, description) VALUES
    ('01HK153X0Q000000000000000Q', NULL, 'dashboard',   'Dashboard', '/',                  'LayoutDashboard', 'general', 10, 1, NULL,   '后台首页与平台概览'),
    ('01HK153X0R000000000000000R', NULL, 'forms',       '表单管理',  '/forms',             'FileText',        'forms',   20, 1, '01HK153X0F000000000000000F', '表单列表与发布管理'),
    ('01HK153X0S000000000000000S', NULL, 'form-create', '新建表单',  '/forms/new',         'FilePlus2',       'forms',   30, 1, '01HK153X0G000000000000000G', '创建新的低代码表单'),
    ('01HK153X0T000000000000000T', NULL, 'users',       '用户管理',  '/admin/users',       'Users',           'admin',   40, 1, '01HK153X050000000000000005', '用户状态与角色分配'),
    ('01HK153X0V000000000000000V', NULL, 'roles',       '角色管理',  '/admin/roles',       'Shield',          'admin',   50, 1, '01HK153X090000000000000009', '角色与权限绑定'),
    ('01HK153X0W000000000000000W', NULL, 'menus',       '菜单管理',  '/admin/menus',       'ListTree',        'admin',   60, 1, '01HK153X0H000000000000000H', '后台菜单目录管理'),
    ('01HK153X0X000000000000000X', NULL, 'permissions', '授权管理',  '/admin/permissions', 'KeySquare',       'admin',   70, 1, '01HK153X0M000000000000000M', '资源路径与动作级权限项')
ON CONFLICT DO NOTHING;
