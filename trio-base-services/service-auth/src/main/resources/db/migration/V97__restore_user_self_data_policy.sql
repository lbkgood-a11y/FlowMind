-- Restore the default USER role policy that may have been removed by a page-capability release.
-- Page capability releases and independently managed data policies have separate lifecycles.

INSERT INTO sys_data_policy (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, combine_mode, status, description, created_by, updated_by
)
SELECT 'DP_USER_QUERY_USER_SELF', 'default', 'ROLE', 'R003', 'USER', 'QUERY',
       'ALLOW', 'AND', 1, 'Default USER role can only query its own user record',
       'SYSTEM', 'SYSTEM'
WHERE EXISTS (
    SELECT 1 FROM sys_role WHERE id = 'R003'
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
SELECT 'DPD_USER_QUERY_USER_SELF_ADMIN', 'DP_USER_QUERY_USER_SELF',
       'ADMIN', 'SELF', NULL, 10, 'SYSTEM', 'SYSTEM'
WHERE EXISTS (
    SELECT 1 FROM sys_data_policy WHERE id = 'DP_USER_QUERY_USER_SELF'
)
ON CONFLICT (id) DO UPDATE SET
    policy_id = EXCLUDED.policy_id,
    dimension_code = EXCLUDED.dimension_code,
    scope_type = EXCLUDED.scope_type,
    org_unit_ids = EXCLUDED.org_unit_ids,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'DATA_POLICY');
