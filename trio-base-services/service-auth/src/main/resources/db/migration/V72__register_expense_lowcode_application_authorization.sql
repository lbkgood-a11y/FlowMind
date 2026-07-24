-- The seeded expense application also bypasses publish-time application resource
-- synchronization. Runtime owner dispatch checks application VIEW before resolving
-- the form action, so register the application contract as well as the V71 form contract.

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, metadata_json, last_synced_at,
    created_by, updated_by
) VALUES (
    'AR' || upper(substr(md5('default:LOWCODE_APP:EXPENSE_REPORT'), 1, 24)),
    'default', 'LOWCODE_APP:EXPENSE_REPORT', 'LOWCODE_APP', 'service-lowcode',
    'LC_APPV_EXPENSE_REPORT_001', U&'\8D39\7528\62A5\9500', 'ACTIVE', 0,
    '{"appKey":"expense_report","version":1,"formKey":"expense"}',
    CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
)
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    metadata_json = EXCLUDED.metadata_json,
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH actions(action_code) AS (
    VALUES ('VIEW'), ('DESIGN'), ('PUBLISH'), ('OFFLINE')
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5('default:LOWCODE_APP:EXPENSE_REPORT:' || action_code), 1, 24)),
       'default', 'LOWCODE_APP:EXPENSE_REPORT', action_code, 'APPLICATION',
       'LOWCODE_APP ' || action_code, 1, 'SYSTEM', 'SYSTEM'
FROM actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE');
