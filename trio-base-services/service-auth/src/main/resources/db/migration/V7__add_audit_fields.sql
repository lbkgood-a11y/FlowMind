-- V7__add_audit_fields.sql — 为所有表添加审计字段（created_by, updated_by, updated_at）

-- ========== sys_user ==========
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32);

UPDATE sys_user SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- ========== sys_role ==========
ALTER TABLE sys_role
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE sys_role SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- ========== sys_menu ==========
ALTER TABLE sys_menu
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32);

UPDATE sys_menu SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- ========== sys_permission ==========
ALTER TABLE sys_permission
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE sys_permission SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- ========== sys_role_permission (关联表：新增 id 主键) ==========
ALTER TABLE sys_role_permission
    ADD COLUMN IF NOT EXISTS id         VARCHAR(32),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE sys_role_permission
SET id = '01' || upper(substr(md5(role_id || ':' || permission_id), 1, 24))
WHERE id IS NULL;
UPDATE sys_role_permission SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

ALTER TABLE sys_role_permission ALTER COLUMN id SET NOT NULL;

-- 必须先删除旧复合主键，再添加新主键
ALTER TABLE sys_role_permission DROP CONSTRAINT IF EXISTS sys_role_permission_pkey;
ALTER TABLE sys_role_permission ADD CONSTRAINT pk_sys_role_permission PRIMARY KEY (id);
ALTER TABLE sys_role_permission DROP CONSTRAINT IF EXISTS uk_sys_role_permission;
ALTER TABLE sys_role_permission ADD CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id);

-- ========== sys_user_role (关联表：新增 id 主键) ==========
ALTER TABLE sys_user_role
    ADD COLUMN IF NOT EXISTS id         VARCHAR(32),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE sys_user_role
SET id = '01' || upper(substr(md5(user_id || ':' || role_id), 1, 24))
WHERE id IS NULL;
UPDATE sys_user_role SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

ALTER TABLE sys_user_role ALTER COLUMN id SET NOT NULL;

ALTER TABLE sys_user_role DROP CONSTRAINT IF EXISTS sys_user_role_pkey;
ALTER TABLE sys_user_role ADD CONSTRAINT pk_sys_user_role PRIMARY KEY (id);
ALTER TABLE sys_user_role DROP CONSTRAINT IF EXISTS uk_sys_user_role;
ALTER TABLE sys_user_role ADD CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id);
