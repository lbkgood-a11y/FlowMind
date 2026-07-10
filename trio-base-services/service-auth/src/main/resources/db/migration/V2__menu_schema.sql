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
    ('P011', '/api/v1/forms',       'GET',    '查看表单列表'),
    ('P012', '/api/v1/forms',       'POST',   '创建表单'),
    ('P013', '/api/v1/menus',       'GET',    '查看菜单列表'),
    ('P014', '/api/v1/menus',       'POST',   '创建菜单'),
    ('P015', '/api/v1/menus/*',     'DELETE', '删除菜单'),
    ('P016', '/api/v1/permissions', 'GET',    '查看权限列表'),
    ('P017', '/api/v1/permissions', 'POST',   '创建权限'),
    ('P018', '/api/v1/permissions/*', 'DELETE', '删除权限')
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu(id, parent_id, menu_key, menu_name, path, icon, menu_group, sort_order, visible, permission_id, description) VALUES
    ('M001', NULL, 'dashboard',   '仪表盘',   '/dashboard',        NULL, 'general', 10, 1, NULL,  '工作台与分析概览'),
    ('M002', NULL, 'forms',       '表单管理', '/forms',            NULL, 'forms',   20, 1, 'P011', '表单列表与发布管理'),
    ('M003', NULL, 'form-create', '新建表单', '/forms/new',        NULL, 'forms',   30, 1, 'P012', '创建新的低代码表单'),
    ('M004', NULL, 'users',       '用户管理', '/system/user',      NULL, 'admin',   40, 1, 'P001', '用户状态与角色分配'),
    ('M005', NULL, 'roles',       '角色管理', '/system/role',      NULL, 'admin',   50, 1, 'P005', '角色与权限绑定'),
    ('M006', NULL, 'menus',       '菜单管理', '/system/menu',      NULL, 'admin',   60, 1, 'P013', '后台菜单目录管理'),
    ('M007', NULL, 'permissions', '授权管理', '/admin/permissions', NULL, 'admin',   70, 1, 'P016', '资源路径与动作级权限项')
ON CONFLICT DO NOTHING;
