-- Register permissions for active frontend routes that previously had no authorization code.
-- Function authorization is still stored only in sys_auth_grant.

WITH route_permissions(menu_id, permission_code, display_name) AS (
    VALUES
        ('01HK153X210000000000000021', '/external/vben-document:GET', '文档'),
        ('01HK153X290000000000000029', '/demos/ant-design:GET', 'Ant Design'),
        ('01HK153X220000000000000022', '/external/vben-github:GET', 'Github'),
        ('01HK153X230000000000000023', '/external/vben-antdv-next:GET', 'Ant Design Vue'),
        ('01HK153X240000000000000024', '/external/vben-naive:GET', 'Naive UI'),
        ('01HK153X250000000000000025', '/external/vben-tdesign:GET', 'TDesign'),
        ('01HK153X260000000000000026', '/external/vben-element-plus:GET', 'Element Plus'),
        ('01HK153X270000000000000027', '/vben-admin/about:GET', '关于'),
        ('M002', '/dashboard/analytics:GET', '分析页'),
        ('M003', '/dashboard/workspace:GET', '工作台'),
        ('01HK153X2A000000000000002A', '/profile:GET', '个人中心')
),
parsed_permissions AS (
    SELECT menu_id,
           permission_code,
           split_part(permission_code, ':', 1) AS resource_code,
           split_part(permission_code, ':', 2) AS action_code,
           display_name
    FROM route_permissions
)
UPDATE sys_menu menu
SET permission_code = permissions.permission_code,
    description = COALESCE(NULLIF(menu.description, ''), permissions.display_name),
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP
FROM parsed_permissions permissions
WHERE menu.id = permissions.menu_id
  AND COALESCE(NULLIF(menu.permission_code, ''), '') <> permissions.permission_code;

WITH route_permissions(menu_id, permission_code, display_name) AS (
    VALUES
        ('01HK153X210000000000000021', '/external/vben-document:GET', '文档'),
        ('01HK153X290000000000000029', '/demos/ant-design:GET', 'Ant Design'),
        ('01HK153X220000000000000022', '/external/vben-github:GET', 'Github'),
        ('01HK153X230000000000000023', '/external/vben-antdv-next:GET', 'Ant Design Vue'),
        ('01HK153X240000000000000024', '/external/vben-naive:GET', 'Naive UI'),
        ('01HK153X250000000000000025', '/external/vben-tdesign:GET', 'TDesign'),
        ('01HK153X260000000000000026', '/external/vben-element-plus:GET', 'Element Plus'),
        ('01HK153X270000000000000027', '/vben-admin/about:GET', '关于'),
        ('M002', '/dashboard/analytics:GET', '分析页'),
        ('M003', '/dashboard/workspace:GET', '工作台'),
        ('01HK153X2A000000000000002A', '/profile:GET', '个人中心')
),
parsed_permissions AS (
    SELECT menu_id,
           permission_code,
           split_part(permission_code, ':', 1) AS resource_code,
           split_part(permission_code, ':', 2) AS action_code,
           display_name
    FROM route_permissions
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, business_object_id,
    display_name, lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'AR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default',
       resource_code,
       'FRONTEND_ROUTE',
       'trio-base-frontend',
       menu_id,
       display_name,
       'ACTIVE',
       0,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       'SYSTEM'
FROM parsed_permissions
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    business_object_id = EXCLUDED.business_object_id,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH route_permissions(menu_id, permission_code, display_name) AS (
    VALUES
        ('01HK153X210000000000000021', '/external/vben-document:GET', '文档'),
        ('01HK153X290000000000000029', '/demos/ant-design:GET', 'Ant Design'),
        ('01HK153X220000000000000022', '/external/vben-github:GET', 'Github'),
        ('01HK153X230000000000000023', '/external/vben-antdv-next:GET', 'Ant Design Vue'),
        ('01HK153X240000000000000024', '/external/vben-naive:GET', 'Naive UI'),
        ('01HK153X250000000000000025', '/external/vben-tdesign:GET', 'TDesign'),
        ('01HK153X260000000000000026', '/external/vben-element-plus:GET', 'Element Plus'),
        ('01HK153X270000000000000027', '/vben-admin/about:GET', '关于'),
        ('M002', '/dashboard/analytics:GET', '分析页'),
        ('M003', '/dashboard/workspace:GET', '工作台'),
        ('01HK153X2A000000000000002A', '/profile:GET', '个人中心')
),
parsed_permissions AS (
    SELECT split_part(permission_code, ':', 1) AS resource_code,
           split_part(permission_code, ':', 2) AS action_code,
           display_name
    FROM route_permissions
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'AA' || upper(substr(md5('default:' || resource_code || ':' || action_code), 1, 24)),
       'default',
       resource_code,
       action_code,
       'FRONTEND_ROUTE',
       display_name,
       1,
       'SYSTEM',
       'SYSTEM'
FROM parsed_permissions
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH route_permissions(menu_id, permission_code, display_name) AS (
    VALUES
        ('01HK153X210000000000000021', '/external/vben-document:GET', '文档'),
        ('01HK153X290000000000000029', '/demos/ant-design:GET', 'Ant Design'),
        ('01HK153X220000000000000022', '/external/vben-github:GET', 'Github'),
        ('01HK153X230000000000000023', '/external/vben-antdv-next:GET', 'Ant Design Vue'),
        ('01HK153X240000000000000024', '/external/vben-naive:GET', 'Naive UI'),
        ('01HK153X250000000000000025', '/external/vben-tdesign:GET', 'TDesign'),
        ('01HK153X260000000000000026', '/external/vben-element-plus:GET', 'Element Plus'),
        ('01HK153X270000000000000027', '/vben-admin/about:GET', '关于'),
        ('M002', '/dashboard/analytics:GET', '分析页'),
        ('M003', '/dashboard/workspace:GET', '工作台'),
        ('01HK153X2A000000000000002A', '/profile:GET', '个人中心')
),
parsed_permissions AS (
    SELECT split_part(permission_code, ':', 1) AS resource_code,
           split_part(permission_code, ':', 2) AS action_code,
           display_name
    FROM route_permissions
)
INSERT INTO sys_auth_grant (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, status, description, created_by, updated_by
)
SELECT 'AG' || upper(substr(md5('default:ROLE:' || role.id || ':' || resource_code || ':' || action_code), 1, 24)),
       'default',
       'ROLE',
       role.id,
       resource_code,
       action_code,
       'ALLOW',
       1,
       display_name,
       'SYSTEM',
       'SYSTEM'
FROM sys_role role
CROSS JOIN parsed_permissions
WHERE role.role_code = 'ADMIN'
ON CONFLICT (tenant_id, subject_type, subject_id, resource_code, action_code, effect) DO UPDATE SET
    status = 1,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE', 'GRANT');
