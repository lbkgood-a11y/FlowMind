-- The API grant controls endpoint access, while this Owner-semantic grant controls
-- USER field enforcement. Both are required for a readable USER query result.

INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:R003:USER:QUERY'), 1, 24)),
       'default', 'ROLE', 'R003', 'USER', 'QUERY',
       'ALLOW', 1, 'Default USER role Owner query contract', 'SYSTEM', 'SYSTEM'
WHERE EXISTS (
    SELECT 1 FROM sys_role WHERE id = 'R003'
)
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect)
DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'GRANT', 'FIELD_POLICY');
