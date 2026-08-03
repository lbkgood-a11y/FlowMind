-- Data-scope contracts are Owner declarations, not projections derived from existing policies.
-- A policy can only be configured after both capability flags are true.
ALTER TABLE sys_auth_action
    ADD COLUMN IF NOT EXISTS data_scope_supported SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS data_scope_enforced SMALLINT NOT NULL DEFAULT 0;

WITH owner_resources(resource_code, resource_type, owner_service, business_object_id, display_name) AS (
    VALUES
        ('USER', 'BUSINESS_OBJECT', 'service-auth', 'USER', '用户'),
        ('FORM_INSTANCE', 'DOCUMENT', 'service-lowcode', 'FORM_INSTANCE', '表单实例'),
        ('BUSINESS_TIMELINE', 'PROJECTION', 'service-business-catalog', 'BUSINESS_TIMELINE', '业务时间线')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at,
    created_by, created_at, updated_by, updated_at
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default', resource_code, resource_type, owner_service, business_object_id,
       display_name, 'ACTIVE', 0, CURRENT_TIMESTAMP,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM owner_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH owner_actions(resource_code, action_code, action_category, description) AS (
    VALUES
        ('USER', 'QUERY', 'READ', '查询用户列表或详情'),
        ('FORM_INSTANCE', 'QUERY', 'READ', '查询表单实例列表或详情'),
        ('FORM_INSTANCE', 'EXPORT', 'EXPORT', '导出表单实例'),
        ('BUSINESS_TIMELINE', 'QUERY', 'READ', '查询业务时间线')
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category, description,
    data_scope_supported, data_scope_enforced, status,
    created_by, created_at, updated_by, updated_at
)
SELECT 'AA' || upper(substr(md5('default:' || resource_code || ':' || action_code), 1, 24)),
       'default', resource_code, action_code, action_category, description,
       1, 1, 1, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM owner_actions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    data_scope_supported = 1,
    data_scope_enforced = 1,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE');
