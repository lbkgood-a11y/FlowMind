-- Retire sys_menu.permission_id from runtime metadata.
-- Existing values are first folded into permission_code, then sys_role_menu is
-- rebuilt as a projection from sys_auth_grant + sys_menu.permission_code only.

UPDATE sys_menu menu
SET permission_code = permission.resource || ':' || permission.action,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
FROM sys_permission permission
WHERE menu.permission_id = permission.id
  AND (menu.permission_code IS NULL OR menu.permission_code = '')
  AND permission.resource IS NOT NULL
  AND permission.resource <> ''
  AND permission.action IS NOT NULL
  AND permission.action <> '';

DROP VIEW IF EXISTS sys_role_menu;

ALTER TABLE sys_menu
    DROP COLUMN IF EXISTS permission_id;

CREATE OR REPLACE VIEW sys_role_menu AS
WITH menu_permissions AS (
    SELECT DISTINCT
           menu.id AS menu_id,
           substring(menu.permission_code from '^(.*):[^:]+$') AS resource_code,
           substring(menu.permission_code from '[^:]+$') AS action_code
    FROM sys_menu menu
    WHERE menu.permission_code IS NOT NULL
      AND menu.permission_code <> ''
      AND position(':' in menu.permission_code) > 0
)
SELECT 'RV' || upper(substr(md5(grant_row.tenant_id || ':' || grant_row.subject_id || ':' || menu_permissions.menu_id), 1, 24)) AS id,
       grant_row.subject_id AS role_id,
       menu_permissions.menu_id,
       grant_row.created_by,
       grant_row.created_at,
       grant_row.updated_by,
       grant_row.updated_at
FROM sys_auth_grant grant_row
JOIN menu_permissions
  ON menu_permissions.resource_code = grant_row.resource_code
 AND menu_permissions.action_code = grant_row.action_code
WHERE grant_row.tenant_id = 'default'
  AND grant_row.subject_type = 'ROLE'
  AND grant_row.effect = 'ALLOW'
  AND grant_row.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM sys_auth_grant denied
      WHERE denied.tenant_id = grant_row.tenant_id
        AND denied.subject_type = grant_row.subject_type
        AND denied.subject_id = grant_row.subject_id
        AND denied.resource_code = grant_row.resource_code
        AND denied.action_code = grant_row.action_code
        AND denied.effect = 'DENY'
        AND denied.status = 1
  );
