-- Register field metadata for built-in, non-lowcode business objects.
-- Field policies remain enforced by each Owner service; this table is the
-- authorization registry projection used by the administration workbench.

UPDATE sys_auth_resource
SET display_name = CASE resource_code
        WHEN 'USER' THEN '用户'
        WHEN 'ORG_UNIT' THEN '组织单元'
        ELSE display_name
    END,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code IN ('USER', 'ORG_UNIT')
  AND lifecycle_status = 'ACTIVE';

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
SELECT 'AF' || upper(substr(md5(resource.tenant_id || ':' || fields.resource_code || ':' || fields.field_key), 1, 24)),
       resource.tenant_id,
       fields.resource_code,
       fields.field_key,
       fields.field_label,
       fields.field_type,
       fields.sensitivity,
       fields.mask_strategy,
       1,
       'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM builtin_fields fields
JOIN sys_auth_resource resource
  ON resource.resource_code = fields.resource_code
 AND resource.lifecycle_status = 'ACTIVE'
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
