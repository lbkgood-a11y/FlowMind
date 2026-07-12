-- Organization management and role data-permission policy foundation.

CREATE TABLE IF NOT EXISTS sys_data_policy (
    id            VARCHAR(32) PRIMARY KEY,
    tenant_id     VARCHAR(32) NOT NULL DEFAULT 'default',
    subject_type  VARCHAR(32) NOT NULL,
    subject_id    VARCHAR(32) NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    action_code   VARCHAR(32) NOT NULL,
    effect        VARCHAR(16) NOT NULL DEFAULT 'ALLOW',
    combine_mode  VARCHAR(16) NOT NULL DEFAULT 'AND',
    status        SMALLINT    NOT NULL DEFAULT 1,
    description   VARCHAR(256),
    created_by    VARCHAR(32),
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_data_policy_subject
    ON sys_data_policy(tenant_id, subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_sys_data_policy_resource_action
    ON sys_data_policy(tenant_id, resource_code, action_code, status);

CREATE TABLE IF NOT EXISTS sys_data_policy_dimension (
    id             VARCHAR(32) PRIMARY KEY,
    policy_id      VARCHAR(32) NOT NULL REFERENCES sys_data_policy(id) ON DELETE CASCADE,
    dimension_code VARCHAR(64) NOT NULL,
    scope_type     VARCHAR(64) NOT NULL,
    org_unit_ids   TEXT,
    sort_order     INTEGER     NOT NULL DEFAULT 100,
    created_by     VARCHAR(32),
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(32),
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_data_policy_dimension
    ON sys_data_policy_dimension(policy_id, dimension_code);
CREATE INDEX IF NOT EXISTS idx_sys_data_policy_dimension_scope
    ON sys_data_policy_dimension(dimension_code, scope_type);

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P019', '/api/v1/org/units', 'GET', '查看组织管理'),
    ('P020', '/api/v1/org/units', 'POST', '新增组织单元'),
    ('P021', '/api/v1/org/units/*', 'PUT', '修改组织单元、组织关系和用户组织归属'),
    ('P022', '/api/v1/org/units/*', 'DELETE', '删除组织单元或组织关系'),
    ('P023', '/api/v1/data-policies', 'GET', '查看数据权限策略'),
    ('P024', '/api/v1/data-policies', 'POST', '新增数据权限策略'),
    ('P025', '/api/v1/data-policies/*', 'PUT', '修改数据权限策略'),
    ('P026', '/api/v1/data-policies/*', 'DELETE', '删除数据权限策略')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_menu
SET parent_id = 'M008',
    menu_key = 'SystemOrg',
    menu_name = '组织管理',
    path = '/system/org',
    component = '/system/org/list',
    icon = 'charm:organisation',
    menu_type = 'menu',
    menu_group = 'system',
    sort_order = 50,
    visible = 1,
    status = 1,
    permission_id = 'P019',
    permission_code = '/api/v1/org/units:GET',
    description = '多维组织、组织树和用户组织归属'
WHERE id = 'M009';

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('M009_BTN_CREATE', 'M009', 'SystemOrgCreate', '新增', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P020', '/api/v1/org/units:POST', '组织管理-新增组织单元'),
    ('M009_BTN_EDIT', 'M009', 'SystemOrgEdit', '修改', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P021', '/api/v1/org/units/*:PUT', '组织管理-编辑组织、关系和用户归属'),
    ('M009_BTN_DELETE', 'M009', 'SystemOrgDelete', '删除', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P022', '/api/v1/org/units/*:DELETE', '组织管理-删除组织或关系'),
    ('M010', 'M008', 'SystemDataPermission', '数据权限', '/system/data-permission', '/system/data-permission/list',
     'mdi:shield-key-outline', NULL, NULL,
     'menu', 'system', 60, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P023', '/api/v1/data-policies:GET', '角色级数据权限策略'),
    ('M010_BTN_CREATE', 'M010', 'SystemDataPermissionCreate', '新增', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P024', '/api/v1/data-policies:POST', '数据权限-新增策略'),
    ('M010_BTN_EDIT', 'M010', 'SystemDataPermissionEdit', '修改', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P025', '/api/v1/data-policies/*:PUT', '数据权限-修改策略'),
    ('M010_BTN_DELETE', 'M010', 'SystemDataPermissionDelete', '删除', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P026', '/api/v1/data-policies/*:DELETE', '数据权限-删除策略')
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
       'R001',
       m.id,
       'SYSTEM',
       'SYSTEM'
FROM sys_menu m
WHERE m.id IN (
    'M009',
    'M009_BTN_CREATE',
    'M009_BTN_EDIT',
    'M009_BTN_DELETE',
    'M010',
    'M010_BTN_CREATE',
    'M010_BTN_EDIT',
    'M010_BTN_DELETE'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;
