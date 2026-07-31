-- Field-policy capability metadata is fail-closed: legacy and undeclared
-- resources do not claim runtime enforcement support.
ALTER TABLE sys_auth_resource
    ADD COLUMN IF NOT EXISTS read_hide_enforced SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS read_mask_enforced SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS write_deny_enforced SMALLINT NOT NULL DEFAULT 0;
