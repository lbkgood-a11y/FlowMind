-- Semantic page-access resources keep navigation access separate from record reads.
WITH pages(resource_code, display_name) AS (
    VALUES
        ('PAGE_USER_MANAGEMENT', '用户管理页面'),
        ('PAGE_TENANT_MANAGEMENT', '租户管理页面'),
        ('PAGE_ROLE_MANAGEMENT', '角色管理页面'),
        ('PAGE_MENU_MANAGEMENT', '菜单管理页面'),
        ('PAGE_AUDIT_LOG', '操作审计页面'),
        ('PAGE_LOGIN_SESSION', '登录会话页面'),
        ('PAGE_SYSTEM_CONFIG', '系统参数页面')
)
INSERT INTO sys_auth_resource (
    id, tenant_id, resource_code, resource_type, owner_service, display_name,
    lifecycle_status, global_flag, last_synced_at, created_by, updated_by
)
SELECT 'PAR' || upper(substr(md5('default:' || resource_code), 1, 24)),
       'default', resource_code, 'PAGE', 'service-auth', display_name,
       'ACTIVE', 0, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
FROM pages
ON CONFLICT (tenant_id, resource_code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    owner_service = EXCLUDED.owner_service,
    display_name = EXCLUDED.display_name,
    lifecycle_status = 'ACTIVE',
    last_synced_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

WITH pages(resource_code, description) AS (
    VALUES
        ('PAGE_USER_MANAGEMENT', '进入用户管理'),
        ('PAGE_TENANT_MANAGEMENT', '进入租户管理'),
        ('PAGE_ROLE_MANAGEMENT', '进入角色管理'),
        ('PAGE_MENU_MANAGEMENT', '进入菜单管理'),
        ('PAGE_AUDIT_LOG', '进入操作审计'),
        ('PAGE_LOGIN_SESSION', '进入登录会话'),
        ('PAGE_SYSTEM_CONFIG', '进入系统参数')
)
INSERT INTO sys_auth_action (
    id, tenant_id, resource_code, action_code, action_category,
    description, status, created_by, updated_by
)
SELECT 'PAA' || upper(substr(md5('default:' || resource_code || ':ACCESS'), 1, 24)),
       'default', resource_code, 'ACCESS', 'PAGE', description, 1, 'SYSTEM', 'SYSTEM'
FROM pages
ON CONFLICT (tenant_id, resource_code, action_code) DO UPDATE SET
    action_category = EXCLUDED.action_category,
    description = EXCLUDED.description,
    status = 1,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_auth_version
SET version_value = version_value + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE version_key IN ('AUTHORIZATION', 'RESOURCE');
