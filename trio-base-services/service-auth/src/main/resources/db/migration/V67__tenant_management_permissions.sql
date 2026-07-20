-- Register tenant management API permissions and grant safe defaults.

WITH tenant_resources(resource_code, resource_type, display_name) AS (
    VALUES
        ('/api/v1/tenants', 'API', '租户列表与创建'),
        ('/api/v1/tenants/*', 'API', '租户详情、状态与设置')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default',
       resource_code,
       resource_type,
       'service-tenant',
       NULL,
       display_name,
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM tenant_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH tenant_actions(resource_code, action_code, action_category, description) AS (
    VALUES
        ('/api/v1/tenants', 'GET', 'MANAGEMENT', '查询租户列表'),
        ('/api/v1/tenants', 'POST', 'MANAGEMENT', '创建租户'),
        ('/api/v1/tenants/*', 'GET', 'MANAGEMENT', '查看租户详情与设置'),
        ('/api/v1/tenants/*', 'PUT', 'MANAGEMENT', '更新租户资料、状态与设置'),
        ('/api/v1/tenants/*', 'DELETE', 'MANAGEMENT', '删除租户设置')
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
FROM tenant_actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH admin_actions(resource_code, action_code, description) AS (
    VALUES
        ('/api/v1/tenants', 'GET', '查询租户列表'),
        ('/api/v1/tenants', 'POST', '创建租户'),
        ('/api/v1/tenants/*', 'GET', '查看租户详情与设置'),
        ('/api/v1/tenants/*', 'PUT', '更新租户资料、状态与设置'),
        ('/api/v1/tenants/*', 'DELETE', '删除租户设置')
),
tenant_admin_actions(resource_code, action_code, description) AS (
    VALUES
        ('/api/v1/tenants', 'GET', '查询本租户'),
        ('/api/v1/tenants/*', 'GET', '查看本租户详情与设置'),
        ('/api/v1/tenants/*', 'PUT', '更新本租户资料与设置')
),
role_actions AS (
    SELECT role.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role
    JOIN admin_actions action ON role.role_code = 'ADMIN'
    UNION ALL
    SELECT role.id AS role_id,
           action.resource_code,
           action.action_code,
           action.description
    FROM sys_role role
    JOIN tenant_admin_actions action ON role.role_code = 'TENANT_ADMIN'
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
