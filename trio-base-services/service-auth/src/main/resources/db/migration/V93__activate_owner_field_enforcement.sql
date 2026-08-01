-- Field governance is usable only after the Owner applies read/write rules at its API boundaries.
-- These resources are covered by typed Owner adapters as of this release.
UPDATE sys_auth_resource
SET owner_service = CASE resource_code
        WHEN 'USER' THEN 'service-auth'
        WHEN 'ORG_UNIT' THEN 'service-org'
        ELSE owner_service
    END,
    read_hide_enforced = 1,
    read_mask_enforced = 1,
    write_deny_enforced = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code IN ('USER', 'ORG_UNIT')
  AND lifecycle_status = 'ACTIVE';

-- Preserve existing low-code/custom-document registrations while correcting known Owner metadata.
UPDATE sys_auth_resource
SET owner_service = 'service-lowcode',
    read_hide_enforced = 1,
    read_mask_enforced = 1,
    write_deny_enforced = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_type = 'LOWCODE_FORM'
  AND lifecycle_status = 'ACTIVE';

UPDATE sys_auth_resource
SET owner_service = 'service-api-runtime',
    read_hide_enforced = 1,
    read_mask_enforced = 1,
    write_deny_enforced = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code = 'CUSTOM_DOC:CONTRACT'
  AND lifecycle_status = 'ACTIVE';

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'FIELD_POLICY');
