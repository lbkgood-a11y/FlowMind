-- Generic expense-report runtime menu grants. Auth owns only RBAC/menu/data-policy data.

UPDATE sys_menu
SET menu_key = 'LowcodeAppCenter',
    path = '/lowcode/apps',
    component = '/lowcode/runtime/center',
    icon = 'mdi:apps',
    sort_order = 40,
    visible = 1,
    status = 1,
    hide_in_menu = 0,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M035';

UPDATE sys_menu
SET component = '/lowcode/runtime/expense-compat',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M032';

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(r.id || ':' || m.id), 1, 24)),
       r.id, m.id, 'SYSTEM', 'SYSTEM'
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.id IN ('R001', 'R002', 'R003')
  AND m.id IN (
      'M030',
      'M032',
      'M035',
      'M035_BTN_DESCRIPTOR',
      'M035_BTN_LIST',
      'M035_BTN_DETAIL',
      'M035_BTN_ACTION',
      'M035_BTN_RETRY'
  )
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_data_policy (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, combine_mode, status, description, created_by, updated_by
) VALUES
    ('DP_EXPENSE_RUNTIME_QUERY_ADMIN_ALL', 'default', 'ROLE', 'R001', 'FORM:EXPENSE', 'QUERY',
     'ALLOW', 'AND', 1, U&'\8D85\7EA7\7BA1\7406\5458\901A\7528\8FD0\884C\65F6\67E5\770B\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_RUNTIME_CREATE_ADMIN', 'default', 'ROLE', 'R001', 'FORM:EXPENSE', 'CREATE',
     'ALLOW', 'AND', 1, U&'\8D85\7EA7\7BA1\7406\5458\901A\7528\8FD0\884C\65F6\53D1\8D77\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_RUNTIME_QUERY_TENANT_ALL', 'default', 'ROLE', 'R002', 'FORM:EXPENSE', 'QUERY',
     'ALLOW', 'AND', 1, U&'\79DF\6237\7BA1\7406\5458\901A\7528\8FD0\884C\65F6\67E5\770B\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_RUNTIME_CREATE_TENANT', 'default', 'ROLE', 'R002', 'FORM:EXPENSE', 'CREATE',
     'ALLOW', 'AND', 1, U&'\79DF\6237\7BA1\7406\5458\901A\7528\8FD0\884C\65F6\53D1\8D77\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_RUNTIME_QUERY_USER_SELF', 'default', 'ROLE', 'R003', 'FORM:EXPENSE', 'QUERY',
     'ALLOW', 'AND', 1, U&'\666E\901A\7528\6237\901A\7528\8FD0\884C\65F6\4EC5\67E5\770B\672C\4EBA\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM'),
    ('DP_EXPENSE_RUNTIME_CREATE_USER', 'default', 'ROLE', 'R003', 'FORM:EXPENSE', 'CREATE',
     'ALLOW', 'AND', 1, U&'\666E\901A\7528\6237\901A\7528\8FD0\884C\65F6\53D1\8D77\672C\4EBA\8D39\7528\62A5\9500', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    effect = EXCLUDED.effect,
    combine_mode = EXCLUDED.combine_mode,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_data_policy_dimension (
    id, policy_id, dimension_code, scope_type, org_unit_ids, sort_order, created_by, updated_by
) VALUES
    ('DPD_EXPENSE_RUNTIME_QUERY_ADMIN_ALL', 'DP_EXPENSE_RUNTIME_QUERY_ADMIN_ALL', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_RUNTIME_CREATE_ADMIN_ALL', 'DP_EXPENSE_RUNTIME_CREATE_ADMIN', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_RUNTIME_QUERY_TENANT_ALL', 'DP_EXPENSE_RUNTIME_QUERY_TENANT_ALL', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_RUNTIME_CREATE_TENANT_ALL', 'DP_EXPENSE_RUNTIME_CREATE_TENANT', 'ADMIN', 'ALL', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_RUNTIME_QUERY_USER_SELF', 'DP_EXPENSE_RUNTIME_QUERY_USER_SELF', 'ADMIN', 'SELF', NULL, 10, 'SYSTEM', 'SYSTEM'),
    ('DPD_EXPENSE_RUNTIME_CREATE_USER_SELF', 'DP_EXPENSE_RUNTIME_CREATE_USER', 'ADMIN', 'SELF', NULL, 10, 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    policy_id = EXCLUDED.policy_id,
    dimension_code = EXCLUDED.dimension_code,
    scope_type = EXCLUDED.scope_type,
    org_unit_ids = EXCLUDED.org_unit_ids,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
