-- V2__add_audit_fields.sql — 组织架构服务添加审计字段

-- ========== sys_org_unit ==========
ALTER TABLE sys_org_unit
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32);

UPDATE sys_org_unit SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- ========== sys_user_org_unit ==========
ALTER TABLE sys_user_org_unit
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE sys_user_org_unit SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;
