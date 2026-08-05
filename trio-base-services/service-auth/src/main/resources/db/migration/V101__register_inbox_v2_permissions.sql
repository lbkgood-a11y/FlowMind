-- Register personal inbox V2 permissions and grant to authenticated users.

WITH inbox_resources(resource_code, resource_type, display_name) AS (
    VALUES ('/api/v2/inbox', 'API', '个人消息中心 V2')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default',
       resource_code,
       resource_type,
       'service-ops',
       NULL,
       display_name,
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM inbox_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH inbox_actions(resource_code, action_code, action_category, description) AS (
    VALUES ('/api/v2/inbox', 'GET', 'MANAGEMENT', '查看个人消息与通知')
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5('default:' || resource_code || ':' || action_code), 1, 24)),
       'default',
       resource_code,
       action_code,
       action_category,
       description,
       1,
       'SYSTEM',
       'SYSTEM'
FROM inbox_actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH inbox_actions(resource_code, action_code, description) AS (
    VALUES ('/api/v2/inbox', 'GET', '查看个人消息与通知')
),
role_actions AS (
    SELECT role.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role
    JOIN inbox_actions action ON role.role_code = 'ADMIN'
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:' || role_id || ':' || resource_code || ':' || action_code), 1, 24)),
       'default',
       'ROLE',
       role_id,
       resource_code,
       action_code,
       'ALLOW',
       1,
       description,
       'SYSTEM',
       'SYSTEM'
FROM role_actions
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
