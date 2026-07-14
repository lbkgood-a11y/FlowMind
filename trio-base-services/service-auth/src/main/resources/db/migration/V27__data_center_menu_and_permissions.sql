-- Data center permissions and menu fixtures for the hybrid query MVP.

INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P085', '/api/v1/data/datasets', 'GET', 'List data datasets'),
    ('P086', '/api/v1/data/datasets', 'POST', 'Create data dataset'),
    ('P087', '/api/v1/data/datasets/*', 'GET', 'View data dataset'),
    ('P088', '/api/v1/data/documents', 'POST', 'Ingest data document'),
    ('P089', '/api/v1/data/query/structured', 'POST', 'Run structured data query'),
    ('P090', '/api/v1/data/query/semantic', 'POST', 'Run semantic data query'),
    ('P091', '/api/v1/data/query/hybrid', 'POST', 'Run hybrid data query')
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
    ('M040', NULL, 'DataCenter', U&'\6570\636E\4E2D\5FC3', '/data', NULL,
     'mdi:database-search-outline', NULL, NULL,
     'catalog', 'data', 60, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, NULL, NULL, 'Data catalog and query center'),

    ('M041', 'M040', 'DataCatalog', U&'\6570\636E\76EE\5F55', '/data/catalog', '/data/hybrid-query/index',
     'mdi:table-cog', NULL, NULL,
     'menu', 'data', 10, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P085', '/api/v1/data/datasets:GET', 'Dataset catalog validation page'),

    ('M042', 'M040', 'HybridQuery', U&'\6DF7\5408\67E5\8BE2', '/data/hybrid-query', '/data/hybrid-query/index',
     'mdi:text-search', NULL, NULL,
     'menu', 'data', 20, 1, 1, 0, 0, 0, 0, 0, 0,
     NULL, NULL, NULL, 'P091', '/api/v1/data/query/hybrid:POST', 'Hybrid query validation page'),

    ('M041_BTN_CREATE', 'M041', 'DataCatalogCreate', U&'\65B0\589E', NULL, NULL, NULL, NULL, NULL,
     'button', 'data', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P086', '/api/v1/data/datasets:POST', 'Create dataset'),

    ('M042_BTN_INGEST', 'M042', 'DataDocumentIngest', U&'\5165\5E93', NULL, NULL, NULL, NULL, NULL,
     'button', 'data', 10, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P088', '/api/v1/data/documents:POST', 'Ingest document'),

    ('M042_BTN_QUERY', 'M042', 'HybridQueryRun', U&'\67E5\8BE2', NULL, NULL, NULL, NULL, NULL,
     'button', 'data', 20, 1, 1, 0, 0, 1, 0, 0, 0,
     NULL, NULL, NULL, 'P091', '/api/v1/data/query/hybrid:POST', 'Run hybrid query')
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_key = EXCLUDED.menu_key,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    menu_group = EXCLUDED.menu_group,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    hide_in_menu = EXCLUDED.hide_in_menu,
    permission_id = EXCLUDED.permission_id,
    permission_code = EXCLUDED.permission_code,
    description = EXCLUDED.description,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001', m.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu m
WHERE m.id IN (
    'M040', 'M041', 'M042',
    'M041_BTN_CREATE', 'M042_BTN_INGEST', 'M042_BTN_QUERY'
)
ON CONFLICT (role_id, menu_id) DO NOTHING;
