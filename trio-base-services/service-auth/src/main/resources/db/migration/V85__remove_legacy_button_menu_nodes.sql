-- Page Capability Catalog and sys_auth_grant are the only authorization model.
-- Button menu rows are retired presentation metadata and must not survive as a
-- parallel permission tree.

UPDATE sys_menu child
SET parent_id = NULL,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
FROM sys_menu parent
WHERE child.parent_id = parent.id
  AND parent.menu_type = 'button'
  AND child.menu_type <> 'button';

DELETE FROM sys_menu
WHERE menu_type = 'button';

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
