-- Expense form closed loop: published form fixture, menu permissions and baseline data policies.

-- 兼容已有表缺少审计列的情况（lc_form_definition/lc_form_field_definition 由 lowcode 模块创建）
ALTER TABLE IF EXISTS lc_form_definition
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS lc_form_field_definition
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS lc_form_definition (
    id             VARCHAR(32) PRIMARY KEY,
    form_key       VARCHAR(128) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(512),
    version        INTEGER NOT NULL DEFAULT 1,
    status         VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    schema_json    TEXT,
    ui_schema_json TEXT,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64),
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_definition_key ON lc_form_definition(form_key);

CREATE TABLE IF NOT EXISTS lc_form_field_definition (
    id                 VARCHAR(32) PRIMARY KEY,
    form_definition_id VARCHAR(32) NOT NULL REFERENCES lc_form_definition(id) ON DELETE CASCADE,
    field_key          VARCHAR(128) NOT NULL,
    label              VARCHAR(255) NOT NULL,
    field_type         VARCHAR(64) NOT NULL,
    required_flag      SMALLINT NOT NULL DEFAULT 0,
    default_value      TEXT,
    placeholder        VARCHAR(255),
    options_json       TEXT,
    sort_order         INTEGER NOT NULL DEFAULT 0,
    created_by         VARCHAR(64),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64),
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_form_field_key
    ON lc_form_field_definition(form_definition_id, field_key);

INSERT INTO lc_form_definition (
    id, form_key, name, description, version, status, schema_json, ui_schema_json,
    created_by, updated_by
) VALUES (
    'LC_FORM_EXPENSE_001', 'expense', U&'\8D39\7528\62A5\9500',
    U&'\8D39\7528\62A5\9500\6F14\793A\8868\5355\FF0C\7528\4E8E\9A8C\8BC1\6570\636E\6743\9650\95ED\73AF',
    1, 'PUBLISHED',
    '{"additionalProperties":false,"properties":{"amount":{"minimum":0.01,"title":"金额","type":"number"},"reason":{"maxLength":200,"title":"事由","type":"string"}},"required":["amount","reason"],"title":"费用报销","type":"object"}',
    '{"amount":{"ui:placeholder":"请输入报销金额","ui:widget":"money"},"reason":{"ui:placeholder":"请输入报销事由","ui:widget":"textarea"}}',
    'SYSTEM', 'SYSTEM'
)
ON CONFLICT (form_key) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 'PUBLISHED',
    schema_json = EXCLUDED.schema_json,
    ui_schema_json = EXCLUDED.ui_schema_json,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO lc_form_field_definition (
    id, form_definition_id, field_key, label, field_type, required_flag,
    placeholder, sort_order, created_by, updated_by
)
SELECT 'LC_FIELD_EXPENSE_AMOUNT', id, 'amount', U&'\91D1\989D', 'number', 1,
       U&'\8BF7\8F93\5165\62A5\9500\91D1\989D', 10, 'SYSTEM', 'SYSTEM'
FROM lc_form_definition WHERE form_key = 'expense'
ON CONFLICT (form_definition_id, field_key) DO UPDATE SET
    label = EXCLUDED.label, field_type = EXCLUDED.field_type,
    required_flag = EXCLUDED.required_flag, placeholder = EXCLUDED.placeholder,
    sort_order = EXCLUDED.sort_order, updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO lc_form_field_definition (
    id, form_definition_id, field_key, label, field_type, required_flag,
    placeholder, sort_order, created_by, updated_by
)
SELECT 'LC_FIELD_EXPENSE_REASON', id, 'reason', U&'\4E8B\7531', 'textarea', 1,
       U&'\8BF7\8F93\5165\62A5\9500\4E8B\7531', 20, 'SYSTEM', 'SYSTEM'
FROM lc_form_definition WHERE form_key = 'expense'
ON CONFLICT (form_definition_id, field_key) DO UPDATE SET
    label = EXCLUDED.label, field_type = EXCLUDED.field_type,
    required_flag = EXCLUDED.required_flag, placeholder = EXCLUDED.placeholder,
    sort_order = EXCLUDED.sort_order, updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P085', '/api/v1/forms/expense/instances', 'GET', 'View expense form instances'),
    ('P086', '/api/v1/forms/expense/submit', 'POST', 'Submit expense form instance')
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
    ('M032', 'M030', 'LowcodeExpense', U&'\8D39\7528\62A5\9500', '/lowcode/expense', '/lowcode/expense/list',
     'mdi:receipt-text-edit-outline', NULL, NULL,
     'menu', 'lowcode', 20, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P085', '/api/v1/forms/expense/instances:GET', 'Expense form data closure'),
    ('M032_BTN_CREATE', 'M032', 'LowcodeExpenseCreate', U&'\53D1\8D77\62A5\9500', NULL, NULL, NULL, NULL, NULL,
     'button', 'lowcode', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P086', '/api/v1/forms/expense/submit:POST', 'Submit expense form')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id, menu_key = EXCLUDED.menu_key, menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path, component = EXCLUDED.component, icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type, menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order, visible = EXCLUDED.visible, status = EXCLUDED.status,
    hide_in_menu = EXCLUDED.hide_in_menu, permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code, description = EXCLUDED.description,
    updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(r.id || ':' || m.id), 1, 24)), r.id, m.id, 'SYSTEM', 'SYSTEM'
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.id IN ('R001', 'R002', 'R003')
  AND m.id IN ('M030', 'M032', 'M032_BTN_CREATE')
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_data_policy (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, combine_mode, status, description, created_by, updated_by
) VALUES
    ('DP_EXPENSE_QUERY_ADMIN_ALL', 'default', 'ROLE', 'R001', 'FORM:EXPENSE', 'QUERY',
     'ALLOW', 'AND', 1, U&'\8D85\7EA7\7BA1\7406\5458\67E5\770B\5168\90E8\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_CREATE_ADMIN', 'default', 'ROLE', 'R001', 'FORM:EXPENSE', 'CREATE',
     'ALLOW', 'AND', 1, U&'\8D85\7EA7\7BA1\7406\5458\53EF\53D1\8D77\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_QUERY_TENANT_ALL', 'default', 'ROLE', 'R002', 'FORM:EXPENSE', 'QUERY',
     'ALLOW', 'AND', 1, U&'\79DF\6237\7BA1\7406\5458\67E5\770B\5168\90E8\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_CREATE_TENANT', 'default', 'ROLE', 'R002', 'FORM:EXPENSE', 'CREATE',
     'ALLOW', 'AND', 1, U&'\79DF\6237\7BA1\7406\5458\53EF\53D1\8D77\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_QUERY_USER_SELF', 'default', 'ROLE', 'R003', 'FORM:EXPENSE', 'QUERY',
     'ALLOW', 'AND', 1, U&'\666E\901A\7528\6237\4EC5\67E5\770B\672C\4EBA\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_CREATE_USER', 'default', 'ROLE', 'R003', 'FORM:EXPENSE', 'CREATE',
     'ALLOW', 'AND', 1, U&'\666E\901A\7528\6237\53EF\53D1\8D77\672C\4EBA\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    resource_code = EXCLUDED.resource_code, action_code = EXCLUDED.action_code,
    effect = EXCLUDED.effect, combine_mode = EXCLUDED.combine_mode,
    status = EXCLUDED.status, description = EXCLUDED.description,
    updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_data_policy_dimension (
    id, policy_id, dimension_code, scope_type, org_unit_ids, sort_order, created_by, updated_by
) VALUES
    ('DPD_EXPENSE_QUERY_ADMIN_ALL', 'DP_EXPENSE_QUERY_ADMIN_ALL', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_CREATE_ADMIN_ALL', 'DP_EXPENSE_CREATE_ADMIN', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_QUERY_TENANT_ALL', 'DP_EXPENSE_QUERY_TENANT_ALL', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_CREATE_TENANT_ALL', 'DP_EXPENSE_CREATE_TENANT', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_QUERY_USER_SELF', 'DP_EXPENSE_QUERY_USER_SELF', 'ADMIN', 'SELF', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_CREATE_USER_SELF', 'DP_EXPENSE_CREATE_USER', 'ADMIN', 'SELF', NULL, 10, 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    policy_id = EXCLUDED.policy_id, dimension_code = EXCLUDED.dimension_code,
    scope_type = EXCLUDED.scope_type, org_unit_ids = EXCLUDED.org_unit_ids,
    sort_order = EXCLUDED.sort_order, updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;
