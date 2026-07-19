-- Keep sys_auth_grant as the only stored role/user function authorization source.
-- sys_menu remains navigation metadata; sys_role_menu becomes a read-only projection.

WITH role_menu_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           rm.role_id,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 1) AS resource_code,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 2) AS action_code,
           COALESCE(m.description, m.menu_name, COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action)) AS description
    FROM sys_role_menu rm
    JOIN sys_role r ON r.id = rm.role_id
    JOIN sys_menu m ON m.id = rm.menu_id
    LEFT JOIN sys_permission p ON p.id = m.permission_id
    WHERE COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action) IS NOT NULL
      AND position(':' in COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action)) > 0
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5(tenant_id || ':ROLE:' || role_id || ':' || resource_code || ':' || action_code), 1, 24)),
       tenant_id,
       'ROLE',
       role_id,
       resource_code,
       action_code,
       'ALLOW',
       1,
       max(description),
       'SYSTEM',
       'SYSTEM'
FROM role_menu_codes
WHERE resource_code IS NOT NULL AND resource_code <> ''
  AND action_code IS NOT NULL AND action_code <> ''
GROUP BY tenant_id, role_id, resource_code, action_code
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = CASE
        WHEN sys_auth_grant.description IS NULL OR sys_auth_grant.description = ''
        THEN EXCLUDED.description
        ELSE sys_auth_grant.description
    END,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'GRANT');

DROP TABLE IF EXISTS sys_role_menu;

CREATE OR REPLACE VIEW sys_role_menu AS
WITH menu_permissions AS (
    SELECT DISTINCT
           m.id AS menu_id,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 1) AS resource_code,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 2) AS action_code
    FROM sys_menu m
    LEFT JOIN sys_permission p ON p.id = m.permission_id
    WHERE COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action) IS NOT NULL
      AND position(':' in COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action)) > 0
)
SELECT 'RV' || upper(substr(md5(g.tenant_id || ':' || g.subject_id || ':' || mp.menu_id), 1, 24)) AS id,
       g.subject_id AS role_id,
       mp.menu_id,
       g.created_by,
       g.created_at,
       g.updated_by,
       g.updated_at
FROM sys_auth_grant g
JOIN menu_permissions mp
  ON mp.resource_code = g.resource_code
 AND mp.action_code = g.action_code
WHERE g.tenant_id = 'default'
  AND g.subject_type = 'ROLE'
  AND g.effect = 'ALLOW'
  AND g.status = 1;
