-- Built-in Owner resources must exist independently of whether a data policy already references them.
WITH builtin_resources(resource_code, display_name, owner_service) AS (
    VALUES
        ('USER', '用户', 'service-auth'),
        ('ORG_UNIT', '组织单元', 'service-org')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag,
    read_hide_enforced, read_mask_enforced, write_deny_enforced,
    last_synced_at, created_by, created_at, updated_by, updated_at
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default', resource_code, 'BUSINESS_OBJECT', owner_service, resource_code,
       display_name, 'ACTIVE', 0,
       1, 1, 1,
       CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM builtin_resources
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    read_hide_enforced = 1,
    read_mask_enforced = 1,
    write_deny_enforced = 1,
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH builtin_fields(resource_code, field_key, field_label, field_type, sensitivity, mask_strategy) AS (
    VALUES
        ('USER', 'username', '用户名', 'string', 'INTERNAL', NULL),
        ('USER', 'email', '邮箱', 'string', 'PERSONAL', 'EMAIL'),
        ('USER', 'phone', '手机号', 'string', 'PERSONAL', 'PHONE'),
        ('USER', 'status', '用户状态', 'number', 'INTERNAL', NULL),
        ('ORG_UNIT', 'unitCode', '组织编码', 'string', 'INTERNAL', NULL),
        ('ORG_UNIT', 'unitName', '组织名称', 'string', 'INTERNAL', NULL),
        ('ORG_UNIT', 'unitType', '组织类型', 'string', 'INTERNAL', NULL),
        ('ORG_UNIT', 'status', '组织状态', 'number', 'INTERNAL', NULL)
)
INSERT INTO sys_auth_field (
    id, tenant_id, resource_code, field_key, field_label, field_type,
    sensitivity_classification, default_mask_strategy, status,
    created_by, created_at, updated_by, updated_at
)
SELECT 'AF' || upper(substr(md5('default:' || resource_code || ':' || field_key), 1, 24)),
       'default', resource_code, field_key, field_label, field_type,
       sensitivity, mask_strategy, 1,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM builtin_fields
ON CONFLICT (tenant_id, resource_code, field_key) DO UPDATE SET
    field_label = EXCLUDED.field_label,
    field_type = EXCLUDED.field_type,
    sensitivity_classification = EXCLUDED.sensitivity_classification,
    default_mask_strategy = EXCLUDED.default_mask_strategy,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'FIELD_POLICY');
