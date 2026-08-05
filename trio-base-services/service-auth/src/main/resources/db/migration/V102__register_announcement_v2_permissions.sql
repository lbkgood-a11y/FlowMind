-- Register announcement V2 governance permissions and grant to ADMIN.

WITH announcement_resource(resource_code, resource_type, display_name) AS (
    VALUES ('/api/v2/announcements', 'API', '公告治理工作台 V2')
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
FROM announcement_resource
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH announcement_actions(resource_code, action_code, action_category, description) AS (
    VALUES
        ('/api/v2/announcements', 'GET', 'MANAGEMENT', '查看公告列表'),
        ('/api/v2/announcements', 'POST', 'MANAGEMENT', '创建公告草稿'),
        ('/api/v2/announcements/*/review', 'POST', 'MANAGEMENT', '提交审核/审批/驳回'),
        ('/api/v2/announcements/*/publish', 'POST', 'MANAGEMENT', '定时发布公告'),
        ('/api/v2/announcements/*/emergency-publish', 'POST', 'MANAGEMENT', '紧急发布公告'),
        ('/api/v2/announcements/*/withdraw', 'POST', 'MANAGEMENT', '撤回公告'),
        ('/api/v2/announcements/*/reminders', 'POST', 'MANAGEMENT', '催读提醒')
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
FROM announcement_actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH announcement_actions(resource_code, action_code, description) AS (
    VALUES
        ('/api/v2/announcements', 'GET', '查看公告列表'),
        ('/api/v2/announcements', 'POST', '创建公告草稿'),
        ('/api/v2/announcements/*/review', 'POST', '提交审核/审批/驳回'),
        ('/api/v2/announcements/*/publish', 'POST', '定时发布公告'),
        ('/api/v2/announcements/*/emergency-publish', 'POST', '紧急发布公告'),
        ('/api/v2/announcements/*/withdraw', 'POST', '撤回公告'),
        ('/api/v2/announcements/*/reminders', 'POST', '催读提醒')
),
role_actions AS (
    SELECT role.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role
    JOIN announcement_actions action ON role.role_code = 'ADMIN'
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
