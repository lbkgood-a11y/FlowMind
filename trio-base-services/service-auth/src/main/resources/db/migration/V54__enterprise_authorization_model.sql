-- Enterprise authorization model: resource registry, grants, field policies,
-- guard templates, decision audit, and tenant-aware user context.

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_sys_user_tenant_status
    ON sys_user(tenant_id, status);

CREATE TABLE IF NOT EXISTS sys_auth_resource (
    id                 VARCHAR(32) PRIMARY KEY,
    tenant_id          VARCHAR(32) NOT NULL DEFAULT 'default',
    resource_code      VARCHAR(160) NOT NULL,
    resource_type      VARCHAR(64) NOT NULL,
    owner_service      VARCHAR(64) NOT NULL,
    business_object_id VARCHAR(64),
    display_name       VARCHAR(160) NOT NULL,
    lifecycle_status   VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    global_flag        SMALLINT NOT NULL DEFAULT 0,
    metadata_json      TEXT,
    last_synced_at     TIMESTAMP,
    created_by         VARCHAR(32),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(32),
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_auth_resource_tenant_code
    ON sys_auth_resource(tenant_id, resource_code);
CREATE INDEX IF NOT EXISTS idx_sys_auth_resource_owner
    ON sys_auth_resource(tenant_id, owner_service, resource_type);
CREATE INDEX IF NOT EXISTS idx_sys_auth_resource_lifecycle
    ON sys_auth_resource(tenant_id, lifecycle_status, updated_at DESC);

CREATE TABLE IF NOT EXISTS sys_auth_action (
    id              VARCHAR(32) PRIMARY KEY,
    tenant_id       VARCHAR(32) NOT NULL DEFAULT 'default',
    resource_code   VARCHAR(160) NOT NULL,
    action_code     VARCHAR(64) NOT NULL,
    action_category VARCHAR(64),
    description     VARCHAR(256),
    guard_codes     TEXT,
    status          SMALLINT NOT NULL DEFAULT 1,
    created_by      VARCHAR(32),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(32),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_auth_action_tenant_resource_action
    ON sys_auth_action(tenant_id, resource_code, action_code);
CREATE INDEX IF NOT EXISTS idx_sys_auth_action_resource
    ON sys_auth_action(tenant_id, resource_code, status);

CREATE TABLE IF NOT EXISTS sys_auth_field (
    id                         VARCHAR(32) PRIMARY KEY,
    tenant_id                  VARCHAR(32) NOT NULL DEFAULT 'default',
    resource_code              VARCHAR(160) NOT NULL,
    field_key                  VARCHAR(128) NOT NULL,
    field_label                VARCHAR(160),
    field_type                 VARCHAR(64),
    sensitivity_classification VARCHAR(64),
    default_mask_strategy      VARCHAR(64),
    status                     SMALLINT NOT NULL DEFAULT 1,
    created_by                 VARCHAR(32),
    created_at                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 VARCHAR(32),
    updated_at                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_auth_field_tenant_resource_field
    ON sys_auth_field(tenant_id, resource_code, field_key);
CREATE INDEX IF NOT EXISTS idx_sys_auth_field_resource
    ON sys_auth_field(tenant_id, resource_code, status);

CREATE TABLE IF NOT EXISTS sys_auth_grant (
    id            VARCHAR(32) PRIMARY KEY,
    tenant_id     VARCHAR(32) NOT NULL DEFAULT 'default',
    subject_type  VARCHAR(32) NOT NULL,
    subject_id    VARCHAR(32) NOT NULL,
    resource_code VARCHAR(160) NOT NULL,
    action_code   VARCHAR(64) NOT NULL,
    effect        VARCHAR(16) NOT NULL DEFAULT 'ALLOW',
    status        SMALLINT NOT NULL DEFAULT 1,
    description   VARCHAR(256),
    created_by    VARCHAR(32),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_auth_grant_subject_resource_action_effect
    ON sys_auth_grant(tenant_id, subject_type, subject_id, resource_code, action_code, effect);
CREATE INDEX IF NOT EXISTS idx_sys_auth_grant_subject
    ON sys_auth_grant(tenant_id, subject_type, subject_id, status);
CREATE INDEX IF NOT EXISTS idx_sys_auth_grant_resource_action
    ON sys_auth_grant(tenant_id, resource_code, action_code, status);

CREATE TABLE IF NOT EXISTS sys_auth_field_policy (
    id            VARCHAR(32) PRIMARY KEY,
    tenant_id     VARCHAR(32) NOT NULL DEFAULT 'default',
    subject_type  VARCHAR(32) NOT NULL,
    subject_id    VARCHAR(32) NOT NULL,
    resource_code VARCHAR(160) NOT NULL,
    field_key     VARCHAR(128) NOT NULL,
    read_mode     VARCHAR(32) NOT NULL DEFAULT 'VISIBLE',
    write_mode    VARCHAR(32) NOT NULL DEFAULT 'EDITABLE',
    mask_strategy VARCHAR(64),
    effect        VARCHAR(16) NOT NULL DEFAULT 'ALLOW',
    status        SMALLINT NOT NULL DEFAULT 1,
    description   VARCHAR(256),
    created_by    VARCHAR(32),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(32),
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_auth_field_policy_subject_field
    ON sys_auth_field_policy(tenant_id, subject_type, subject_id, resource_code, field_key, effect);
CREATE INDEX IF NOT EXISTS idx_sys_auth_field_policy_subject
    ON sys_auth_field_policy(tenant_id, subject_type, subject_id, status);
CREATE INDEX IF NOT EXISTS idx_sys_auth_field_policy_resource_field
    ON sys_auth_field_policy(tenant_id, resource_code, field_key, status);

CREATE TABLE IF NOT EXISTS sys_auth_guard_template (
    id                       VARCHAR(32) PRIMARY KEY,
    tenant_id                VARCHAR(32) NOT NULL DEFAULT 'default',
    guard_code               VARCHAR(96) NOT NULL,
    owner_service            VARCHAR(64) NOT NULL,
    supported_resource_types TEXT,
    config_schema_json       TEXT,
    description              VARCHAR(256),
    status                   SMALLINT NOT NULL DEFAULT 1,
    created_by               VARCHAR(32),
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(32),
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_auth_guard_template_tenant_code
    ON sys_auth_guard_template(tenant_id, guard_code);
CREATE INDEX IF NOT EXISTS idx_sys_auth_guard_template_owner
    ON sys_auth_guard_template(tenant_id, owner_service, status);

CREATE TABLE IF NOT EXISTS sys_auth_decision_log (
    id                     VARCHAR(32) PRIMARY KEY,
    tenant_id              VARCHAR(32) NOT NULL DEFAULT 'default',
    user_id                VARCHAR(32),
    subject_snapshot       TEXT,
    resource_code          VARCHAR(160) NOT NULL,
    action_code            VARCHAR(64) NOT NULL,
    allowed                SMALLINT NOT NULL,
    reason_codes           TEXT,
    matched_grant_id       VARCHAR(32),
    data_scope_snapshot    TEXT,
    field_rule_snapshot    TEXT,
    guard_snapshot         TEXT,
    auth_version           BIGINT,
    role_version           BIGINT,
    data_policy_version    BIGINT,
    field_policy_version   BIGINT,
    guard_template_version BIGINT,
    owner_service          VARCHAR(64),
    business_object_id     VARCHAR(64),
    trace_id               VARCHAR(128),
    decided_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(32),
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(32),
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_auth_decision_log_subject
    ON sys_auth_decision_log(tenant_id, user_id, decided_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_auth_decision_log_resource
    ON sys_auth_decision_log(tenant_id, resource_code, action_code, decided_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_auth_decision_log_allowed
    ON sys_auth_decision_log(tenant_id, allowed, decided_at DESC);

CREATE TABLE IF NOT EXISTS sys_auth_version (
    version_key   VARCHAR(64) PRIMARY KEY,
    version_value BIGINT NOT NULL DEFAULT 1,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_auth_version(version_key, version_value) VALUES
    ('AUTHORIZATION', 1),
    ('RESOURCE', 1),
    ('GRANT', 1),
    ('FIELD_POLICY', 1),
    ('GUARD_TEMPLATE', 1),
    ('DATA_POLICY', 1)
ON CONFLICT (version_key) DO NOTHING;

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P_AUTHZ_READ', '/api/v1/authz/**', 'GET', '查看企业授权资源和决策'),
    ('P_AUTHZ_WRITE', '/api/v1/authz/**', 'POST', '配置企业授权资源、授权和预览决策'),
    ('P_AUTHZ_DELETE', '/api/v1/authz/**', 'DELETE', '删除企业授权配置')
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
    ('M_AUTHZ', 'M008', 'SystemAuthorization', '企业授权', '/system/authorization',
     '/system/authorization/list', 'mdi:shield-account-outline', NULL, NULL,
     'menu', 'system', 65, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P_AUTHZ_READ', '/api/v1/authz/**:GET', '企业级资源授权、数据范围、字段权限和决策预览'),
    ('M_AUTHZ_BTN_WRITE', 'M_AUTHZ', 'SystemAuthorizationWrite', '配置', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P_AUTHZ_WRITE', '/api/v1/authz/**:POST', '企业授权-配置'),
    ('M_AUTHZ_BTN_DELETE', 'M_AUTHZ', 'SystemAuthorizationDelete', '删除', NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P_AUTHZ_DELETE', '/api/v1/authz/**:DELETE', '企业授权-删除')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    hide_in_menu = EXCLUDED.hide_in_menu,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT 'AZ' || upper(substr(md5('R001:' || menu.id), 1, 24)),
       'R001',
       menu.id,
       'SYSTEM',
       'SYSTEM'
FROM sys_menu menu
WHERE menu.id IN ('M_AUTHZ', 'M_AUTHZ_BTN_WRITE', 'M_AUTHZ_BTN_DELETE')
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_auth_guard_template (
    id, tenant_id, guard_code, owner_service, supported_resource_types,
    config_schema_json, description, status, created_by, updated_by
) VALUES
    ('AGT_WORKFLOW_CANDIDATE', 'default', 'WORKFLOW_CANDIDATE', 'service-workflow-engine',
     'LOWCODE_FORM,CUSTOM_DOC,WORKFLOW_TASK', NULL, '当前用户必须是待办任务候选人或处理人', 1, 'SYSTEM', 'SYSTEM'),
    ('AGT_NO_SELF_APPROVAL', 'default', 'NO_SELF_APPROVAL', 'service-workflow-engine',
     'LOWCODE_FORM,CUSTOM_DOC,WORKFLOW_TASK', NULL, '发起人不可审批自己的单据', 1, 'SYSTEM', 'SYSTEM'),
    ('AGT_DOCUMENT_STATUS', 'default', 'DOCUMENT_STATUS', 'service-lowcode',
     'LOWCODE_FORM,CUSTOM_DOC', NULL, '单据状态必须允许当前操作', 1, 'SYSTEM', 'SYSTEM'),
    ('AGT_ARCHIVED_LOCK', 'default', 'ARCHIVED_LOCK', 'service-lowcode',
     'LOWCODE_FORM,CUSTOM_DOC', NULL, '已归档单据不可编辑', 1, 'SYSTEM', 'SYSTEM')
ON CONFLICT (tenant_id, guard_code) DO UPDATE SET
    owner_service = EXCLUDED.owner_service,
    supported_resource_types = EXCLUDED.supported_resource_types,
    config_schema_json = EXCLUDED.config_schema_json,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH permission_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           p.resource AS resource_code,
           p.action AS action_code,
           COALESCE(p.description, p.resource || ':' || p.action) AS display_name
    FROM sys_permission p
    WHERE p.resource IS NOT NULL AND p.action IS NOT NULL
    UNION
    SELECT DISTINCT
           'default' AS tenant_id,
           split_part(m.permission_code, ':', 1) AS resource_code,
           split_part(m.permission_code, ':', 2) AS action_code,
           COALESCE(m.description, m.menu_name, m.permission_code) AS display_name
    FROM sys_menu m
    WHERE m.permission_code IS NOT NULL
      AND m.permission_code <> ''
      AND position(':' in m.permission_code) > 0
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5(tenant_id || ':' || resource_code), 1, 24)),
       tenant_id,
       resource_code,
       CASE WHEN resource_code LIKE '/api/%' THEN 'API_OPERATION' ELSE 'LEGACY_PERMISSION' END,
       'service-auth',
       max(display_name),
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM permission_codes
WHERE resource_code IS NOT NULL AND resource_code <> ''
GROUP BY tenant_id, resource_code
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH permission_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           p.resource AS resource_code,
           p.action AS action_code,
           COALESCE(p.description, p.resource || ':' || p.action) AS description
    FROM sys_permission p
    WHERE p.resource IS NOT NULL AND p.action IS NOT NULL
    UNION
    SELECT DISTINCT
           'default' AS tenant_id,
           split_part(m.permission_code, ':', 1) AS resource_code,
           split_part(m.permission_code, ':', 2) AS action_code,
           COALESCE(m.description, m.menu_name, m.permission_code) AS description
    FROM sys_menu m
    WHERE m.permission_code IS NOT NULL
      AND m.permission_code <> ''
      AND position(':' in m.permission_code) > 0
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5(tenant_id || ':' || resource_code || ':' || action_code), 1, 24)),
       tenant_id,
       resource_code,
       action_code,
       CASE WHEN resource_code LIKE '/api/%' THEN 'API' ELSE 'BUSINESS' END,
       max(description),
       1,
       'SYSTEM',
       'SYSTEM'
FROM permission_codes
WHERE resource_code IS NOT NULL AND resource_code <> ''
  AND action_code IS NOT NULL AND action_code <> ''
GROUP BY tenant_id, resource_code, action_code
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH role_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           rm.role_id,
           COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action) AS permission_code
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.id = rm.menu_id
    LEFT JOIN sys_permission p ON p.id = m.permission_id
),
split_codes AS (
    SELECT tenant_id,
           role_id,
           split_part(permission_code, ':', 1) AS resource_code,
           split_part(permission_code, ':', 2) AS action_code
    FROM role_codes
    WHERE permission_code IS NOT NULL
      AND permission_code <> ''
      AND position(':' in permission_code) > 0
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5(tenant_id || ':ROLE:' || role_id || ':' || resource_code || ':' || action_code), 1, 24)),
       tenant_id,
       'ROLE',
       role_id,
       resource_code,
       action_code,
       'ALLOW',
       1,
       'Legacy role-menu permission backfill',
       'SYSTEM',
       'SYSTEM'
FROM split_codes
WHERE resource_code IS NOT NULL AND resource_code <> ''
  AND action_code IS NOT NULL AND action_code <> ''
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO NOTHING;
