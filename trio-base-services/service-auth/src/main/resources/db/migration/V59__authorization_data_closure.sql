-- Close RBAC menu permissions, enterprise authorization grants, and data policies.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P_DATASET_LIST', '/api/v1/data/datasets', 'GET', 'List data datasets'),
    ('P_DATASET_CREATE', '/api/v1/data/datasets', 'POST', 'Create data dataset'),
    ('P_DATASET_VIEW', '/api/v1/data/datasets/*', 'GET', 'View data dataset')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_menu
SET permission_id = 'P_DATASET_LIST',
    permission_code = '/api/v1/data/datasets:GET',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M041';

UPDATE sys_menu
SET permission_id = 'P_DATASET_CREATE',
    permission_code = '/api/v1/data/datasets:POST',
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'M041_BTN_CREATE';

DELETE FROM sys_data_policy
WHERE id IN (
    'DP_EXPENSE_QUERY_ADMIN_ALL',
    'DP_EXPENSE_CREATE_ADMIN',
    'DP_EXPENSE_QUERY_TENANT_ALL',
    'DP_EXPENSE_CREATE_TENANT',
    'DP_EXPENSE_QUERY_USER_SELF',
    'DP_EXPENSE_CREATE_USER'
)
AND EXISTS (
    SELECT 1
    FROM sys_data_policy runtime_policy
    WHERE runtime_policy.id IN (
        'DP_EXPENSE_RUNTIME_QUERY_ADMIN_ALL',
        'DP_EXPENSE_RUNTIME_CREATE_ADMIN',
        'DP_EXPENSE_RUNTIME_QUERY_TENANT_ALL',
        'DP_EXPENSE_RUNTIME_CREATE_TENANT',
        'DP_EXPENSE_RUNTIME_QUERY_USER_SELF',
        'DP_EXPENSE_RUNTIME_CREATE_USER'
    )
);

WITH permission_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           p.resource AS resource_code,
           p.action AS action_code,
           COALESCE(p.description, p.resource || ':' || p.action) AS display_name
    FROM sys_permission p
    WHERE p.resource IS NOT NULL AND p.resource <> ''
      AND p.action IS NOT NULL AND p.action <> ''
    UNION
    SELECT DISTINCT
           'default' AS tenant_id,
           split_part(m.permission_code, ':', 1) AS resource_code,
           split_part(m.permission_code, ':', 2) AS action_code,
           COALESCE(m.description, m.menu_name, m.permission_code) AS display_name
    FROM sys_menu m
    WHERE m.permission_code IS NOT NULL
      AND m.permission_code <> ''
      AND position(':' in m.permission_code) > 0
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5(tenant_id || ':' || resource_code), 1, 24)),
       tenant_id,
       resource_code,
       CASE WHEN resource_code LIKE '/api/%' THEN 'API_OPERATION' ELSE 'LEGACY_PERMISSION' END,
       'service-auth',
       max(display_name),
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM permission_codes
WHERE resource_code IS NOT NULL AND resource_code <> ''
GROUP BY tenant_id, resource_code
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH permission_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           p.resource AS resource_code,
           p.action AS action_code,
           COALESCE(p.description, p.resource || ':' || p.action) AS description
    FROM sys_permission p
    WHERE p.resource IS NOT NULL AND p.resource <> ''
      AND p.action IS NOT NULL AND p.action <> ''
    UNION
    SELECT DISTINCT
           'default' AS tenant_id,
           split_part(m.permission_code, ':', 1) AS resource_code,
           split_part(m.permission_code, ':', 2) AS action_code,
           COALESCE(m.description, m.menu_name, m.permission_code) AS description
    FROM sys_menu m
    WHERE m.permission_code IS NOT NULL
      AND m.permission_code <> ''
      AND position(':' in m.permission_code) > 0
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5(tenant_id || ':' || resource_code || ':' || action_code), 1, 24)),
       tenant_id,
       resource_code,
       action_code,
       CASE WHEN resource_code LIKE '/api/%' THEN 'API' ELSE 'BUSINESS' END,
       max(description),
       1,
       'SYSTEM',
       'SYSTEM'
FROM permission_codes
WHERE resource_code IS NOT NULL AND resource_code <> ''
  AND action_code IS NOT NULL AND action_code <> ''
GROUP BY tenant_id, resource_code, action_code
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM sys_auth_grant grant_row
WHERE grant_row.subject_type = 'ROLE'
  AND NOT EXISTS (SELECT 1 FROM sys_role role_row WHERE role_row.id = grant_row.subject_id);

DELETE FROM sys_auth_grant grant_row
WHERE grant_row.subject_type = 'USER'
  AND NOT EXISTS (SELECT 1 FROM sys_user user_row WHERE user_row.id = grant_row.subject_id);

DELETE FROM sys_auth_field_policy policy_row
WHERE policy_row.subject_type = 'ROLE'
  AND NOT EXISTS (SELECT 1 FROM sys_role role_row WHERE role_row.id = policy_row.subject_id);

DELETE FROM sys_auth_field_policy policy_row
WHERE policy_row.subject_type = 'USER'
  AND NOT EXISTS (SELECT 1 FROM sys_user user_row WHERE user_row.id = policy_row.subject_id);

DELETE FROM sys_data_policy policy_row
WHERE policy_row.subject_type = 'ROLE'
  AND NOT EXISTS (SELECT 1 FROM sys_role role_row WHERE role_row.id = policy_row.subject_id);

DELETE FROM sys_data_policy policy_row
WHERE policy_row.subject_type = 'USER'
  AND NOT EXISTS (SELECT 1 FROM sys_user user_row WHERE user_row.id = policy_row.subject_id);

WITH role_menu_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           rm.role_id,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 1) AS resource_code,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 2) AS action_code
    FROM sys_role_menu rm
    JOIN sys_role r ON r.id = rm.role_id
    JOIN sys_menu m ON m.id = rm.menu_id
    LEFT JOIN sys_permission p ON p.id = m.permission_id
    WHERE COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action) IS NOT NULL
      AND position(':' in COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action)) > 0
)
DELETE FROM sys_auth_grant grant_row
WHERE grant_row.tenant_id = 'default'
  AND grant_row.subject_type = 'ROLE'
  AND grant_row.effect = 'ALLOW'
  AND grant_row.description IN (
      'Legacy role-menu permission backfill',
      'Role menu permission sync',
      'Enterprise authorization menu update grant'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM role_menu_codes code_row
      WHERE code_row.tenant_id = grant_row.tenant_id
        AND code_row.role_id = grant_row.subject_id
        AND code_row.resource_code = grant_row.resource_code
        AND code_row.action_code = grant_row.action_code
  );

WITH role_menu_codes AS (
    SELECT DISTINCT
           'default' AS tenant_id,
           rm.role_id,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 1) AS resource_code,
           split_part(COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action), ':', 2) AS action_code
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
       'Role menu permission sync',
       'SYSTEM',
       'SYSTEM'
FROM role_menu_codes
WHERE resource_code IS NOT NULL AND resource_code <> ''
  AND action_code IS NOT NULL AND action_code <> ''
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = CASE
        WHEN sys_auth_grant.description IS NULL
          OR sys_auth_grant.description IN (
              'Legacy role-menu permission backfill',
              'Role menu permission sync',
              'Enterprise authorization menu update grant'
          )
        THEN EXCLUDED.description
        ELSE sys_auth_grant.description
    END,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT', 'DATA_POLICY', 'FIELD_POLICY');
