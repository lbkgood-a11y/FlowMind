-- Retire the legacy /api/v1/permissions management surface.
-- sys_permission remains as compatibility metadata for legacy menu.permission_id references;
-- runtime function authorization is managed through sys_auth_resource/action/grant.

UPDATE sys_menu
SET status = 0,
    visible = 0,
    hide_in_menu = 1,
    description = COALESCE(NULLIF(description, ''), 'Retired legacy permission management entry'),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id IN ('M007', '01HK153X3A000000000000003A', '01HK153X3B000000000000003B', '01HK153X3C000000000000003C')
   OR permission_code IN ('/api/v1/permissions:GET', '/api/v1/permissions:POST', '/api/v1/permissions/*:DELETE');

UPDATE sys_auth_action
SET status = 0,
    description = COALESCE(NULLIF(description, ''), 'Retired legacy permission management action'),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'default'
  AND resource_code IN ('/api/v1/permissions', '/api/v1/permissions/*');

UPDATE sys_auth_resource
SET lifecycle_status = 'RETIRED',
    display_name = COALESCE(NULLIF(display_name, ''), 'Retired legacy permission management API'),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'default'
  AND resource_code IN ('/api/v1/permissions', '/api/v1/permissions/*');

UPDATE sys_auth_grant
SET status = 0,
    description = COALESCE(NULLIF(description, ''), 'Retired legacy permission management grant'),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'default'
  AND resource_code IN ('/api/v1/permissions', '/api/v1/permissions/*');

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
