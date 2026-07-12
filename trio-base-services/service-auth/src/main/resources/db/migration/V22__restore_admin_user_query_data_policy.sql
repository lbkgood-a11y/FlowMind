-- Restore the baseline data policy that lets the ADMIN role query all users.
-- Some development databases may have kept only the USER self-scope policy after
-- manual edits or old deduplication runs.

INSERT INTO sys_data_policy (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, combine_mode, status, description, created_by, updated_by
)
SELECT
    'DP_USER_QUERY_ADMIN_ALL',
    'default',
    'ROLE',
    'R001',
    'USER',
    'QUERY',
    'ALLOW',
    'AND',
    1,
    '超级管理员可查看全部用户数据',
    'SYSTEM',
    'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_data_policy policy
    JOIN sys_data_policy_dimension dimension ON dimension.policy_id = policy.id
    WHERE policy.tenant_id = 'default'
      AND policy.subject_type = 'ROLE'
      AND policy.subject_id = 'R001'
      AND policy.resource_code = 'USER'
      AND policy.action_code = 'QUERY'
      AND policy.effect = 'ALLOW'
      AND policy.combine_mode = 'AND'
      AND policy.status = 1
      AND dimension.dimension_code = 'ADMIN'
      AND dimension.scope_type = 'ALL'
      AND COALESCE(dimension.org_unit_ids, '') = ''
)
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    subject_type = EXCLUDED.subject_type,
    subject_id = EXCLUDED.subject_id,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    effect = EXCLUDED.effect,
    combine_mode = EXCLUDED.combine_mode,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_data_policy_dimension (
    id, policy_id, dimension_code, scope_type, org_unit_ids, sort_order,
    created_by, updated_by
)
SELECT
    'DPD_USER_QUERY_ADMIN_ALL_ADMIN',
    'DP_USER_QUERY_ADMIN_ALL',
    'ADMIN',
    'ALL',
    NULL,
    10,
    'SYSTEM',
    'SYSTEM'
WHERE EXISTS (
    SELECT 1 FROM sys_data_policy WHERE id = 'DP_USER_QUERY_ADMIN_ALL'
)
ON CONFLICT (id) DO UPDATE SET
    policy_id = EXCLUDED.policy_id,
    dimension_code = EXCLUDED.dimension_code,
    scope_type = EXCLUDED.scope_type,
    org_unit_ids = EXCLUDED.org_unit_ids,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
