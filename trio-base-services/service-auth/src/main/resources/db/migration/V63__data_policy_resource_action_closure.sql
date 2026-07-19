-- Register active data-policy business resources/actions in the unified authorization catalog.
-- Data policies are not grants, but they still must reference active resource/action metadata.

WITH active_data_policy_resources AS (
    SELECT tenant_id,
           resource_code
    FROM sys_data_policy
    WHERE COALESCE(status, 1) = 1
      AND resource_code IS NOT NULL
      AND resource_code <> ''
      AND action_code IS NOT NULL
      AND action_code <> ''
    GROUP BY tenant_id, resource_code
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5(tenant_id || ':' || resource_code), 1, 24)),
       tenant_id,
       resource_code,
       CASE
           WHEN resource_code LIKE 'FORM:%' THEN 'LOWCODE_FORM'
           WHEN resource_code IN ('USER', 'ORG_UNIT') THEN 'BUSINESS_OBJECT'
           ELSE 'DATA_SCOPE_RESOURCE'
       END,
       CASE
           WHEN resource_code LIKE 'FORM:%' THEN 'service-lowcode'
           ELSE 'service-auth'
       END,
       CASE
           WHEN resource_code LIKE 'FORM:%' THEN split_part(resource_code, ':', 2)
           ELSE resource_code
       END,
       CASE
           WHEN resource_code = 'USER' THEN 'User data scope'
           WHEN resource_code = 'ORG_UNIT' THEN 'Organization data scope'
           WHEN resource_code LIKE 'FORM:%' THEN split_part(resource_code, ':', 2) || ' form data scope'
           ELSE resource_code || ' data scope'
       END,
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM active_data_policy_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH active_data_policy_actions AS (
    SELECT tenant_id,
           resource_code,
           action_code,
           max(COALESCE(NULLIF(description, ''), resource_code || ':' || action_code)) AS description
    FROM sys_data_policy
    WHERE COALESCE(status, 1) = 1
      AND resource_code IS NOT NULL
      AND resource_code <> ''
      AND action_code IS NOT NULL
      AND action_code <> ''
    GROUP BY tenant_id, resource_code, action_code
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5(tenant_id || ':' || resource_code || ':' || action_code), 1, 24)),
       tenant_id,
       resource_code,
       action_code,
       'DATA_POLICY',
       description,
       1,
       'SYSTEM',
       'SYSTEM'
FROM active_data_policy_actions
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
