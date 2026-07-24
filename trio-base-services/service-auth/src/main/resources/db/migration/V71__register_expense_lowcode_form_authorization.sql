-- The expense runtime application is seeded directly by service-lowcode migrations,
-- so it does not pass through the publish-time authorization synchronizer. Register
-- its form contract in the default-tenant authorization catalog for fail-closed
-- Global Action validation.

INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, metadata_json, last_synced_at,
    created_by, updated_by
) VALUES (
    'AR' || upper(substr(md5('default:LOWCODE_FORM:EXPENSE'), 1, 24)),
    'default', 'LOWCODE_FORM:EXPENSE', 'LOWCODE_FORM', 'service-lowcode',
    'LC_FORM_EXPENSE_001', U&'\8D39\7528\62A5\9500', 'ACTIVE', 0,
    '{"formKey":"expense","version":1}', CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
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
    VALUES ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'), ('SUBMIT'),
           ('APPROVE'), ('REJECT'), ('EXPORT'), ('DESIGN'), ('PUBLISH'),
           ('OFFLINE'), ('FIELD_READ'), ('FIELD_WRITE')
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5('default:LOWCODE_FORM:EXPENSE:' || action_code), 1, 24)),
       'default', 'LOWCODE_FORM:EXPENSE', action_code,
       CASE WHEN action_code LIKE 'FIELD_%' THEN 'FIELD' ELSE 'DOCUMENT' END,
       'LOWCODE_FORM ' || action_code, 1, 'SYSTEM', 'SYSTEM'
FROM actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH fields(field_key, field_label, field_type) AS (
    VALUES ('amount', U&'\91D1\989D', 'number'),
           ('reason', U&'\4E8B\7531', 'string'),
           ('dept', U&'\6240\5C5E\90E8\95E8', 'string'),
           ('remark', U&'\5907\6CE8', 'string')
)
INSERT INTO sys_auth_field (
    id, tenant_id, resource_code, field_key, field_label, field_type,
    status, created_by, updated_by
)
SELECT 'AF' || upper(substr(md5('default:LOWCODE_FORM:EXPENSE:' || field_key), 1, 24)),
       'default', 'LOWCODE_FORM:EXPENSE', field_key, field_label, field_type,
       1, 'SYSTEM', 'SYSTEM'
FROM fields
ON CONFLICT (tenant_id, resource_code, field_key) DO UPDATE SET
    field_label = EXCLUDED.field_label,
    field_type = EXCLUDED.field_type,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE');
