CREATE TABLE IF NOT EXISTS sys_role_menu (
    id         VARCHAR(32) PRIMARY KEY,
    role_id    VARCHAR(32) NOT NULL REFERENCES sys_role(id),
    menu_id    VARCHAR(32) NOT NULL REFERENCES sys_menu(id),
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_menu ON sys_role_menu(role_id, menu_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_role ON sys_role_menu(role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu ON sys_role_menu(menu_id);

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(rp.role_id || ':' || m.id), 1, 24)),
       rp.role_id, m.id, 'SYSTEM', 'SYSTEM'
FROM sys_role_permission rp
JOIN sys_menu m ON m.permission_id = rp.permission_id
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || m.id), 1, 24)),
       'R001', m.id, 'SYSTEM', 'SYSTEM'
FROM sys_menu m
ON CONFLICT (role_id, menu_id) DO NOTHING;
