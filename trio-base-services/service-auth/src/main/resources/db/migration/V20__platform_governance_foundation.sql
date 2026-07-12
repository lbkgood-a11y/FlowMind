-- Platform governance foundation: audit logs, sessions, dictionaries, and system configs.

CREATE TABLE IF NOT EXISTS sys_operation_audit_log (
    id               VARCHAR(32) PRIMARY KEY,
    tenant_id        VARCHAR(32) NOT NULL DEFAULT 'default',
    user_id          VARCHAR(32),
    username         VARCHAR(64),
    permission_code  VARCHAR(160),
    module_name      VARCHAR(64),
    action_name      VARCHAR(64),
    resource_id      VARCHAR(64),
    http_method      VARCHAR(16),
    request_path     VARCHAR(256),
    query_string     TEXT,
    client_ip        VARCHAR(64),
    user_agent       VARCHAR(512),
    request_summary  TEXT,
    response_summary TEXT,
    result_status    VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    status_code      INTEGER,
    error_message    VARCHAR(512),
    latency_ms       BIGINT,
    trace_id         VARCHAR(128),
    operated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(32),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(32),
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_operation_audit_log_user
    ON sys_operation_audit_log(tenant_id, user_id, operated_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_operation_audit_log_path
    ON sys_operation_audit_log(tenant_id, request_path, operated_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_operation_audit_log_result
    ON sys_operation_audit_log(tenant_id, result_status, operated_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_operation_audit_log_trace
    ON sys_operation_audit_log(trace_id);

CREATE TABLE IF NOT EXISTS sys_login_log (
    id             VARCHAR(32) PRIMARY KEY,
    tenant_id      VARCHAR(32) NOT NULL DEFAULT 'default',
    user_id        VARCHAR(32),
    username       VARCHAR(64),
    login_result   VARCHAR(16) NOT NULL,
    failure_reason VARCHAR(256),
    client_ip      VARCHAR(64),
    user_agent     VARCHAR(512),
    trace_id       VARCHAR(128),
    login_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(32),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(32),
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_login_log_user
    ON sys_login_log(tenant_id, username, login_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_result
    ON sys_login_log(tenant_id, login_result, login_at DESC);

CREATE TABLE IF NOT EXISTS sys_user_session (
    id                 VARCHAR(32) PRIMARY KEY,
    tenant_id          VARCHAR(32) NOT NULL DEFAULT 'default',
    user_id            VARCHAR(32) NOT NULL,
    username           VARCHAR(64) NOT NULL,
    access_jti         VARCHAR(64),
    refresh_jti        VARCHAR(64),
    session_status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    client_ip          VARCHAR(64),
    user_agent         VARCHAR(512),
    issued_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         TIMESTAMP,
    refresh_expires_at TIMESTAMP,
    last_active_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_at          TIMESTAMP,
    revoked_by         VARCHAR(32),
    revoked_at         TIMESTAMP,
    trace_id           VARCHAR(128),
    created_by         VARCHAR(32),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(32),
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_session_access_jti
    ON sys_user_session(access_jti)
    WHERE access_jti IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sys_user_session_user
    ON sys_user_session(tenant_id, user_id, last_active_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_user_session_status
    ON sys_user_session(tenant_id, session_status, last_active_at DESC);

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id          VARCHAR(32) PRIMARY KEY,
    tenant_id   VARCHAR(32) NOT NULL DEFAULT 'default',
    dict_code   VARCHAR(64) NOT NULL,
    dict_name   VARCHAR(128) NOT NULL,
    status      SMALLINT NOT NULL DEFAULT 1,
    system_flag SMALLINT NOT NULL DEFAULT 0,
    sort_order  INTEGER NOT NULL DEFAULT 100,
    description VARCHAR(256),
    created_by  VARCHAR(32),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(32),
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_type_code
    ON sys_dict_type(tenant_id, dict_code);
CREATE INDEX IF NOT EXISTS idx_sys_dict_type_status
    ON sys_dict_type(tenant_id, status, sort_order);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id          VARCHAR(32) PRIMARY KEY,
    tenant_id   VARCHAR(32) NOT NULL DEFAULT 'default',
    dict_type_id VARCHAR(32) NOT NULL REFERENCES sys_dict_type(id) ON DELETE CASCADE,
    dict_code   VARCHAR(64) NOT NULL,
    item_label  VARCHAR(128) NOT NULL,
    item_value  VARCHAR(128) NOT NULL,
    tag_type    VARCHAR(32),
    css_class   VARCHAR(64),
    status      SMALLINT NOT NULL DEFAULT 1,
    system_flag SMALLINT NOT NULL DEFAULT 0,
    sort_order  INTEGER NOT NULL DEFAULT 100,
    description VARCHAR(256),
    metadata    TEXT,
    created_by  VARCHAR(32),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(32),
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_item_value
    ON sys_dict_item(tenant_id, dict_code, item_value);
CREATE INDEX IF NOT EXISTS idx_sys_dict_item_code
    ON sys_dict_item(tenant_id, dict_code, status, sort_order);

CREATE TABLE IF NOT EXISTS sys_system_config (
    id            VARCHAR(32) PRIMARY KEY,
    tenant_id     VARCHAR(32) NOT NULL DEFAULT 'default',
    config_key    VARCHAR(128) NOT NULL,
    config_value  TEXT,
    default_value TEXT,
    config_type   VARCHAR(32) NOT NULL DEFAULT 'STRING',
    config_group  VARCHAR(64) NOT NULL DEFAULT 'general',
    sensitive     SMALLINT NOT NULL DEFAULT 0,
    system_flag   SMALLINT NOT NULL DEFAULT 0,
    status        SMALLINT NOT NULL DEFAULT 1,
    sort_order    INTEGER NOT NULL DEFAULT 100,
    description   VARCHAR(256),
    created_by    VARCHAR(32),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_system_config_key
    ON sys_system_config(tenant_id, config_key);
CREATE INDEX IF NOT EXISTS idx_sys_system_config_group
    ON sys_system_config(tenant_id, config_group, status, sort_order);

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P028', '/api/v1/audit-logs', 'GET', '查看操作审计日志'),
    ('P029', '/api/v1/sessions', 'GET', '查看登录日志和会话'),
    ('P030', '/api/v1/sessions/*', 'PUT', '强制失效用户会话'),
    ('P031', '/api/v1/dictionaries', 'GET', '查看数据字典'),
    ('P032', '/api/v1/dictionaries', 'POST', '新增数据字典'),
    ('P033', '/api/v1/dictionaries/*', 'PUT', '修改数据字典'),
    ('P034', '/api/v1/dictionaries/*', 'DELETE', '删除数据字典'),
    ('P035', '/api/v1/system-configs', 'GET', '查看系统参数'),
    ('P036', '/api/v1/system-configs/*', 'PUT', '修改系统参数')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('M011', 'M008', 'SystemAuditLog', '操作审计', '/system/audit-log', '/system/audit-log/list',
     'mdi:clipboard-text-clock-outline', NULL, NULL,
     'menu', 'system', 70, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P028', '/api/v1/audit-logs:GET', '平台操作审计日志'),
    ('M012', 'M008', 'SystemSession', '登录会话', '/system/session', '/system/session/list',
     'mdi:account-clock-outline', NULL, NULL,
     'menu', 'system', 80, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P029', '/api/v1/sessions:GET', '登录日志和在线会话管理'),
    ('M012_BTN_REVOKE', 'M012', 'SystemSessionRevoke', '强制失效', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P030', '/api/v1/sessions/*:PUT', '会话管理-强制失效'),
    ('M013', 'M008', 'SystemDictionary', '数据字典', '/system/dictionary', '/system/dictionary/list',
     'mdi:book-cog-outline', NULL, NULL,
     'menu', 'system', 90, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P031', '/api/v1/dictionaries:GET', '平台数据字典管理'),
    ('M013_BTN_CREATE', 'M013', 'SystemDictionaryCreate', '新增', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P032', '/api/v1/dictionaries:POST', '数据字典-新增'),
    ('M013_BTN_EDIT', 'M013', 'SystemDictionaryEdit', '修改', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P033', '/api/v1/dictionaries/*:PUT', '数据字典-修改'),
    ('M013_BTN_DELETE', 'M013', 'SystemDictionaryDelete', '删除', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 30, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P034', '/api/v1/dictionaries/*:DELETE', '数据字典-删除'),
    ('M014', 'M008', 'SystemConfig', '系统参数', '/system/config', '/system/config/list',
     'mdi:tune-variant', NULL, NULL,
     'menu', 'system', 100, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P035', '/api/v1/system-configs:GET', '平台系统参数配置'),
    ('M014_BTN_EDIT', 'M014', 'SystemConfigEdit', '修改', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P036', '/api/v1/system-configs/*:PUT', '系统参数-修改')
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
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001',
       m.id,
       'SYSTEM',
       'SYSTEM'
FROM sys_menu m
WHERE m.id IN (
    'M011',
    'M012',
    'M012_BTN_REVOKE',
    'M013',
    'M013_BTN_CREATE',
    'M013_BTN_EDIT',
    'M013_BTN_DELETE',
    'M014',
    'M014_BTN_EDIT'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_dict_type (
    id, tenant_id, dict_code, dict_name, status, system_flag, sort_order, description, created_by, updated_by
) VALUES
    ('DT_ORG_UNIT_TYPE', 'default', 'ORG_UNIT_TYPE', '组织类型', 1, 1, 10, '组织单元类型', 'SYSTEM', 'SYSTEM'),
    ('DT_USER_STATUS', 'default', 'USER_STATUS', '用户状态', 1, 1, 20, '用户启停状态', 'SYSTEM', 'SYSTEM'),
    ('DT_DATA_SCOPE_TYPE', 'default', 'DATA_SCOPE_TYPE', '数据范围类型', 1, 1, 30, '数据权限范围类型', 'SYSTEM', 'SYSTEM'),
    ('DT_DATA_POLICY_EFFECT', 'default', 'DATA_POLICY_EFFECT', '数据策略效果', 1, 1, 40, 'ALLOW/DENY 策略效果', 'SYSTEM', 'SYSTEM'),
    ('DT_RESOURCE_ACTION', 'default', 'RESOURCE_ACTION', '资源动作', 1, 1, 50, '业务资源动作枚举', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    dict_code = EXCLUDED.dict_code,
    dict_name = EXCLUDED.dict_name,
    status = EXCLUDED.status,
    system_flag = EXCLUDED.system_flag,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_dict_item (
    id, tenant_id, dict_type_id, dict_code, item_label, item_value, tag_type,
    status, system_flag, sort_order, description, created_by, updated_by
) VALUES
    ('DI_ORG_UNIT_COMPANY', 'default', 'DT_ORG_UNIT_TYPE', 'ORG_UNIT_TYPE', '公司', 'COMPANY', 'blue', 1, 1, 10, '公司/法人主体', 'SYSTEM', 'SYSTEM'),
    ('DI_ORG_UNIT_DEPARTMENT', 'default', 'DT_ORG_UNIT_TYPE', 'ORG_UNIT_TYPE', '部门', 'DEPARTMENT', 'green', 1, 1, 20, '行政部门', 'SYSTEM', 'SYSTEM'),
    ('DI_ORG_UNIT_TEAM', 'default', 'DT_ORG_UNIT_TYPE', 'ORG_UNIT_TYPE', '小组', 'TEAM', 'cyan', 1, 1, 30, '团队或小组', 'SYSTEM', 'SYSTEM'),
    ('DI_ORG_UNIT_COST_CENTER', 'default', 'DT_ORG_UNIT_TYPE', 'ORG_UNIT_TYPE', '成本中心', 'COST_CENTER', 'orange', 1, 1, 40, '核算成本中心', 'SYSTEM', 'SYSTEM'),
    ('DI_USER_STATUS_ENABLED', 'default', 'DT_USER_STATUS', 'USER_STATUS', '启用', '1', 'green', 1, 1, 10, '启用状态', 'SYSTEM', 'SYSTEM'),
    ('DI_USER_STATUS_DISABLED', 'default', 'DT_USER_STATUS', 'USER_STATUS', '禁用', '0', 'default', 1, 1, 20, '禁用状态', 'SYSTEM', 'SYSTEM'),
    ('DI_SCOPE_SELF', 'default', 'DT_DATA_SCOPE_TYPE', 'DATA_SCOPE_TYPE', '本人', 'SELF', 'blue', 1, 1, 10, '仅本人数据', 'SYSTEM', 'SYSTEM'),
    ('DI_SCOPE_OWN_ORG', 'default', 'DT_DATA_SCOPE_TYPE', 'DATA_SCOPE_TYPE', '本组织', 'OWN_ORG', 'green', 1, 1, 20, '当前主组织', 'SYSTEM', 'SYSTEM'),
    ('DI_SCOPE_OWN_ORG_CHILDREN', 'default', 'DT_DATA_SCOPE_TYPE', 'DATA_SCOPE_TYPE', '本组织及下级', 'OWN_ORG_AND_CHILDREN', 'cyan', 1, 1, 30, '当前主组织及下级', 'SYSTEM', 'SYSTEM'),
    ('DI_SCOPE_ASSIGNED', 'default', 'DT_DATA_SCOPE_TYPE', 'DATA_SCOPE_TYPE', '指定组织', 'ASSIGNED_ORGS', 'orange', 1, 1, 40, '显式指定组织集合', 'SYSTEM', 'SYSTEM'),
    ('DI_SCOPE_ALL', 'default', 'DT_DATA_SCOPE_TYPE', 'DATA_SCOPE_TYPE', '全部', 'ALL', 'purple', 1, 1, 50, '全部数据', 'SYSTEM', 'SYSTEM'),
    ('DI_EFFECT_ALLOW', 'default', 'DT_DATA_POLICY_EFFECT', 'DATA_POLICY_EFFECT', '允许', 'ALLOW', 'green', 1, 1, 10, '允许访问', 'SYSTEM', 'SYSTEM'),
    ('DI_EFFECT_DENY', 'default', 'DT_DATA_POLICY_EFFECT', 'DATA_POLICY_EFFECT', '拒绝', 'DENY', 'red', 1, 1, 20, '拒绝访问', 'SYSTEM', 'SYSTEM'),
    ('DI_ACTION_QUERY', 'default', 'DT_RESOURCE_ACTION', 'RESOURCE_ACTION', '查询', 'QUERY', 'blue', 1, 1, 10, '查询动作', 'SYSTEM', 'SYSTEM'),
    ('DI_ACTION_CREATE', 'default', 'DT_RESOURCE_ACTION', 'RESOURCE_ACTION', '创建', 'CREATE', 'green', 1, 1, 20, '创建动作', 'SYSTEM', 'SYSTEM'),
    ('DI_ACTION_UPDATE', 'default', 'DT_RESOURCE_ACTION', 'RESOURCE_ACTION', '修改', 'UPDATE', 'orange', 1, 1, 30, '修改动作', 'SYSTEM', 'SYSTEM'),
    ('DI_ACTION_EXPORT', 'default', 'DT_RESOURCE_ACTION', 'RESOURCE_ACTION', '导出', 'EXPORT', 'purple', 1, 1, 40, '导出动作', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    dict_type_id = EXCLUDED.dict_type_id,
    dict_code = EXCLUDED.dict_code,
    item_label = EXCLUDED.item_label,
    item_value = EXCLUDED.item_value,
    tag_type = EXCLUDED.tag_type,
    status = EXCLUDED.status,
    system_flag = EXCLUDED.system_flag,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_system_config (
    id, tenant_id, config_key, config_value, default_value, config_type, config_group,
    sensitive, system_flag, status, sort_order, description, created_by, updated_by
) VALUES
    ('CFG_PASSWORD_MIN_LENGTH', 'default', 'security.password.min_length', '8', '8', 'INTEGER', 'security', 0, 1, 1, 10, '密码最小长度', 'SYSTEM', 'SYSTEM'),
    ('CFG_PASSWORD_REQUIRE_MIXED', 'default', 'security.password.require_mixed', 'true', 'true', 'BOOLEAN', 'security', 0, 1, 1, 20, '密码是否要求大小写和数字', 'SYSTEM', 'SYSTEM'),
    ('CFG_AUTH_ACCESS_TTL', 'default', 'auth.access_token_ttl', '300', '300', 'INTEGER', 'auth', 0, 1, 1, 10, 'Access Token 有效期秒数', 'SYSTEM', 'SYSTEM'),
    ('CFG_AUTH_REFRESH_TTL', 'default', 'auth.refresh_token_ttl', '1800', '1800', 'INTEGER', 'auth', 0, 1, 1, 20, 'Refresh Token 有效期秒数', 'SYSTEM', 'SYSTEM'),
    ('CFG_UI_DEFAULT_HOME', 'default', 'ui.default_home_path', '/dashboard/analytics', '/dashboard/analytics', 'STRING', 'ui', 0, 1, 1, 10, '登录后的默认首页', 'SYSTEM', 'SYSTEM'),
    ('CFG_ORG_DEFAULT_DIMENSION', 'default', 'org.default_dimension', 'ADMIN', 'ADMIN', 'STRING', 'org', 0, 1, 1, 10, '默认组织维度', 'SYSTEM', 'SYSTEM'),
    ('CFG_AUTH_JWT_SECRET_ALIAS', 'default', 'auth.jwt.secret_alias', 'triobase-default', 'triobase-default', 'STRING', 'auth', 1, 1, 1, 30, 'JWT 密钥别名，敏感展示', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    config_key = EXCLUDED.config_key,
    config_value = EXCLUDED.config_value,
    default_value = EXCLUDED.default_value,
    config_type = EXCLUDED.config_type,
    config_group = EXCLUDED.config_group,
    sensitive = EXCLUDED.sensitive,
    system_flag = EXCLUDED.system_flag,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
