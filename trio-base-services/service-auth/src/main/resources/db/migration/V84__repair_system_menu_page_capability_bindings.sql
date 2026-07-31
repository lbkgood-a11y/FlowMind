-- V82 used historical aliases for three menus whose persisted stable keys were renamed.
-- Repair already-upgraded databases without modifying the applied migration history.

UPDATE sys_menu
SET page_code = CASE menu_key
    WHEN 'SystemUser' THEN 'SYSTEM.USER'
    WHEN 'SystemRole' THEN 'SYSTEM.ROLE'
    WHEN 'SystemMenu' THEN 'SYSTEM.MENU'
    ELSE page_code
END,
updated_by = 'SYSTEM',
updated_at = CURRENT_TIMESTAMP
WHERE menu_key IN ('SystemUser', 'SystemRole', 'SystemMenu')
  AND page_code IS DISTINCT FROM CASE menu_key
      WHEN 'SystemUser' THEN 'SYSTEM.USER'
      WHEN 'SystemRole' THEN 'SYSTEM.ROLE'
      WHEN 'SystemMenu' THEN 'SYSTEM.MENU'
  END;
