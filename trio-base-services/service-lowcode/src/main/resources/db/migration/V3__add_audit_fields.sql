-- V3__add_audit_fields.sql — 低代码服务添加审计字段

-- ========== lc_form_definition ==========
-- created_by 已存在，只需添加 updated_by
ALTER TABLE lc_form_definition
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

UPDATE lc_form_definition SET updated_by = COALESCE(created_by, 'SYSTEM') WHERE updated_by IS NULL;

-- ========== lc_form_field_definition ==========
ALTER TABLE lc_form_field_definition
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE lc_form_field_definition SET created_by = 'SYSTEM', updated_by = 'SYSTEM' WHERE created_by IS NULL;

-- ========== lc_form_instance ==========
ALTER TABLE lc_form_instance
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE lc_form_instance SET created_by = COALESCE(submitted_by, 'SYSTEM'), updated_by = COALESCE(submitted_by, 'SYSTEM') WHERE created_by IS NULL;
