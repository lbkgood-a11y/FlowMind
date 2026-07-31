ALTER TABLE sys_menu
    ADD COLUMN IF NOT EXISTS page_code VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_sys_menu_page_code
    ON sys_menu (page_code);

UPDATE sys_menu
SET page_code = CASE menu_key
    WHEN 'users' THEN 'SYSTEM.USER'
    WHEN 'tenant' THEN 'SYSTEM.TENANT'
    WHEN 'roles' THEN 'SYSTEM.ROLE'
    WHEN 'menus' THEN 'SYSTEM.MENU'
    WHEN 'SystemAuditLog' THEN 'SYSTEM.AUDIT'
    WHEN 'SystemSession' THEN 'SYSTEM.SESSION'
    WHEN 'SystemConfig' THEN 'SYSTEM.CONFIG'
END,
updated_by = 'SYSTEM',
updated_at = CURRENT_TIMESTAMP
WHERE page_code IS NULL
  AND menu_key IN ('users', 'tenant', 'roles', 'menus', 'SystemAuditLog', 'SystemSession', 'SystemConfig');
