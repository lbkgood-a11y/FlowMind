-- Local acceptance users for the published expense-report workflow.

INSERT INTO sys_user(
    id, username, password, email, phone, status, created_by, updated_by
) VALUES
    ('U004', 'dept_head',
     '$2a$10$sSzlRIHLEd3J4Vutq/w4XemoxUwwgq0aA.qzsNaSp.YPkzLRmtjCy',
     'dept-head@triobase.local', '13800000004', 1, 'SYSTEM', 'SYSTEM'),
    ('U005', 'finance_approver',
     '$2a$10$sSzlRIHLEd3J4Vutq/w4XemoxUwwgq0aA.qzsNaSp.YPkzLRmtjCy',
     'finance@triobase.local', '13800000005', 1, 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO UPDATE SET
    username = EXCLUDED.username,
    password = EXCLUDED.password,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    status = EXCLUDED.status,
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_user_role(id, user_id, role_id, created_by, updated_by)
SELECT '01' || upper(substr(md5(u.id || ':' || r.id), 1, 24)),
       u.id,
       r.id,
       'SYSTEM',
       'SYSTEM'
FROM sys_user u
JOIN sys_role r
  ON (u.id = 'U004' AND r.role_code = 'DEPT_HEAD')
  OR (u.id = 'U005' AND r.role_code = 'FINANCE')
ON CONFLICT (user_id, role_id) DO UPDATE SET
    updated_by = 'SYSTEM',
    updated_at = CURRENT_TIMESTAMP;
