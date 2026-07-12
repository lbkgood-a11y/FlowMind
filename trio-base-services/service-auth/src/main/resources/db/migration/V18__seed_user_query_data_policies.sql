-- Seed baseline data scopes for the first protected business query.
-- Admin keeps all user-management data, ordinary users see only themselves.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P027', '/api/v1/data-policies/effective', 'GET', '解析当前用户数据权限范围')
ON CONFLICT (id) DO UPDATE SET
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_menu (
    id, parent_id, menu_key, menu_name, path, component, icon, active_icon, active_path,
    menu_type, menu_group, sort_order, visible, status, keep_alive, affix_tab,
    hide_in_menu, hide_children_in_menu, hide_in_breadcrumb, hide_in_tab,
    badge, badge_type, badge_variant, permission_id, permission_code, description
) VALUES
    ('M010_API_EFFECTIVE', 'M008', 'SystemDataPolicyEffectiveApi', 'resolveDataPolicy(API)',
     NULL, NULL, NULL, NULL, NULL,
     'button', 'system', 90, 0, 1, 0, 0, 1, 0, 1, 1,
     NULL, NULL, NULL, 'P027', '/api/v1/data-policies/effective:GET',
     '数据权限运行时解析接口')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    active_icon = EXCLUDED.active_icon,
    active_path = EXCLUDED.active_path,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    keep_alive = EXCLUDED.keep_alive,
    affix_tab = EXCLUDED.affix_tab,
    hide_in_menu = EXCLUDED.hide_in_menu,
    hide_children_in_menu = EXCLUDED.hide_children_in_menu,
    hide_in_breadcrumb = EXCLUDED.hide_in_breadcrumb,
    hide_in_tab = EXCLUDED.hide_in_tab,
    badge = EXCLUDED.badge,
    badge_type = EXCLUDED.badge_type,
    badge_variant = EXCLUDED.badge_variant,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(r.id || ':M010_API_EFFECTIVE'), 1, 24)),
       r.id,
       'M010_API_EFFECTIVE',
       'SYSTEM',
       'SYSTEM'
FROM sys_role r
WHERE r.id IN ('R001', 'R002', 'R003')
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_data_policy (
    id, tenant_id, subject_type, subject_id, resource_code, action_code,
    effect, combine_mode, status, description, created_by, updated_by
) VALUES
    ('DP_USER_QUERY_ADMIN_ALL', 'default', 'ROLE', 'R001', 'USER', 'QUERY',
     'ALLOW', 'AND', 1, '超级管理员可查看全部用户数据', 'SYSTEM', 'SYSTEM'),
    ('DP_USER_QUERY_USER_SELF', 'default', 'ROLE', 'R003', 'USER', 'QUERY',
     'ALLOW', 'AND', 1, '普通用户仅可查看自己的用户数据', 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    subject_type = EXCLUDED.subject_type,
    subject_id = EXCLUDED.subject_id,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    effect = EXCLUDED.effect,
    combine_mode = EXCLUDED.combine_mode,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_data_policy_dimension (
    id, policy_id, dimension_code, scope_type, org_unit_ids, sort_order,
    created_by, updated_by
) VALUES
    ('DPD_USER_QUERY_ADMIN_ALL_ADMIN', 'DP_USER_QUERY_ADMIN_ALL', 'ADMIN', 'ALL', NULL, 10,
     'SYSTEM', 'SYSTEM'),
    ('DPD_USER_QUERY_USER_SELF_ADMIN', 'DP_USER_QUERY_USER_SELF', 'ADMIN', 'SELF', NULL, 10,
     'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    policy_id = EXCLUDED.policy_id,
    dimension_code = EXCLUDED.dimension_code,
    scope_type = EXCLUDED.scope_type,
    org_unit_ids = EXCLUDED.org_unit_ids,
    sort_order = EXCLUDED.sort_order,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
