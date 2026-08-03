-- Register the service-org Owner query contract. API access and Owner-semantic
-- authorization remain separate grants; data scope is enforced on ORG_UNIT/QUERY.

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at,
    created_by, created_at, updated_by, updated_at
)
VALUES (
    'AR' || upper(substr(md5('default:ORG_UNIT'), 1, 24)),
    'default', 'ORG_UNIT', 'BUSINESS_OBJECT', 'service-org', 'ORG_UNIT',
    'Organization unit', 'ACTIVE', 0, CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = 'BUSINESS_OBJECT', owner_service = 'service-org',
    business_object_id = 'ORG_UNIT', display_name = 'Organization unit',
    lifecycle_status = 'ACTIVE', last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category, description,
    data_scope_supported, data_scope_enforced, status,
    created_by, created_at, updated_by, updated_at
)
VALUES (
    'AA' || upper(substr(md5('default:ORG_UNIT:QUERY'), 1, 24)),
    'default', 'ORG_UNIT', 'QUERY', 'READ', 'Query organization list, tree, or detail',
    1, 1, 1, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = 'READ', description = 'Query organization list, tree, or detail',
    data_scope_supported = 1, data_scope_enforced = 1, status = 1,
    updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5(grant_row.tenant_id || ':ROLE:' || grant_row.subject_id || ':ORG_UNIT:QUERY'), 1, 24)),
       grant_row.tenant_id, 'ROLE', grant_row.subject_id, 'ORG_UNIT', 'QUERY',
       'ALLOW', 1, 'Owner query grant derived from organization GET access', 'SYSTEM', 'SYSTEM'
FROM sys_auth_grant grant_row
WHERE grant_row.subject_type = 'ROLE'
  AND grant_row.resource_code = '/api/v1/org/units'
  AND grant_row.action_code = 'GET'
  AND grant_row.effect = 'ALLOW'
  AND grant_row.status = 1
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect)
DO UPDATE SET status = 1, updated_by = 'SYSTEM', updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1, updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
